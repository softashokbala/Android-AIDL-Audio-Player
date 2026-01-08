// IAudioPlayer.aidl
package com.example.aidlplayer;

// Import the callback interface so we can use it as a parameter
import com.example.aidlplayer.IAudioPlayerCallback;
import android.os.ParcelFileDescriptor;


/**
 * Interface definition for the Audio Player Service.
 */
interface IAudioPlayer {
    /**
     * Start playback of a file.
     * @param path The path or URI string of the audio file.
     */
    void play(String path);

    /**
     * Play a file using a FileDescriptor (avoids permission issues across processes).
     */
    void playFile(in ParcelFileDescriptor pfd);

    /**
     * Pause playback.
     */
    void pause();

    /**
     * Stop playback and release resources.
     */
    void stop();

    /**
     * Seek to a specific position.
     * @param positionMs Position in milliseconds.
     */
    void seekTo(long positionMs);
    
    /**
     * Check if player is playing.
     */
    boolean isPlaying();

    /**
     * Get total duration of the track.
     */
    long getDuration();

    /**
     * Get current playback position.
     */
    long getCurrentPosition();

    /**
     * Registers a callback interface with the service.
     */
    void registerCallback(IAudioPlayerCallback callback);

    /**
     * Unregisters a callback interface from the service.
     */
    void unregisterCallback(IAudioPlayerCallback callback);
}
