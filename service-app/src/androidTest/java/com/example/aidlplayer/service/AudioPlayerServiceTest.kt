package com.example.aidlplayer.service

import android.content.Intent
import android.os.IBinder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import com.example.aidlplayer.IAudioPlayer
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeoutException

@RunWith(AndroidJUnit4::class)
class AudioPlayerServiceTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    @Test
    fun testServiceBinding() {
        // Create the intent to bind to the AudioPlayerService
        val serviceIntent = Intent(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext,
            AudioPlayerService::class.java
        ).apply {
            action = "com.example.aidlplayer.action.BIND_PLAYER"
        }

        // Bind to the service
        val binder: IBinder = serviceRule.bindService(serviceIntent)

        // Verify that the binder is not null
        assertNotNull("Binder should not be null", binder)

        // Convert binder to AIDL interface
        val audioPlayer = IAudioPlayer.Stub.asInterface(binder)
        assertNotNull("IAudioPlayer interface should not be null", audioPlayer)
        
        // Basic interaction check
        // Note: We can't easily verify internal state without exposing it or mocking MediaPlayer,
        // but we can ensure the IPC call succeeds without crashing.
        val isPlaying = audioPlayer.isPlaying
        // Initially it should be false
        assertTrue("Player should not be playing initially", !isPlaying)
    }
}
