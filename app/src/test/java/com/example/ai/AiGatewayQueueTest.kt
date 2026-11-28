package com.example.ai

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiGatewayQueueTest {
    @Test
    fun runsInOrderAndCancelsBothQueuedAndRunningRequests() = runTest {
        val queue = AiGatewayQueue(backgroundScope)
        val first = GatewayTaskKey.Coach(10L)
        val started = mutableListOf<String>()
        queue.submit(first) {
            started.add("coach")
            awaitCancellation()
        }
        queue.submit(first) { started.add("duplicate") }
        queue.submit(GatewayTaskKey.Coordinates) { started.add("coordinate") }
        queue.submit(GatewayTaskKey.Models) { started.add("models") }
        runCurrent()

        assertEquals(listOf("coach"), started)
        assertEquals(GatewayStage.QUEUED, (queue.status.value[GatewayTaskKey.Models] as GatewayTaskStatus.Active).stage)
        queue.cancel(GatewayTaskKey.Coordinates)
        queue.cancel(first)
        runCurrent()

        assertEquals(listOf("coach", "models"), started)
        assertEquals(emptyMap<GatewayTaskKey, GatewayTaskStatus>(), queue.status.value)
    }

    @Test
    fun reportsFailureWithoutPreventingTheNextRequest() = runTest {
        val queue = AiGatewayQueue(backgroundScope)
        val completed = CompletableDeferred<Unit>()
        queue.submit(GatewayTaskKey.Models) { throw IOException("Network unavailable") }
        queue.submit(GatewayTaskKey.Coordinates) { completed.complete(Unit) }
        runCurrent()

        completed.await()
        assertEquals(GatewayTaskStatus.Failed("Network unavailable"), queue.status.value[GatewayTaskKey.Models])
        assertNull(queue.status.value[GatewayTaskKey.Coordinates])
    }
}
