package com.example.sistemidigitali.customEvents;

import android.graphics.Bitmap;

public class PictureTakenEvent {

    private String error;
    Bitmap image;

    public PictureTakenEvent(Bitmap image, String error) {
        this.image = image;
        this.error = error;
    }

    public Bitmap getImage() {
        return image;
    }

    public String getError() {
        return error;
    }
}
