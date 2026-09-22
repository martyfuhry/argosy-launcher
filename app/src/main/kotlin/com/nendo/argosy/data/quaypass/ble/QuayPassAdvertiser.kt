package com.nendo.argosy.data.quaypass.ble

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope

class QuayPassAdvertiser(
    private val application: Application,
    scope: CoroutineScope,
    isExchangeActive: () -> Boolean = { false }
) {

    private val bluetoothAdapter by lazy {
        (application.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    private var isAdvertising = false

    private val rotator = EphemeralIdRotator(
        scope = scope,
        isBusy = isExchangeActive,
        onRotate = { id -> restart(id) }
    )

    fun start() {
        if (isAdvertising) return
        if (!advertise(rotator.currentId)) return
        rotator.start()
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        rotator.stop()
        if (!isAdvertising) return
        bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        isAdvertising = false
        Log.d(TAG, "QuayPass advertising stopped")
    }

    @SuppressLint("MissingPermission")
    private fun restart(ephemeralId: ByteArray) {
        if (!isAdvertising) return
        bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        isAdvertising = false
        advertise(ephemeralId)
    }

    @SuppressLint("MissingPermission")
    private fun advertise(ephemeralId: ByteArray): Boolean {
        val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser ?: run {
            Log.w(TAG, "BLE advertiser unavailable")
            return false
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val manufacturerData =
            QuayPassConfig.MAGIC_BYTES +
                byteArrayOf(QuayPassConfig.PROTOCOL_MAJOR) +
                ephemeralId

        val data = AdvertiseData.Builder()
            .addManufacturerData(QuayPassConfig.MANUFACTURER_ID, manufacturerData)
            .build()

        advertiser.startAdvertising(settings, data, advertiseCallback)
        return true
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            isAdvertising = true
            Log.d(TAG, "QuayPass advertising started")
        }

        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
            Log.e(TAG, "QuayPass advertising failed: $errorCode")
        }
    }

    companion object {
        private const val TAG = "QuayPassAdvertiser"
    }
}
