package com.example.sistemidigitali.model;

import android.content.Context;
import android.os.VibrationEffect;
import android.os.Vibrator;

public class CustomVibrator {

    private final Vibrator vibrator;

    public CustomVibrator(Context context) {
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    public void vibrateLight() {
        if(this.vibrator.hasVibrator()) {
            this.vibrator.cancel();
            this.vibrator.vibrate(VibrationEffect.createOneShot(85, 255));
        }
    }

    public void vibrateMedium() {
        if(this.vibrator.hasVibrator()) {
            this.vibrator.cancel();
            this.vibrator.vibrate(VibrationEffect.createOneShot(100, 255));
        }
    }


    public void vibrateHeavy() {
        if(this.vibrator.hasVibrator()) {
            this.vibrator.cancel();
            this.vibrator.vibrate(VibrationEffect.createOneShot(150, 150));
        }
    }
}
