package com.example.aidlplayer;

/**
 * Interface for controlling WiFi via the remote service.
 */
interface IWifiManager {
    /**
     * Enable or disable WiFi.
     */
    void setWifiEnabled(boolean enabled);
    
    /**
     * Check if WiFi is currently enabled.
     */
    boolean isWifiEnabled();
    
    /**
     * Get the current SSID if connected.
     */
    String getSsid();
}
