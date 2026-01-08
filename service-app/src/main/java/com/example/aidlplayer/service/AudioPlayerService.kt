package com.example.aidlplayer.service

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.IBinder
import android.os.RemoteCallbackList
import android.os.RemoteException
import android.util.Log
import com.example.aidlplayer.IAudioPlayer
import com.example.aidlplayer.IAudioPlayerCallback
import android.os.ParcelFileDescriptor
import java.io.IOException

/**
 * Service that manages Audio Playback and exposes an AIDL interface.
 *
 * INTERVIEW NOTE:
 * - This service runs in a separate process (configured in Manifest).
 * - AIDL calls come in on valid Binder threads, so we must be thread-safe.
 */
class AudioPlayerService : Service() {

    companion object {
        private const val TAG = "AudioPlayerService"
    }

    // INTERVIEW NOTE:
    // RemoteCallbackList is the standard way to manage AIDL callbacks.
    // It handles:
    // 1. Thread safety (internally synchronized).
    // 2. Automatic unregistering when the client process dies (Link-to-Death).
    // 3. Broadcasting to multiple clients.
    private val callbacks = RemoteCallbackList<IAudioPlayerCallback>()

    private var mediaPlayer: MediaPlayer? = null
    
    // Shared state must be synchronized or use atomic variables
    private val lock = Any()
    
    private var isPlayerPrepared = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Service created")
        initializeMediaPlayer()
    }

    private fun initializeMediaPlayer() {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setOnCompletionListener {
                    Log.d(TAG, "Playback completed")
                    notifyCallbacks { onCompleted() }
                }
                setOnErrorListener { _, what, extra ->
                    val errorMsg = "MediaPlayer Error: what=$what, extra=$extra"
                    Log.e(TAG, errorMsg)
                    notifyCallbacks { onError(errorMsg) }
                    isPlayerPrepared = false
                    true // Handled
                }
                setOnPreparedListener { mp ->
                    isPlayerPrepared = true
                    mp.start()
                    notifyCallbacks { onStarted(mp.duration.toLong()) }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "onBind: Client bound")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind: Client unbound")
        // Return true to allow rebind if needed, or false otherwise.
        // Usually good cleanup place if no clients are left.
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        releasePlayer()
        callbacks.kill() // Unregister all callbacks
    }

    private fun releasePlayer() {
        synchronized(lock) {
            mediaPlayer?.release()
            mediaPlayer = null
            isPlayerPrepared = false
        }
    }

    // INTERVIEW NOTE:
    // The Stub implementation constitutes the Binder object passed to the client.
    // Methods here are called on Binder threads, NOT the main UI thread.
    private val binder = object : IAudioPlayer.Stub() {
        
        override fun play(path: String?) {
            Log.d(TAG, "AIDL play: $path")
            if (path.isNullOrEmpty()) return

            synchronized(lock) {
                try {
                    mediaPlayer?.apply {
                        reset()
                        isPlayerPrepared = false
                        setDataSource(path)
                        prepareAsync() // Prepare asynchronously to not block binder thread too long
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Error setting data source", e)
                    notifyCallbacks { onError("Error playing file: ${e.message}") }
                }
            }
        }

        override fun pause() {
            Log.d(TAG, "AIDL pause")
            synchronized(lock) {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        it.pause()
                        notifyCallbacks { onPaused() }
                    }
                }
            }
        }

        override fun playFile(pfd: ParcelFileDescriptor?) {
            Log.d(TAG, "AIDL playFile")
            if (pfd == null) return

            synchronized(lock) {
                try {
                    mediaPlayer?.apply {
                        reset()
                        isPlayerPrepared = false
                        // Use the FileDescriptor directly
                        setDataSource(pfd.fileDescriptor)
                        prepareAsync() 
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Error setting data source from PFD", e)
                    notifyCallbacks { onError("Error playing file descriptor: ${e.message}") }
                } finally {
                    // Important: Close the PFD after passing the FD to MediaPlayer
                    // MediaPlayer duplicates the FD natively, so we can close this one.
                    try {
                        pfd.close()
                    } catch (e: IOException) {
                         Log.e(TAG, "Error closing PFD", e)
                    }
                }
            }
        }

        override fun stop() {
            Log.d(TAG, "AIDL stop")
            synchronized(lock) {
                mediaPlayer?.let {
                    if (it.isPlaying || isPlayerPrepared) {
                        it.stop()
                        isPlayerPrepared = false // Needs prepare again
                        notifyCallbacks { onStopped() }
                    }
                }
            }
        }

        override fun seekTo(positionMs: Long) {
             synchronized(lock) {
                if (isPlayerPrepared) {
                     mediaPlayer?.seekTo(positionMs.toInt())
                }
             }
        }

        override fun getCurrentPosition(): Long {
             synchronized(lock) {
                 return if (isPlayerPrepared) (mediaPlayer?.currentPosition ?: 0).toLong() else 0L
             }
        }

        override fun getDuration(): Long {
            synchronized(lock) {
                return if (isPlayerPrepared) (mediaPlayer?.duration ?: 0).toLong() else 0L
            }
        }

        override fun isPlaying(): Boolean {
             synchronized(lock) {
                return try {
                    mediaPlayer?.isPlaying == true
                } catch (e: IllegalStateException) {
                    false
                }
             }
        }

        override fun registerCallback(callback: IAudioPlayerCallback?) {
            callback?.let {
                Log.d(TAG, "Registering callback")
                callbacks.register(it)
            }
        }

        override fun unregisterCallback(callback: IAudioPlayerCallback?) {
            callback?.let {
                Log.d(TAG, "Unregistering callback")
                callbacks.unregister(it)
            }
        }
    }

    // Helper to broadcast to all registered callbacks
    private fun notifyCallbacks(action: IAudioPlayerCallback.() -> Unit) {
        val count = callbacks.beginBroadcast()
        for (i in 0 until count) {
            try {
                val item = callbacks.getBroadcastItem(i)
                item.action()
            } catch (e: RemoteException) {
                // The RemoteCallbackList handles removal of dead callbacks,
                // but exceptions can still happen during transmission.
                Log.e(TAG, "Error notifying callback", e)
            }
        }
        callbacks.finishBroadcast()
    }
}
