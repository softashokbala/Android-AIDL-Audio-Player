package com.example.aidlplayer.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.aidlplayer.IBluetoothManager
import com.example.aidlplayer.ICarControl
import com.example.aidlplayer.INfcManager
import com.example.aidlplayer.IWifiManager

class ConnectivityService : Service() {

    companion object {
        const val ACTION_WIFI = "com.example.aidlplayer.action.BIND_WIFI"
        const val ACTION_BLUETOOTH = "com.example.aidlplayer.action.BIND_BLUETOOTH"
        const val ACTION_NFC = "com.example.aidlplayer.action.BIND_NFC"
        const val ACTION_CAR_CONTROL = "com.example.aidlplayer.action.BIND_CAR_CONTROL"
        private const val TAG = "ConnectivityService"
    }

    private val wifiBinder = object : IWifiManager.Stub() {
        private var isEnabled = false
        override fun setWifiEnabled(enabled: Boolean) {
            Log.d(TAG, "setWifiEnabled: $enabled")
            isEnabled = enabled
        }
        override fun isWifiEnabled(): Boolean = isEnabled
        override fun getSsid(): String = if (isEnabled) "Demo_WiFi_5G" else "<unknown>"
    }

    private val bluetoothBinder = object : IBluetoothManager.Stub() {
        private var isEnabled = false
        override fun setBluetoothEnabled(enabled: Boolean) {
             Log.d(TAG, "setBluetoothEnabled: $enabled")
             isEnabled = enabled
        }
        override fun isBluetoothEnabled(): Boolean = isEnabled
        override fun getPairedDevices(): Array<String> = arrayOf("MyPhone", "Headset X", "CarKit")
    }

    private val nfcBinder = object : INfcManager.Stub() {
        override fun isNfcEnabled(): Boolean = true // Mock always on
    }

    private val carControlBinder = object : ICarControl.Stub() {
        private val hvacTemps = mutableMapOf(0 to 22.0f, 1 to 22.0f)
        override fun setHvacTemperature(zone: Int, temp: Float) {
            Log.d(TAG, "setHvacTemperature: zone=$zone, temp=$temp")
            hvacTemps[zone] = temp
        }
        override fun getHvacTemperature(zone: Int): Float = hvacTemps[zone] ?: 22.0f
        override fun setWindowPosition(windowId: Int, position: Int) {
            Log.d(TAG, "setWindowPosition: id=$windowId, pos=$position")
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "onBind action: ${intent?.action}")
        return when (intent?.action) {
            ACTION_WIFI -> wifiBinder
            ACTION_BLUETOOTH -> bluetoothBinder
            ACTION_NFC -> nfcBinder
            ACTION_CAR_CONTROL -> carControlBinder
            else -> null
        }
    }
}
