package com.example.sistemidigitali.model;

public class ImageSavedEvent {

    private String error;

    public ImageSavedEvent(String error) {
        this.error = error;
    }

    /**
     * The result of the image saving operation.
     * @return "success" if the image has been successfully saved, the error message otherwise.
     */
    public String getError() {
        return error;
    }
}
