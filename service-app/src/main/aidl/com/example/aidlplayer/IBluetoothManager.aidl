package com.example.aidlplayer;



/**
 * Interface for controlling Bluetooth via the remote service.
 */
interface IBluetoothManager {
    /**
     * Enable or disable Bluetooth.
     */
    void setBluetoothEnabled(boolean enabled);
    
    /**
     * Check if Bluetooth is enabled.
     */
    boolean isBluetoothEnabled();
    
    /**
     * Get list of paired device names.
     */
    String[] getPairedDevices();
}
