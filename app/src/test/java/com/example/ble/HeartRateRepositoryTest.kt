package com.example.ble

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HeartRateRepositoryTest {
    private val address = "02:00:00:00:00:01"
    private lateinit var app: Application
    private lateinit var adapter: BluetoothAdapter

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        val manager = app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        adapter = requireNotNull(manager.adapter)
        shadowOf(adapter).setEnabled(true)
        HeartRateRepository.disconnect()
        HeartRateRepository.clearLastSavedDevice(app)
    }

    @After
    fun tearDown() {
        HeartRateRepository.disconnect()
        HeartRateRepository.clearLastSavedDevice(app)
        idleMain()
    }

    @Test
    fun connectionWithoutCallbacksTimesOutAndClosesGatt() {
        val gatt = connect()
        assertEquals(BleConnectionStatus.CONNECTING, HeartRateRepository.heartRateState.value.status)

        advanceSeconds(20)

        assertEquals(BleConnectionStatus.ERROR, HeartRateRepository.heartRateState.value.status)
        assertTrue(shadowOf(gatt).isClosed)
        assertNull(HeartRateRepository.lastSavedDevice.value)
    }

    @Test
    fun doesNotSaveDeviceWhenSubscriptionIsRejected() {
        val gatt = connect()
        val callback = shadowOf(gatt).gattCallback
        val descriptor = startSubscription(gatt, callback)
        assertEquals(BleConnectionStatus.CONNECTING, HeartRateRepository.heartRateState.value.status)
        assertNull(HeartRateRepository.lastSavedDevice.value)

        callback.onDescriptorWrite(gatt, descriptor, BluetoothGatt.GATT_FAILURE)
        idleMain()

        assertEquals(BleConnectionStatus.ERROR, HeartRateRepository.heartRateState.value.status)
        assertTrue(shadowOf(gatt).isClosed)
        assertNull(HeartRateRepository.lastSavedDevice.value)
    }

    @Test
    fun confirmedSubscriptionKeepsConnectionAndAcceptsHeartRate() {
        val gatt = connect()
        val callback = shadowOf(gatt).gattCallback
        val descriptor = startSubscription(gatt, callback)
        callback.onDescriptorWrite(gatt, descriptor, BluetoothGatt.GATT_SUCCESS)
        idleMain()

        assertEquals(BleConnectionStatus.CONNECTED, HeartRateRepository.heartRateState.value.status)
        assertEquals(address, HeartRateRepository.lastSavedDevice.value?.address)
        assertNull(HeartRateRepository.heartRateState.value.bpm)

        advanceSeconds(20)
        callback.onCharacteristicChanged(gatt, requireNotNull(descriptor.characteristic), byteArrayOf(0, 86))
        idleMain()

        assertFalse(shadowOf(gatt).isClosed)
        assertEquals(86, HeartRateRepository.heartRateState.value.bpm)
    }

    @Test
    fun callbackFromFirstDeviceDoesNotCloseSecondConnection() {
        val previous = connect()
        val nextAddress = "02:00:00:00:00:02"
        val current = connect(nextAddress)

        shadowOf(previous).gattCallback.onServicesDiscovered(previous, BluetoothGatt.GATT_FAILURE)
        idleMain()

        assertFalse(shadowOf(current).isClosed)
        assertEquals(nextAddress, HeartRateRepository.heartRateState.value.deviceAddress)
        assertEquals(BleConnectionStatus.CONNECTING, HeartRateRepository.heartRateState.value.status)
    }

    private fun connect(deviceAddress: String = address): BluetoothGatt {
        HeartRateRepository.connect(app, deviceAddress, "Test Band")
        return shadowOf(adapter.getRemoteDevice(deviceAddress)).bluetoothGatts.last()
    }

    private fun startSubscription(gatt: BluetoothGatt, callback: BluetoothGattCallback): BluetoothGattDescriptor {
        val service = BluetoothGattService(HeartRateRepository.HEART_RATE_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            HeartRateRepository.HEART_RATE_MEASUREMENT_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            0
        )
        val descriptor = BluetoothGattDescriptor(HeartRateRepository.CCCD_UUID, BluetoothGattDescriptor.PERMISSION_WRITE)
        characteristic.addDescriptor(descriptor)
        service.addCharacteristic(characteristic)

        shadowOf(gatt).apply {
            addDiscoverableService(service)
            allowCharacteristicNotification(characteristic)
            // Let the write enqueue successfully, but deliver its result from the test.
            setGattCallback(object : BluetoothGattCallback() {
                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    callback.onServicesDiscovered(gatt, status)
                }
            })
        }
        callback.onConnectionStateChange(gatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        idleMain()
        return descriptor
    }

    private fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

    private fun advanceSeconds(seconds: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds))
    }
}
