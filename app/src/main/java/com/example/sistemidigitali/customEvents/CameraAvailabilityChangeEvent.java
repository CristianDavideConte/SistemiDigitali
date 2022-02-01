package com.example.sistemidigitali.customEvents;

public class CameraAvailabilityChangeEvent {
    private boolean isAvailable;

    public CameraAvailabilityChangeEvent(boolean isAvailable) {
        this.isAvailable = isAvailable;
    }

    public boolean isAvailable() {
        return isAvailable;
    }
}
