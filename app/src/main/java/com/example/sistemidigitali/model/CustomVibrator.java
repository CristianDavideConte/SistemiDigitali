package com.example.sistemidigitali.model;

import android.content.Context;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;

public class CustomVibrator {

    private final Vibrator vibrator;
    private final boolean lightVibrationIsSupported, heavyVibrationIsSupported;

    public CustomVibrator(Context context) {
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        final int[] supportedVibrations = this.vibrator.areEffectsSupported(VibrationEffect.EFFECT_CLICK, VibrationEffect.EFFECT_HEAVY_CLICK, VibrationEffect.EFFECT_DOUBLE_CLICK);
        this.lightVibrationIsSupported = this.vibrator.hasVibrator() && supportedVibrations[0] == Vibrator.VIBRATION_EFFECT_SUPPORT_YES;
        this.heavyVibrationIsSupported = this.vibrator.hasVibrator() && supportedVibrations[1] == Vibrator.VIBRATION_EFFECT_SUPPORT_YES;
    }

    public void vibrateLight() {
        if(this.lightVibrationIsSupported) {
            this.vibrator.cancel();
            this.vibrator.vibrate(VibrationEffect.createOneShot(85, 255));
        }
    }

    public void vibrateMedium() {
        if(this.lightVibrationIsSupported) {
            this.vibrator.cancel();
            this.vibrator.vibrate(VibrationEffect.createOneShot(100, 255));
        }
    }


    public void vibrateHeavy() {
        if(this.heavyVibrationIsSupported) {
            this.vibrator.cancel();
            this.vibrator.vibrate(VibrationEffect.createOneShot(150, 150));
        }
    }
}
