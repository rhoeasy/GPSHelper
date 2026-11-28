package com.example.ai

import androidx.annotation.MainThread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal sealed interface GatewayTaskKey {
    data class Coach(val sessionId: Long) : GatewayTaskKey
    data object Coordinates : GatewayTaskKey
    data object Models : GatewayTaskKey
    data object PageAssistant : GatewayTaskKey
}

enum class GatewayStage { QUEUED, CONNECTING, WAITING_FOR_OUTPUT, RECEIVING, SAVING }

internal sealed interface GatewayTaskStatus {
    data class Active(val stage: GatewayStage, val partialText: String = "", val modelId: String? = null) : GatewayTaskStatus
    data class Failed(val message: String) : GatewayTaskStatus
}

/** A single FIFO worker. The owner and its callers use the main dispatcher. */
internal class AiGatewayQueue(private val scope: CoroutineScope) {
    private class Request(
        val key: GatewayTaskKey,
        val modelId: String?,
        val run: suspend ((GatewayStage, String) -> Unit) -> Unit
    )

    private val pending = ArrayDeque<Request>()
    private var active: Request? = null
    private var activeJob: Job? = null
    private val _status = MutableStateFlow<Map<GatewayTaskKey, GatewayTaskStatus>>(emptyMap())
    val status = _status.asStateFlow()

    @MainThread
    fun submit(key: GatewayTaskKey, modelId: String? = null, run: suspend ((GatewayStage, String) -> Unit) -> Unit) {
        if (_status.value[key] is GatewayTaskStatus.Active) return
        pending.addLast(Request(key, modelId, run))
        setStatus(key, GatewayTaskStatus.Active(GatewayStage.QUEUED, modelId = modelId))
        startNext()
    }

    @MainThread
    fun cancel(key: GatewayTaskKey) {
        pending.removeAll { it.key == key }
        if (active?.key == key) {
            // The HTTP coroutine closes its call before the next request can start.
            activeJob?.cancel()
        } else {
            setStatus(key, null)
        }
    }

    @MainThread
    fun cancelAll() {
        pending.clear()
        _status.value = emptyMap()
        activeJob?.cancel()
    }

    private fun startNext() {
        if (active != null) return
        val request = pending.removeFirstOrNull() ?: return
        active = request
        var failure: GatewayTaskStatus.Failed? = null
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                setStatus(request.key, GatewayTaskStatus.Active(GatewayStage.CONNECTING, modelId = request.modelId))
                request.run { stage, partial -> setStatus(request.key, GatewayTaskStatus.Active(stage, partial, request.modelId)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = GatewayTaskStatus.Failed(e.message?.takeIf { it.isNotBlank() } ?: "网关任务失败，请重试")
            }
        }
        activeJob = job
        job.invokeOnCompletion {
            scope.launch {
                if (_status.value[request.key] is GatewayTaskStatus.Active && pending.none { it.key == request.key }) {
                    setStatus(request.key, failure)
                }
                active = null
                activeJob = null
                startNext()
            }
        }
        job.start()
    }

    private fun setStatus(key: GatewayTaskKey, value: GatewayTaskStatus?) {
        _status.value = if (value == null) _status.value - key else _status.value + (key to value)
    }
}
