package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.ai.AssistantPage
import com.example.ble.HeartRateState
import com.example.gps.AccuracyTelemetry
import com.example.gps.AltitudeTelemetry
import com.example.gps.DashboardSpeedStyle
import com.example.gps.GpsData
import com.example.gps.OverlayDisplayConfig
import com.example.gps.SafetyAlertConfig
import com.example.gps.SpeedUnit
import com.example.sensor.EnvironmentTelemetry
import com.example.sensor.GForceData
import com.example.track.RecordingSnapshot
import com.example.track.TrackFormatters
import com.example.track.TrackSessionDetail
import com.example.track.TrackSessionEntity
import com.example.ui.theme.ThemeMode
import java.util.Locale

internal enum class AssistantPageKind(val title: String) {
    DASHBOARD("仪表盘"), TRACKS("轨迹"), TRACK_DETAIL("记录详情"),
    RECORDER("轨迹录制"), ALTITUDE("海拔"), ACCURACY("定位精度"),
    COMPASS("指南针"), SATELLITES("卫星数"), COORDINATES("经纬度"),
    SETTINGS("设置"), APPEARANCE("外观"), OVERLAY("悬浮窗设置"),
    SAFETY("安全预警"), AI_GATEWAY("AI 网关"), ABOUT("关于"),
    AI_HISTORY("AI 教练历史"), AI_REPORT("AI 教练报告"), SOCIAL_POSTER("制作社交图"),
    BLUETOOTH("蓝牙心率设备"), FOCUS("专注模式"), TIMING_LINE("卡丁车计时线"), MERGE("合并轨迹");

    fun snapshot(vararg content: String): AssistantPage =
        AssistantPage(name, title, content.filter { it.isNotBlank() }.joinToString("\n"))
}

internal class AssistantPageRegistry {
    private val pages = LinkedHashMap<Any, () -> AssistantPage>()
    fun attach(token: Any, current: () -> AssistantPage) { pages[token] = current }
    fun detach(token: Any) { pages.remove(token) }
    fun active(): AssistantPage? = pages.values.lastOrNull()?.invoke()
}

internal val LocalAssistantPages = staticCompositionLocalOf<AssistantPageRegistry?> { null }

@Composable
internal fun ProvideAssistantSubpage(snapshot: () -> AssistantPage) {
    val registry = LocalAssistantPages.current ?: return
    val current = rememberUpdatedState(snapshot)
    DisposableEffect(registry) {
        val token = Any()
        registry.attach(token) { current.value() }
        onDispose { registry.detach(token) }
    }
}

