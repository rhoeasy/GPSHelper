package com.example.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * Native Android BLE Heart Rate Monitor hub supporting Xiaomi Mi Band (with Heart Rate Broadcast
 * enabled) and any standard Bluetooth SIG Heart Rate Service (0x180D / 0x2A37) sensor.
 */
object HeartRateRepository {
    private const val TAG = "HeartRateRepository"
    private const val PREFS_NAME = "ble_hr_prefs"
    private const val KEY_LAST_ADDR = "last_hr_address"
    private const val KEY_LAST_NAME = "last_hr_name"
    private const val SCAN_TIMEOUT_MS = 12_000L

    val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")
    val HEART_RATE_MEASUREMENT_UUID: UUID = UUID.fromString("00002A37-0000-1000-8000-00805F9B34FB")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    private val WEARABLE_KEYWORDS = listOf(
        "xiaomi", "mi band", "smart band", "redmi", "amazfit",
        "band", "watch", "hrm", "heart", "polar", "garmin", "coros", "wahoo", "magene", "coospo"
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val discoveredMap = LinkedHashMap<String, BleHrDevice>()

    private var appContext: Context? = null
    private var scanner: BluetoothLeScanner? = null
    private var activeGatt: BluetoothGatt? = null
    private var isScanningInternal = false

    private val _heartRateState = MutableStateFlow(HeartRateState())
    val heartRateState: StateFlow<HeartRateState> = _heartRateState.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<BleHrDevice>>(emptyList())
    val scannedDevices: StateFlow<List<BleHrDevice>> = _scannedDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _lastSavedDevice = MutableStateFlow<Pair<String, String>?>(null)
    val lastSavedDevice: StateFlow<Pair<String, String>?> = _lastSavedDevice.asStateFlow()

    private val stopScanRunnable = Runnable { stopScan() }

    fun loadLastDevice(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val addr = prefs.getString(KEY_LAST_ADDR, null)
        val name = prefs.getString(KEY_LAST_NAME, null)
        _lastSavedDevice.value = if (!addr.isNullOrBlank() && !name.isNullOrBlank()) {
            name to addr
        } else {
            null
        }
    }

    fun hasBlePermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter?.isEnabled == true
    }

    @SuppressLint("MissingPermission")
    fun startScan(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        loadLastDevice(appCtx)

        if (!hasBlePermissions(appCtx)) {
            _heartRateState.update { it.copy(errorMessage = "缺少蓝牙权限") }
            return
        }

        val adapter = getBluetoothAdapter(appCtx)
        if (adapter == null || !adapter.isEnabled) {
            _heartRateState.update { it.copy(errorMessage = "请先开启手机蓝牙") }
            return
        }

        val leScanner = adapter.bluetoothLeScanner
        if (leScanner == null) {
            _heartRateState.update { it.copy(errorMessage = "当前设备不支持低功耗蓝牙 (BLE)") }
            return
        }

        stopScan()
        discoveredMap.clear()
        _scannedDevices.value = emptyList()
        _heartRateState.update { it.copy(errorMessage = null) }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner = leScanner
            isScanningInternal = true
            _isScanning.value = true
            leScanner.startScan(null, settings, scanCallback)
            mainHandler.removeCallbacks(stopScanRunnable)
            mainHandler.postDelayed(stopScanRunnable, SCAN_TIMEOUT_MS)
        } catch (se: SecurityException) {
            Log.w(TAG, "Missing permission for BLE scan", se)
            isScanningInternal = false
            _isScanning.value = false
            _heartRateState.update { it.copy(errorMessage = "蓝牙扫描权限被拒绝") }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BLE scan", e)
            isScanningInternal = false
            _isScanning.value = false
            _heartRateState.update { it.copy(errorMessage = "启动蓝牙扫描失败") }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        mainHandler.removeCallbacks(stopScanRunnable)
        if (isScanningInternal) {
            runCatching { scanner?.stopScan(scanCallback) }
        }
        isScanningInternal = false
        _isScanning.value = false
    }

