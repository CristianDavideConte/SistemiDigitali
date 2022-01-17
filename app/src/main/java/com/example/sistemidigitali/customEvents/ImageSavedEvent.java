package com.example.sistemidigitali.customEvents;

import android.net.Uri;

public class ImageSavedEvent {

    private String error;
    private Uri uri;

    public ImageSavedEvent(String error, Uri uri) {
        this.error = error;
        this.uri = uri;
    }

    /**
     * The result of the image saving operation.
     * @return "success" if the image has been successfully saved, the error message otherwise.
     */
    public String getError() {
        return error;
    }

    public Uri getUri() {
        return uri;
    }
}
