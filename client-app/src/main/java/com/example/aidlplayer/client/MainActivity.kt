package com.example.aidlplayer.client

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileNotFoundException
import androidx.core.app.ActivityCompat
import com.example.aidlplayer.IAudioPlayer
import com.example.aidlplayer.IAudioPlayerCallback
import com.example.aidlplayer.client.databinding.ActivityMainBinding
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Client Activity that connects to the remote Audio Service via AIDL.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var audioService: IAudioPlayer? = null
    private var isBound = false
    
    // Executor for updating UI progress
    private val uiUpdateExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    // Modern way to pick files (resolves permissions)
    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            playFileFromUri(uri)
        } else {
            Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show()
        }
    }

    // INTERVIEW NOTE:
    // This callback is called from the Service process.
    // By default, AIDL callbacks arrive on a Binder thread pool in the client process.
    // If we touch UI here, we MUST usage runOnUiThread { ... }
    private val callback = object : IAudioPlayerCallback.Stub() {
        override fun onStarted(durationMs: Long) {
            runOnUiThread {
                binding.tvStatus.text = "Status: Playing"
                binding.seekBar.max = durationMs.toInt()
                startProgressUpdater()
            }
        }

        override fun onPaused() {
            runOnUiThread {
                binding.tvStatus.text = "Status: Paused"
            }
        }

        override fun onStopped() {
            runOnUiThread {
                binding.tvStatus.text = "Status: Stopped"
                binding.seekBar.progress = 0
            }
        }

        override fun onCompleted() {
            runOnUiThread {
                binding.tvStatus.text = "Status: Completed"
                binding.seekBar.progress = 0
            }
        }

        override fun onError(message: String?) {
            runOnUiThread {
                binding.tvStatus.text = "Error: $message"
                Toast.makeText(this@MainActivity, "Error: $message", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onProgress(currentPositionMs: Long) {
            // Optional: If service pushes progress. We are pulling it for now.
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d("MainActivity", "Service Connected")
            audioService = IAudioPlayer.Stub.asInterface(service)
            isBound = true
            binding.tvStatus.text = "Connected to Service"
            binding.btnConnect.text = "Disconnect"

            try {
                // INTERVIEW NOTE:
                // Always register callbacks immediately after connection if needed.
                // We also set a DeathRecipient implementation implicitly by handling process death elsewhere,
                // but RemoteCallbackList on server side handles the other end.
                // We can also use linkToDeath here if we want strict monitoring.
                audioService?.registerCallback(callback)
                
                // Set initial linkToDeath to monitor service crash
                service?.linkToDeath({
                    runOnUiThread {
                        binding.tvStatus.text = "Service Died (Process Crash)"
                        handleUnbind()
                    }
                }, 0)
                
            } catch (e: RemoteException) {
                e.printStackTrace()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d("MainActivity", "Service Disconnected")
            // This happens only if the service crashes or is killed by OS.
            // It does NOT happen on unbind().
            handleUnbind()
            binding.tvStatus.text = "Service Disconnected (Crash)"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        checkPermissions()
    }

    private fun setupListeners() {
        binding.btnConnect.setOnClickListener {
            if (isBound) {
                unbindAudioService()
            } else {
                bindAudioService()
            }
        }

        binding.btnPickFile.setOnClickListener {
             if (isBound) {
                 filePickerLauncher.launch("audio/*")
             } else {
                 Toast.makeText(this, "Service not bound", Toast.LENGTH_SHORT).show()
             }
        }

        // INTERVIEW NOTE:
        // We use a File Picker + ParcelFileDescriptor instead of a raw file path string.
        // This is crucial for Android 10+ (Scoped Storage) where raw file access is restricted.
        binding.btnPlay.setOnClickListener {
             if (!isBound) {
                Toast.makeText(this, "Service not bound", Toast.LENGTH_SHORT).show()
             } else {
                 Toast.makeText(this, "Please pick a file first", Toast.LENGTH_SHORT).show()
             }
        }

        binding.btnPause.setOnClickListener {
            try { audioService?.pause() } catch (e: RemoteException) { e.printStackTrace() }
        }

        binding.btnStop.setOnClickListener {
            try { audioService?.stop() } catch (e: RemoteException) { e.printStackTrace() }
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && isBound) {
                    try { audioService?.seekTo(progress.toLong()) } catch (e: RemoteException) { e.printStackTrace() }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun bindAudioService() {
        val intent = Intent("com.example.aidlplayer.action.BIND_PLAYER")
        intent.setPackage("com.example.aidlplayer.service") // Explicit Intent is required for API 21+
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun unbindAudioService() {
        if (isBound) {
            try {
                audioService?.unregisterCallback(callback)
            } catch (e: RemoteException) {
                e.printStackTrace()
            }
            unbindService(serviceConnection)
            handleUnbind()
        }
    }

    private fun handleUnbind() {
        isBound = false
        audioService = null
        binding.btnConnect.text = "Connect"
        binding.tvStatus.text = "Disconnected"
    }

    private fun startProgressUpdater() {
        uiUpdateExecutor.scheduleWithFixedDelay({
            if (isBound && audioService != null) {
                try {
                    val isPlaying = audioService?.isPlaying == true
                    if (isPlaying) {
                        val current = audioService?.currentPosition ?: 0L
                        val total = audioService?.duration ?: 0L
                        runOnUiThread {
                            binding.seekBar.progress = current.toInt()
                            binding.tvDuration.text = formatTime(current.toInt()) + " / " + formatTime(total.toInt())
                        }
                    }
                } catch (e: RemoteException) {
                    e.printStackTrace()
                }
            }
        }, 0, 500, TimeUnit.MILLISECONDS)
    }
    
    private fun formatTime(ms: Int): String {
        val sec = (ms / 1000) % 60
        val min = (ms / (1000 * 60)) % 60
        return String.format("%02d:%02d", min, sec)
    }

    private fun playFileFromUri(uri: Uri) {
        try {
            // Open the URI as a read-only FileDescriptor
            // This works because the Client (us) has permission to read this URI (grant from Picker)
            val pfd: ParcelFileDescriptor? = contentResolver.openFileDescriptor(uri, "r")
            
            if (pfd != null) {
                audioService?.playFile(pfd)
                // Note: pfd is closed by the Service (or we can close it here if we dup it, 
                // but usually passing via Binder transfers ownership or we rely on Service to close its copy).
                // Actually, AIDL 'in' parameters for PFD map to the same underlying file struct, 
                // but Binder duplicates the descriptor. We should close our copy.
                pfd.close()
                binding.tvStatus.text = "Status: Sending File..."
            }
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
        } catch (e: RemoteException) {
            e.printStackTrace()
            Toast.makeText(this, "Remote Error", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
             e.printStackTrace()
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
             if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                 ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO), PERMISSION_REQUEST_CODE)
             }
        } else {
             if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                 ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), PERMISSION_REQUEST_CODE)
             }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        uiUpdateExecutor.shutdownNow()
        if (isBound) {
            unbindService(serviceConnection)
        }
    }
}