internal fun gpsAssistantPage(
    tab: MainTab,
    telemetry: TelemetryDetailPage?,
    settings: SettingsSubPage?,
    detail: TrackSessionDetail?,
    aiHistoryCount: Int?,
    recorderOpen: Boolean,
    gps: GpsData,
    altitude: AltitudeTelemetry,
    accuracy: AccuracyTelemetry,
    environment: EnvironmentTelemetry,
    gForce: GForceData,
    heartRate: HeartRateState,
    recording: RecordingSnapshot,
    sessions: List<TrackSessionEntity>,
    unit: SpeedUnit,
    speedStyle: DashboardSpeedStyle,
    theme: ThemeMode,
    overlay: OverlayDisplayConfig,
    overlayRunning: Boolean,
    safety: SafetyAlertConfig,
    gatewayConfigured: Boolean
): AssistantPage {
    if (detail != null) return AssistantPageKind.TRACK_DETAIL.snapshot(trackAssistantSummary(detail.session, unit))
    if (aiHistoryCount != null) return AssistantPageKind.AI_HISTORY.snapshot("报告数：$aiHistoryCount")
    when (telemetry) {
        TelemetryDetailPage.ALTITUDE -> return AssistantPageKind.ALTITUDE.snapshot(
            "当前海拔：${gps.formattedAltitude()}",
            "最低：${meters(altitude.minAltitudeM)}；最高：${meters(altitude.maxAltitudeM)}",
            "累计上升：${meters(altitude.elevationGainM)}；累计下降：${meters(altitude.elevationLossM)}",
            "垂直精度：${meters(altitude.verticalAccuracyM)}"
        )
        TelemetryDetailPage.ACCURACY -> return AssistantPageKind.ACCURACY.snapshot(
            "当前水平精度：${gps.formattedAccuracy()}；样本数：${accuracy.sampleCount}",
            "最佳：${meters(accuracy.bestAccuracyM)}；平均：${meters(accuracy.avgAccuracyM)}",
            "速度精度：${number(accuracy.speedAccuracyMps)} m/s"
        )
        TelemetryDetailPage.COMPASS -> return AssistantPageKind.COMPASS.snapshot(
            if (environment.hasCompassReading) "磁方位角：${number(environment.magneticAzimuthDeg)}°"
            else "当前没有指南针读数",
            "GPS 行进方向：${gps.formattedBearing()}"
        )
        TelemetryDetailPage.SATELLITES -> return AssistantPageKind.SATELLITES.snapshot(
            "参与定位 / 可见：${gps.formattedSatellites()}；定位状态：${gps.fixStatus.name}"
        )
        TelemetryDetailPage.COORDINATES -> return AssistantPageKind.COORDINATES.snapshot(
            if (gps.hasFix) "当前有定位；未附上精确经纬度" else "当前没有定位"
        )
        null -> Unit
    }
    if (recorderOpen && tab == MainTab.TRACKS) return AssistantPageKind.RECORDER.snapshot(
        "模式：${recording.mode.labelZh}；状态：${recording.status.name}",
        "用时：${TrackFormatters.formatDuration(recording.elapsedMs)}；距离：${TrackFormatters.formatDistance(recording.distanceMeters)}",
        recording.stepCount?.let { "步数：$it" }.orEmpty()
    )
    return when (tab) {
        MainTab.DASHBOARD -> AssistantPageKind.DASHBOARD.snapshot(
            "速度：${gps.formattedSpeedCompact(unit)}；海拔：${gps.formattedAltitude()}",
            "定位状态：${gps.fixStatus.name}；精度：${gps.formattedAccuracy()}；卫星：${gps.formattedSatellites()}",
            if (gForce.isAvailable) "合成 G：${number(gForce.totalG)}；横向 G：${number(gForce.lateralG)}；纵向 G：${number(gForce.longitudinalG)}" else "G 力传感器不可用",
            heartRate.bpm?.takeIf { heartRate.isConnected }?.let { "当前心率：$it bpm" }.orEmpty()
        )
        MainTab.TRACKS -> AssistantPageKind.TRACKS.snapshot(
            "已保存记录：${sessions.size} 条",
            sessions.take(3).joinToString("\n") { trackAssistantSummary(it, unit, compact = true) }
        )
        MainTab.SETTINGS -> when (settings) {
            null -> AssistantPageKind.SETTINGS.snapshot("外观、悬浮窗、安全预警、AI 网关、关于")
            SettingsSubPage.APPEARANCE -> AssistantPageKind.APPEARANCE.snapshot(
                "主题模式：${theme.name}；速度单位：${unit.label}；速度表样式：${speedStyle.name}"
            )
            SettingsSubPage.OVERLAY -> AssistantPageKind.OVERLAY.snapshot(
                "悬浮窗运行中：${yesNo(overlayRunning)}；启用组件：${overlay.enabledCount}",
                "背景不透明度：${(overlay.backgroundAlpha * 100).toInt()}%；字号倍率：${overlay.fontScale}",
                "打点：${yesNo(overlay.showRideTimer)}；间隔：${overlay.rideMarkerIntervalMinutes} 分钟；平时显示用时：${yesNo(overlay.showRideElapsedTime)}"
            )
            SettingsSubPage.SAFETY -> AssistantPageKind.SAFETY.snapshot(
                "速度预警：${yesNo(safety.speedAlertEnabled)}，阈值 ${safety.speedLimitKmh} km/h",
                "心率预警：${yesNo(safety.hrAlertEnabled)}，阈值 ${safety.hrLimitBpm} bpm；震动：${yesNo(safety.vibrateOnAlert)}"
            )
            SettingsSubPage.AI_GATEWAY -> AssistantPageKind.AI_GATEWAY.snapshot(
                "网关配置完成：${yesNo(gatewayConfigured)}；未附上网关地址或密钥"
            )
            SettingsSubPage.ABOUT -> AssistantPageKind.ABOUT.snapshot("GPSHelper · 版本 ${com.example.BuildConfig.VERSION_NAME}")
        }
    }
}

private fun yesNo(value: Boolean) = if (value) "是" else "否"
private fun number(value: Number?): String = value?.let { String.format(Locale.US, "%.2f", it.toDouble()) } ?: "--"
private fun meters(value: Number?) = value?.let { "${number(it)} m" } ?: "--"

internal fun trackAssistantSummary(session: TrackSessionEntity, unit: SpeedUnit, compact: Boolean = false): String {
    val base = "${session.name.take(100)} · ${session.activityMode.labelZh} · " +
        "${TrackFormatters.formatDistance(session.distanceMeters)} · ${TrackFormatters.formatDuration(session.durationMs)}"
    if (compact) return base
    return buildList {
        add(base)
        add("最高速度：${TrackFormatters.formatSpeed(session.maxSpeedMps, unit)}；轨迹点数：${session.pointCount}")
        if (session.activityMode.isElevationTrail) add("累计爬升：${meters(session.elevationGainMeters)}")
        else add("完成圈数：${session.lapCount}；最佳圈速：${TrackFormatters.formatLapTime(session.bestLapMs)}")
        session.stepCount?.let { add("步数：$it") }
        session.avgHeartRate?.let { add("平均心率：$it bpm") }
    }.joinToString("\n")
}
