package com.example.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
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
 *
 * Also queries locally connected GATT devices and system-paired BLE wearables so bands already
 * connected to Mi Fitness / Zepp Life appear immediately at the top of the picker.
 */
object HeartRateRepository {
    private const val TAG = "HeartRateRepository"
    private const val PREFS_NAME = "ble_hr_prefs"
    private const val KEY_LAST_ADDR = "last_hr_address"
    private const val KEY_LAST_NAME = "last_hr_name"
    private const val KEY_LAST_TIME_MS = "last_hr_connected_ms"
    private const val KEY_AUTO_RECONNECT = "auto_reconnect_hr"
    private const val SCAN_TIMEOUT_MS = 12_000L
    private const val CONNECT_TIMEOUT_MS = 20_000L

    val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")
    val HEART_RATE_MEASUREMENT_UUID: UUID = UUID.fromString("00002A37-0000-1000-8000-00805F9B34FB")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    private val mainHandler = Handler(Looper.getMainLooper())
    private val localDeviceMap = LinkedHashMap<String, BleHrDevice>()
    private val discoveredMap = LinkedHashMap<String, BleHrDevice>()

    private var appContext: Context? = null
    private var scanner: BluetoothLeScanner? = null
    private var activeGatt: BluetoothGatt? = null
    private var isScanningInternal = false
    private var manualDisconnectThisSession = false

    private val _heartRateState = MutableStateFlow(HeartRateState())
    val heartRateState: StateFlow<HeartRateState> = _heartRateState.asStateFlow()

    private val _localDevices = MutableStateFlow<List<BleHrDevice>>(emptyList())
    val localDevices: StateFlow<List<BleHrDevice>> = _localDevices.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<BleHrDevice>>(emptyList())
    val scannedDevices: StateFlow<List<BleHrDevice>> = _scannedDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _lastSavedDevice = MutableStateFlow<SavedBleDevice?>(null)
    val lastSavedDevice: StateFlow<SavedBleDevice?> = _lastSavedDevice.asStateFlow()

    private val _autoReconnectEnabled = MutableStateFlow(true)
    val autoReconnectEnabled: StateFlow<Boolean> = _autoReconnectEnabled.asStateFlow()

    private val stopScanRunnable = Runnable { stopScan() }
    private val connectTimeoutRunnable = Runnable {
        if (_heartRateState.value.status == BleConnectionStatus.CONNECTING) {
            failConnection("连接超时，请确认手环在附近并开启心率广播后重试")
        }
    }

    fun loadLastDevice(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val addr = prefs.getString(KEY_LAST_ADDR, null)
        val name = prefs.getString(KEY_LAST_NAME, null)
        val connectedMs = prefs.getLong(KEY_LAST_TIME_MS, 0L)
        _autoReconnectEnabled.value = prefs.getBoolean(KEY_AUTO_RECONNECT, true)
        _lastSavedDevice.value = if (!addr.isNullOrBlank() && !name.isNullOrBlank()) {
            SavedBleDevice(name = name, address = addr, lastConnectedAtMs = connectedMs)
        } else {
            null
        }
    }

