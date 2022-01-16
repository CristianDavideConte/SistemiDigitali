package com.example.sistemidigitali.customEvents;

public class OverlayVisibilityChangeEvent {
    int visibility;

    public OverlayVisibilityChangeEvent(int visibility) {
        this.visibility = visibility;
    }

    public int getVisibility() {
        return visibility;
    }
}
