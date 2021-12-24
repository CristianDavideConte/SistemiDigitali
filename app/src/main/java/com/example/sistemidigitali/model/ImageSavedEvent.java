package com.example.sistemidigitali.model;

public class ImageSavedEvent {

    private String error;

    public ImageSavedEvent(String error) {
        this.error = error;
    }

    public String getError() {
        return error;
    }
}
