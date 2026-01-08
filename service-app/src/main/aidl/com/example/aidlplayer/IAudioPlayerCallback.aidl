// IAudioPlayerCallback.aidl
package com.example.aidlplayer;

/**
 * Interface definition for a callback to be invoked when the audio player state changes.
 * This allows the service to communicate back to the client.
 */
oneway interface IAudioPlayerCallback {
    // Player is playing, callback with duration
    void onStarted(long durationMs);
    
    // Player paused
    void onPaused();
    
    // Player stopped
    void onStopped();
    
    // Playback completed
    void onCompleted();
    
    // Error occurred
    void onError(String message);
    
    // Periodic progress update (optional, but good for UI)
    void onProgress(long currentPositionMs);
}