    @SuppressLint("MissingPermission")
    fun connect(context: Context, address: String, deviceName: String) {
        val appCtx = context.applicationContext
        appContext = appCtx
        stopScan()

        if (!hasBlePermissions(appCtx)) {
            _heartRateState.update {
                it.copy(status = BleConnectionStatus.ERROR, errorMessage = "请先授予蓝牙连接权限")
            }
            return
        }

        val adapter = getBluetoothAdapter(appCtx)
        if (adapter == null || !adapter.isEnabled) {
            _heartRateState.update {
                it.copy(status = BleConnectionStatus.ERROR, errorMessage = "请先开启手机蓝牙")
            }
            return
        }

        closeActiveGatt()

        val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull()
        if (device == null) {
            _heartRateState.update {
                it.copy(status = BleConnectionStatus.ERROR, errorMessage = "无效的蓝牙设备地址")
            }
            return
        }

        _heartRateState.value = HeartRateState(
            status = BleConnectionStatus.CONNECTING,
            deviceName = deviceName,
            deviceAddress = address,
            bpm = null,
            errorMessage = null
        )

        try {
            activeGatt = device.connectGatt(
                appCtx,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )
        } catch (se: SecurityException) {
            Log.w(TAG, "Missing BLUETOOTH_CONNECT permission", se)
            _heartRateState.update {
                it.copy(status = BleConnectionStatus.ERROR, errorMessage = "蓝牙连接权限不足")
            }
        } catch (e: Exception) {
            Log.e(TAG, "BLE connectGatt failed", e)
            _heartRateState.update {
                it.copy(status = BleConnectionStatus.ERROR, errorMessage = "连接设备失败，请重试")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        stopScan()
        closeActiveGatt()
        _heartRateState.value = HeartRateState(status = BleConnectionStatus.DISCONNECTED)
    }

    @SuppressLint("MissingPermission")
    private fun closeActiveGatt() {
        val gatt = activeGatt
        activeGatt = null
        if (gatt != null) {
            runCatching { gatt.disconnect() }
            runCatching { gatt.close() }
        }
    }

    private fun getBluetoothAdapter(context: Context): BluetoothAdapter? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter
    }

    private fun saveLastDevice(name: String, address: String) {
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_NAME, name)
            .putString(KEY_LAST_ADDR, address)
            .apply()
        _lastSavedDevice.value = name to address
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val address = device.address ?: return
            val advUuids = result.scanRecord?.serviceUuids.orEmpty()
            val advertisesHr = advUuids.any { it == ParcelUuid(HEART_RATE_SERVICE_UUID) }

            val rawName = result.scanRecord?.deviceName ?: runCatching { device.name }.getOrNull()
            // Skip unnamed devices unless they explicitly advertise the Heart Rate Service
            if (rawName.isNullOrBlank() && !advertisesHr) return
            val displayName = rawName?.trim().takeUnless { it.isNullOrBlank() } ?: "BLE 心率设备"

            discoveredMap[address] = BleHrDevice(
                name = displayName,
                address = address,
                rssi = result.rssi,
                advertisesHeartRate = advertisesHr
            )

            _scannedDevices.value = discoveredMap.values.sortedWith(
                compareByDescending<BleHrDevice> { it.advertisesHeartRate }
                    .thenByDescending { deviceItem ->
                        val lower = deviceItem.name.lowercase()
                        WEARABLE_KEYWORDS.any { lower.contains(it) }
                    }
                    .thenByDescending { it.rssi }
            )
        }

        override fun onScanFailed(errorCode: Int) {
            isScanningInternal = false
            _isScanning.value = false
            _heartRateState.update {
                it.copy(errorMessage = "蓝牙扫描失败 (错误码 $errorCode)")
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS && newState != BluetoothProfile.STATE_CONNECTED) {
                Log.w(TAG, "GATT connection error: status=$status newState=$newState")
                if (activeGatt == gatt) {
                    closeActiveGatt()
                    _heartRateState.update {
                        it.copy(
                            status = BleConnectionStatus.DISCONNECTED,
                            bpm = null,
                            errorMessage = "蓝牙连接已断开，请确认手环在附近并开启心率广播"
                        )
                    }
                }
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    val discovered = runCatching { gatt.discoverServices() }.getOrDefault(false)
                    if (!discovered) {
                        closeActiveGatt()
                        _heartRateState.update {
                            it.copy(
                                status = BleConnectionStatus.ERROR,
                                errorMessage = "无法读取设备蓝牙服务"
                            )
                        }
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (activeGatt == gatt) {
                        closeActiveGatt()
                        _heartRateState.update {
                            it.copy(
                                status = BleConnectionStatus.DISCONNECTED,
                                bpm = null
                            )
                        }
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                closeActiveGatt()
                _heartRateState.update {
                    it.copy(
                        status = BleConnectionStatus.ERROR,
                        errorMessage = "蓝牙服务发现失败 ($status)"
                    )
                }
                return
            }

            val hrService = gatt.getService(HEART_RATE_SERVICE_UUID)
            val hrChar = hrService?.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)
            if (hrService == null || hrChar == null) {
                closeActiveGatt()
                _heartRateState.update {
                    it.copy(
                        status = BleConnectionStatus.ERROR,
                        bpm = null,
                        errorMessage = "未发现标准心率服务，请在「小米运动健康」中开启手环「心率广播」后重试"
                    )
                }
                return
            }

            val notifyEnabled = runCatching {
                gatt.setCharacteristicNotification(hrChar, true)
            }.getOrDefault(false)

            val cccd = hrChar.getDescriptor(CCCD_UUID)
            if (!notifyEnabled || cccd == null) {
                closeActiveGatt()
                _heartRateState.update {
                    it.copy(
                        status = BleConnectionStatus.ERROR,
                        errorMessage = "无法订阅心率通知特征"
                    )
                }
                return
            }

            val writeStarted = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                        BluetoothGatt.GATT_SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(cccd)
                }
            }.getOrDefault(false)

            if (!writeStarted) {
                closeActiveGatt()
                _heartRateState.update {
                    it.copy(
                        status = BleConnectionStatus.ERROR,
                        errorMessage = "启用心率广播通知失败"
                    )
                }
                return
            }

            val currentName = _heartRateState.value.deviceName ?: gatt.device.address
            val currentAddr = gatt.device.address
            saveLastDevice(currentName, currentAddr)

            _heartRateState.update {
                it.copy(
                    status = BleConnectionStatus.CONNECTED,
                    deviceName = currentName,
                    deviceAddress = currentAddr,
                    errorMessage = null
                )
            }
        }

        // Android 13+ (API 33+) callback
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == HEART_RATE_MEASUREMENT_UUID) {
                handleHeartRatePacket(value)
            }
        }

        // Legacy API < 33 callback
        @Deprecated("Deprecated in Android API 33")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
                characteristic.uuid == HEART_RATE_MEASUREMENT_UUID
            ) {
                characteristic.value?.let { handleHeartRatePacket(it) }
            }
        }
    }

    private fun handleHeartRatePacket(bytes: ByteArray) {
        val bpm = parseHeartRateBpm(bytes) ?: return
        _heartRateState.update { current ->
            current.copy(
                status = BleConnectionStatus.CONNECTED,
                bpm = bpm,
                lastUpdatedMs = SystemClock.elapsedRealtime(),
                errorMessage = null
            )
        }
    }
}
