package com.example.ble

enum class BleConnectionStatus {
    IDLE,
    SCANNING,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    ERROR
}

enum class BleDeviceSource {
    CONNECTED_LOCAL,
    PAIRED_LOCAL,
    NEARBY_SCAN
}

enum class BleDeviceCategory {
    HEART_RATE_SENSOR,
    WEARABLE,
    OTHER
}

data class SavedBleDevice(
    val name: String,
    val address: String,
    val lastConnectedAtMs: Long = 0L
)

data class BleHrDevice(
    val name: String,
    val address: String,
    val rssi: Int? = null,
    val advertisesHeartRate: Boolean = false,
    val source: BleDeviceSource = BleDeviceSource.NEARBY_SCAN,
    val category: BleDeviceCategory = BleDeviceCategory.OTHER,
    val isLastConnected: Boolean = false
)

data class HeartRateState(
    val status: BleConnectionStatus = BleConnectionStatus.IDLE,
    val deviceName: String? = null,
    val deviceAddress: String? = null,
    val bpm: Int? = null,
    val lastUpdatedMs: Long = 0L,
    val errorMessage: String? = null
) {
    val isConnected: Boolean
        get() = status == BleConnectionStatus.CONNECTED
}

/**
 * Pure deterministic helpers for human-readable BLE device classification, sorting,
 * proximity descriptions, and short address suffixes.
 */
object BleDeviceClassifier {
    private val HR_SENSOR_KEYWORDS = listOf(
        "hrm", "heart", "hr-", "polar", "wahoo", "tickr",
        "magene", "coospo", "igpsport", "bryton", "kalenji",
        "decathlon", "cycled", "心率"
    )

    private val WEARABLE_KEYWORDS = listOf(
        "xiaomi", "mi band", "mi smart band", "smart band", "redmi",
        "amazfit", "zepp", "huawei", "honor", "band", "watch",
        "garmin", "coros", "suunto", "fitbit", "galaxy fit",
        "oppo", "vivo", "oneplus", "ticwatch", "手环", "手表"
    )

    fun classifyCategory(name: String, advertisesHeartRate: Boolean): BleDeviceCategory {
        val lower = name.lowercase()
        if (advertisesHeartRate || HR_SENSOR_KEYWORDS.any { lower.contains(it) }) {
            return BleDeviceCategory.HEART_RATE_SENSOR
        }
        if (WEARABLE_KEYWORDS.any { lower.contains(it) }) {
            return BleDeviceCategory.WEARABLE
        }
        return BleDeviceCategory.OTHER
    }

    fun isRecommendedWearableOrHr(device: BleHrDevice): Boolean {
        return device.advertisesHeartRate ||
            device.isLastConnected ||
            device.source == BleDeviceSource.CONNECTED_LOCAL ||
            device.category != BleDeviceCategory.OTHER
    }

    fun categoryLabelZh(category: BleDeviceCategory): String = when (category) {
        BleDeviceCategory.HEART_RATE_SENSOR -> "心率设备"
        BleDeviceCategory.WEARABLE -> "手环 / 手表"
        BleDeviceCategory.OTHER -> "蓝牙设备"
    }

    /**
     * Converts raw RSSI dBm into plain-language proximity & signal quality.
     */
    fun signalDescriptionZh(rssi: Int?, source: BleDeviceSource): String {
        if (rssi != null) {
            return when {
                rssi >= -55 -> "就在手边 · 信号极强"
                rssi >= -70 -> "很近 · 信号强"
                rssi >= -82 -> "较近 · 信号中等"
                else -> "较远 · 信号弱"
            }
        }
        return when (source) {
            BleDeviceSource.CONNECTED_LOCAL -> "系统已直连 · 就在身边"
            BleDeviceSource.PAIRED_LOCAL -> "已在手机蓝牙配对"
            BleDeviceSource.NEARBY_SCAN -> "附近设备"
        }
    }

    /**
     * Signal strength bars (1..4) for visual indicator; null when RSSI is unavailable.
     */
    fun signalBars(rssi: Int?): Int? {
        if (rssi == null) return null
        return when {
            rssi >= -55 -> 4
            rssi >= -70 -> 3
            rssi >= -82 -> 2
            else -> 1
        }
    }

    /**
     * Short human-friendly address tail (e.g. "尾号 2B:0C") to distinguish identical models
     * without cluttering the UI with full 17-character MAC strings.
     */
    fun shortAddressTail(address: String): String {
        val cleaned = address.trim().uppercase()
        return if (cleaned.length >= 5) {
            "尾号 ${cleaned.takeLast(5)}"
        } else {
            cleaned
        }
    }

    fun sortDevices(devices: Collection<BleHrDevice>): List<BleHrDevice> {
        return devices.sortedWith(
            compareByDescending<BleHrDevice> { it.isLastConnected }
                .thenByDescending { it.source == BleDeviceSource.CONNECTED_LOCAL }
                .thenByDescending { it.advertisesHeartRate }
                .thenByDescending { it.category == BleDeviceCategory.HEART_RATE_SENSOR }
                .thenByDescending { it.category == BleDeviceCategory.WEARABLE }
                .thenByDescending { it.source == BleDeviceSource.PAIRED_LOCAL }
                .thenByDescending { it.rssi ?: Int.MIN_VALUE }
                .thenBy { it.name.lowercase() }
        )
    }
}

/**
 * Parses standard Bluetooth SIG Heart Rate Measurement characteristic (UUID 0x2A37).
 */
fun parseHeartRateBpm(data: ByteArray): Int? {
    if (data.size < 2) return null
    val flags = data[0].toInt() and 0xFF
    val is16Bit = (flags and 0x01) != 0
    val bpm = if (is16Bit) {
        if (data.size < 3) return null
        (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
    } else {
        data[1].toInt() and 0xFF
    }
    return bpm.takeIf { it in 25..240 }
}
