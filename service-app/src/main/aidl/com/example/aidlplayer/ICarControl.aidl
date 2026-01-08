package com.example.aidlplayer;

/**
 * Interface for Vehicle Control (HVAC, Windows).
 */
interface ICarControl {
    /**
     * Set temperature for a specific zone.
     * @param zone The zone ID (e.g., 0 for Driver, 1 for Passenger).
     * @param temp Temperature in Celsius.
     */
    void setHvacTemperature(int zone, float temp);
    
    /**
     * Get current temperature setting for a zone.
     */
    float getHvacTemperature(int zone);
    
    /**
     * Control window position.
     * @param windowId Window ID.
     * @param position Position (0 = closed, 100 = open).
     */
    void setWindowPosition(int windowId, int position);
}