    fun setAutoReconnectEnabled(context: Context, enabled: Boolean) {
        val appCtx = context.applicationContext
        appContext = appCtx
        appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_RECONNECT, enabled)
            .apply()
        _autoReconnectEnabled.value = enabled
        if (enabled) {
            manualDisconnectThisSession = false
        }
    }

    fun clearLastSavedDevice(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAST_ADDR)
            .remove(KEY_LAST_NAME)
            .remove(KEY_LAST_TIME_MS)
            .apply()
        _lastSavedDevice.value = null
        publishDeviceLists()
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

    /**
     * Automatically reconnects to the remembered wearable on app launch/resume if enabled,
     * Bluetooth & permissions are ready, and the user has not manually disconnected this session.
     */
    fun tryAutoReconnectIfEnabled(context: Context): Boolean {
        val appCtx = context.applicationContext
        loadLastDevice(appCtx)
        if (!_autoReconnectEnabled.value || manualDisconnectThisSession) return false
        val saved = _lastSavedDevice.value ?: return false
        val currentStatus = _heartRateState.value.status
        if (currentStatus == BleConnectionStatus.CONNECTED ||
            currentStatus == BleConnectionStatus.CONNECTING
        ) {
            return false
        }
        if (!hasBlePermissions(appCtx) || !isBluetoothEnabled(appCtx)) return false
        connect(appCtx, address = saved.address, deviceName = saved.name)
        return true
    }

    /**
     * Reads BLE devices already connected locally to this phone (e.g., Mi Band connected to
     * Mi Fitness / Zepp Life) as well as system-paired BLE/wearable devices.
     */
    @SuppressLint("MissingPermission")
    fun refreshLocalDevices(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        loadLastDevice(appCtx)

        if (!hasBlePermissions(appCtx)) {
            localDeviceMap.clear()
            publishDeviceLists()
            return
        }

        val manager = appCtx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter
        if (manager == null || adapter == null || !adapter.isEnabled) {
            localDeviceMap.clear()
            publishDeviceLists()
            return
        }

        val lastAddr = _lastSavedDevice.value?.address
        val refreshed = LinkedHashMap<String, BleHrDevice>()

        // 1. Currently connected BLE GATT peripherals on this phone
        val connectedGatt = runCatching {
            manager.getConnectedDevices(BluetoothProfile.GATT)
        }.getOrDefault(emptyList())

        val connectedServer = runCatching {
            manager.getConnectedDevices(BluetoothProfile.GATT_SERVER)
        }.getOrDefault(emptyList())

        (connectedGatt + connectedServer).forEach { device ->
            val addr = device.address ?: return@forEach
            val existing = localDeviceMap[addr] ?: discoveredMap[addr]
            val friendlyName = resolveFriendlyName(device, existing?.name)
            val advHr = existing?.advertisesHeartRate == true
            refreshed[addr] = BleHrDevice(
                name = friendlyName,
                address = addr,
                rssi = existing?.rssi,
                advertisesHeartRate = advHr,
                source = BleDeviceSource.CONNECTED_LOCAL,
                category = BleDeviceClassifier.classifyCategory(friendlyName, advHr),
                isLastConnected = addr.equals(lastAddr, ignoreCase = true)
            )
        }

        // 2. System-paired Bluetooth devices (BLE / Dual-mode / recognizable wearables)
        val bonded = runCatching { adapter.bondedDevices }.getOrNull().orEmpty()
        bonded.forEach { device ->
            val addr = device.address ?: return@forEach
            if (refreshed.containsKey(addr)) return@forEach
            val existing = localDeviceMap[addr] ?: discoveredMap[addr]
            val friendlyName = resolveFriendlyName(device, existing?.name)
            val category = BleDeviceClassifier.classifyCategory(
                friendlyName,
                existing?.advertisesHeartRate == true
            )
            if (!isEligiblePairedDevice(device, category)) return@forEach

            refreshed[addr] = BleHrDevice(
                name = friendlyName,
                address = addr,
                rssi = existing?.rssi,
                advertisesHeartRate = existing?.advertisesHeartRate == true,
                source = BleDeviceSource.PAIRED_LOCAL,
                category = category,
                isLastConnected = addr.equals(lastAddr, ignoreCase = true)
            )
        }

        localDeviceMap.clear()
        localDeviceMap.putAll(refreshed)
        publishDeviceLists()
    }

    @SuppressLint("MissingPermission")
    fun startScan(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        loadLastDevice(appCtx)
        refreshLocalDevices(appCtx)

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
        publishDeviceLists()
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
        manualDisconnectThisSession = false
        stopScan()

        if (!hasBlePermissions(appCtx)) {
            failConnection("请先授予蓝牙连接权限")
            return
        }

        val adapter = getBluetoothAdapter(appCtx)
        if (adapter == null || !adapter.isEnabled) {
            failConnection("请先开启手机蓝牙")
            return
        }
        if (activeGatt != null && address.equals(_heartRateState.value.deviceAddress, ignoreCase = true)) return

        val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull()
        if (device == null) {
            failConnection("无效的蓝牙设备地址")
            return
        }

        closeActiveGatt()
        val friendlyName = resolveFriendlyName(device, deviceName)

        _heartRateState.value = HeartRateState(
            status = BleConnectionStatus.CONNECTING,
            deviceName = friendlyName,
            deviceAddress = address,
            bpm = null,
            errorMessage = null
        )

        val gatt = try {
            device.connectGatt(
                appCtx,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )
        } catch (se: SecurityException) {
            Log.w(TAG, "Missing BLUETOOTH_CONNECT permission", se)
            failConnection("蓝牙连接权限不足")
            return
        } catch (e: Exception) {
            Log.e(TAG, "BLE connectGatt failed", e)
            failConnection("连接设备失败，请重试")
            return
        }
        if (gatt == null) {
            failConnection("无法发起蓝牙连接，请重试")
            return
        }
        activeGatt = gatt
        mainHandler.postDelayed(connectTimeoutRunnable, CONNECT_TIMEOUT_MS)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        manualDisconnectThisSession = true
        stopScan()
        closeActiveGatt()
        _heartRateState.value = HeartRateState(status = BleConnectionStatus.DISCONNECTED)
    }

    @SuppressLint("MissingPermission")
    private fun closeActiveGatt() {
        mainHandler.removeCallbacks(connectTimeoutRunnable)
        val gatt = activeGatt
        activeGatt = null
        if (gatt != null) {
            runCatching { gatt.disconnect() }
            runCatching { gatt.close() }
        }
    }

    private fun failConnection(message: String) {
        closeActiveGatt()
        _heartRateState.update {
            it.copy(status = BleConnectionStatus.ERROR, bpm = null, errorMessage = message)
        }
    }

    private fun getBluetoothAdapter(context: Context): BluetoothAdapter? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter
    }

    @SuppressLint("MissingPermission")
    private fun resolveFriendlyName(device: BluetoothDevice, fallbackName: String? = null): String {
        val alias = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { device.alias }.getOrNull()?.trim()
        } else {
            null
        }
        val sysName = runCatching { device.name }.getOrNull()?.trim()
        return alias.takeUnless { it.isNullOrBlank() }
            ?: fallbackName?.trim().takeUnless { it.isNullOrBlank() }
            ?: sysName.takeUnless { it.isNullOrBlank() }
            ?: "蓝牙设备 (${BleDeviceClassifier.shortAddressTail(device.address ?: "")})"
    }

    @SuppressLint("MissingPermission")
    private fun isEligiblePairedDevice(device: BluetoothDevice, category: BleDeviceCategory): Boolean {
        if (category != BleDeviceCategory.OTHER) return true
        val type = runCatching { device.type }.getOrDefault(BluetoothDevice.DEVICE_TYPE_UNKNOWN)
        if (type == BluetoothDevice.DEVICE_TYPE_LE || type == BluetoothDevice.DEVICE_TYPE_DUAL) {
            return true
        }
        // Skip pure Classic Bluetooth car stereos/speakers/phones/computers
        val majorClass = runCatching { device.bluetoothClass?.majorDeviceClass }.getOrNull()
        if (type == BluetoothDevice.DEVICE_TYPE_CLASSIC) {
            return majorClass == BluetoothClass.Device.Major.WEARABLE ||
                majorClass == BluetoothClass.Device.Major.HEALTH
        }
        return majorClass != BluetoothClass.Device.Major.AUDIO_VIDEO &&
            majorClass != BluetoothClass.Device.Major.PHONE &&
            majorClass != BluetoothClass.Device.Major.COMPUTER
    }

    private fun saveLastDevice(name: String, address: String) {
        val ctx = appContext ?: return
        val nowMs = System.currentTimeMillis()
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_NAME, name)
            .putString(KEY_LAST_ADDR, address)
            .putLong(KEY_LAST_TIME_MS, nowMs)
            .apply()
        _lastSavedDevice.value = SavedBleDevice(
            name = name,
            address = address,
            lastConnectedAtMs = nowMs
        )
        publishDeviceLists()
    }

    private fun publishDeviceLists() {
        val lastAddr = _lastSavedDevice.value?.address
        val locals = localDeviceMap.values.map { item ->
            item.copy(isLastConnected = item.address.equals(lastAddr, ignoreCase = true))
        }
        _localDevices.value = BleDeviceClassifier.sortDevices(locals)

        // Nearby scanned devices exclude ones already displayed in the local section
        val nearbyOnly = discoveredMap.values
            .filterNot { localDeviceMap.containsKey(it.address) }
            .map { item ->
                item.copy(isLastConnected = item.address.equals(lastAddr, ignoreCase = true))
            }
        _scannedDevices.value = BleDeviceClassifier.sortDevices(nearbyOnly)
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val address = device.address ?: return
            val advUuids = result.scanRecord?.serviceUuids.orEmpty()
            val advertisesHr = advUuids.any { it == ParcelUuid(HEART_RATE_SERVICE_UUID) }

            val alias = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { device.alias }.getOrNull()?.trim()
            } else {
                null
            }
            val rawName = alias.takeUnless { it.isNullOrBlank() }
                ?: result.scanRecord?.deviceName?.trim().takeUnless { it.isNullOrBlank() }
                ?: runCatching { device.name }.getOrNull()?.trim().takeUnless { it.isNullOrBlank() }

            // If this device is already in local connected/paired list, enrich it with live RSSI & HR badge
            val existingLocal = localDeviceMap[address]
            if (existingLocal != null) {
                val updatedName = rawName ?: existingLocal.name
                val mergedHr = existingLocal.advertisesHeartRate || advertisesHr
                localDeviceMap[address] = existingLocal.copy(
                    name = updatedName,
                    rssi = result.rssi,
                    advertisesHeartRate = mergedHr,
                    category = BleDeviceClassifier.classifyCategory(updatedName, mergedHr)
                )
                publishDeviceLists()
                return
            }

            // Skip unnamed devices unless they explicitly advertise the Heart Rate Service
            if (rawName.isNullOrBlank() && !advertisesHr) return
            val displayName = rawName ?: "BLE 心率设备"
            val category = BleDeviceClassifier.classifyCategory(displayName, advertisesHr)
            val lastAddr = _lastSavedDevice.value?.address

            discoveredMap[address] = BleHrDevice(
                name = displayName,
                address = address,
                rssi = result.rssi,
                advertisesHeartRate = advertisesHr,
                source = BleDeviceSource.NEARBY_SCAN,
                category = category,
                isLastConnected = address.equals(lastAddr, ignoreCase = true)
            )
            publishDeviceLists()
        }

        override fun onScanFailed(errorCode: Int) {
            if (!isScanningInternal) return
            isScanningInternal = false
            _isScanning.value = false
            _heartRateState.update {
                it.copy(errorMessage = "蓝牙扫描失败 (错误码 $errorCode)")
            }
        }
    }

    private fun onActiveGatt(gatt: BluetoothGatt, action: () -> Unit) {
        // Serialize callbacks with connect/disconnect and ignore callbacks after a GATT is replaced.
        mainHandler.post { if (activeGatt === gatt) action() }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) = onActiveGatt(gatt) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "GATT connection error: status=$status newState=$newState")
                failConnection("蓝牙连接中断（错误码 $status），请确认设备在附近后重试")
                return@onActiveGatt
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    val discovered = runCatching { gatt.discoverServices() }.getOrDefault(false)
                    if (!discovered) failConnection("无法读取设备蓝牙服务")
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (_heartRateState.value.status == BleConnectionStatus.CONNECTING) {
                        failConnection("设备在完成心率订阅前断开，请重试")
                    } else {
                        closeActiveGatt()
                        _heartRateState.update {
                            it.copy(
                                status = BleConnectionStatus.DISCONNECTED,
                                bpm = null,
                                errorMessage = "蓝牙连接已断开"
                            )
                        }
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) = onActiveGatt(gatt) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failConnection("蓝牙服务发现失败（错误码 $status）")
                return@onActiveGatt
            }

            val hrService = gatt.getService(HEART_RATE_SERVICE_UUID)
            val hrChar = hrService?.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)
            if (hrChar == null) {
                failConnection("未发现标准蓝牙心率服务，请确认设备支持并已开启心率广播")
                return@onActiveGatt
            }

            val notifyEnabled = runCatching {
                gatt.setCharacteristicNotification(hrChar, true)
            }.getOrDefault(false)

            val cccd = hrChar.getDescriptor(CCCD_UUID)
            if (!notifyEnabled || cccd == null) {
                failConnection("无法订阅设备的心率通知")
                return@onActiveGatt
            }

            val writeStarted = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                        BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(cccd)
                }
            }.getOrDefault(false)

            if (!writeStarted) {
                failConnection("发起心率订阅失败，请重试")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) =
            onActiveGatt(gatt) {
                if (_heartRateState.value.status != BleConnectionStatus.CONNECTING ||
                    descriptor.uuid != CCCD_UUID || descriptor.characteristic?.uuid != HEART_RATE_MEASUREMENT_UUID
                ) return@onActiveGatt
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    failConnection("设备未接受心率订阅（错误码 $status），请确认已开启心率广播")
                    return@onActiveGatt
                }

                mainHandler.removeCallbacks(connectTimeoutRunnable)
                val currentName = resolveFriendlyName(gatt.device, _heartRateState.value.deviceName)
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
                onActiveGatt(gatt) { handleHeartRatePacket(value) }
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
                val value = characteristic.value?.copyOf() ?: return
                onActiveGatt(gatt) { handleHeartRatePacket(value) }
            }
        }
    }

    private fun handleHeartRatePacket(bytes: ByteArray) {
        if (!_heartRateState.value.isConnected) return
        val bpm = parseHeartRateBpm(bytes) ?: return
        _heartRateState.update { current ->
            current.copy(
                bpm = bpm,
                lastUpdatedMs = SystemClock.elapsedRealtime(),
                errorMessage = null
            )
        }
    }
}
