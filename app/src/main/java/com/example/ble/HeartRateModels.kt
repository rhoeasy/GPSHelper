package com.example.ble

enum class BleConnectionStatus {
    IDLE,
    SCANNING,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    ERROR
}

data class BleHrDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val advertisesHeartRate: Boolean
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
