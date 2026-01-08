package com.example.aidlplayer.client.car

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.aidlplayer.IBluetoothManager
import com.example.aidlplayer.ICarControl
import com.example.aidlplayer.IWifiManager
import com.example.aidlplayer.client.R

class CarDashboardActivity : AppCompatActivity() {

    private var wifiManager: IWifiManager? = null
    private var btManager: IBluetoothManager? = null
    private var carControl: ICarControl? = null

    private lateinit var tvWifiStatus: TextView
    private lateinit var tvBtStatus: TextView
    private lateinit var tvTemp: TextView

    private val wifiConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            wifiManager = IWifiManager.Stub.asInterface(service)
            tvWifiStatus.text = "WiFi Manager Connected"
            checkWifiState()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            wifiManager = null
            tvWifiStatus.text = "WiFi Manager Disconnected"
        }
    }

    private val btConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            btManager = IBluetoothManager.Stub.asInterface(service)
            tvBtStatus.text = "BT Manager Connected"
            checkBtState()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            btManager = null
            tvBtStatus.text = "BT Manager Disconnected"
        }
    }

    private val carConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            carControl = ICarControl.Stub.asInterface(service)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            carControl = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_car_dashboard)

        tvWifiStatus = findViewById(R.id.tvWifiStatus)
        tvBtStatus = findViewById(R.id.tvBtStatus)
        tvTemp = findViewById(R.id.tvTemp)

        findViewById<Button>(R.id.btnConnectWifi).setOnClickListener {
            bindService(
                createExplicitIntent("com.example.aidlplayer.action.BIND_WIFI"),
                wifiConnection,
                Context.BIND_AUTO_CREATE
            )
        }

        findViewById<Button>(R.id.btnConnectBt).setOnClickListener {
            bindService(
                createExplicitIntent("com.example.aidlplayer.action.BIND_BLUETOOTH"),
                btConnection,
                Context.BIND_AUTO_CREATE
            )
        }

        findViewById<Button>(R.id.btnConnectCar).setOnClickListener {
             bindService(
                createExplicitIntent("com.example.aidlplayer.action.BIND_CAR_CONTROL"),
                carConnection,
                Context.BIND_AUTO_CREATE
            )
        }
        
        findViewById<SeekBar>(R.id.seekBarTemp).setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    tvTemp.text = "$progress°C"
                    try {
                        carControl?.setHvacTemperature(0, progress.toFloat())
                    } catch (e: RemoteException) {
                        e.printStackTrace()
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun checkWifiState() {
        try {
            val enabled = wifiManager?.isWifiEnabled == true
            val ssid = wifiManager?.ssid
            tvWifiStatus.text = "WiFi: $enabled, SSID: $ssid"
        } catch (e: RemoteException) { e.printStackTrace() }
    }

    private fun checkBtState() {
         try {
            val devices = btManager?.pairedDevices
            tvBtStatus.text = "BT Devices: ${devices?.joinToString()}"
        } catch (e: RemoteException) { e.printStackTrace() }
    }

    private fun createExplicitIntent(action: String): Intent {
        val intent = Intent(action)
        intent.setPackage("com.example.aidlplayer.service")
        return intent
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unbind conservatively
        try { unbindService(wifiConnection) } catch (e: Exception) {}
        try { unbindService(btConnection) } catch (e: Exception) {}
        try { unbindService(carConnection) } catch (e: Exception) {}
    }
}
