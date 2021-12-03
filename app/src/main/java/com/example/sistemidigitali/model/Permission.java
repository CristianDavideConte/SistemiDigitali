package com.example.sistemidigitali.model;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class Permission extends AppCompatActivity {
    final static int PERMISSION_REQUEST_CODE = 100; //Arbitrary code for camera

    public Permission(Context context) {
        super();
    }

    public boolean checkPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED;
    }

    public void requestPermission(Activity activity, String permission) {
        ActivityCompat.requestPermissions(
                activity,
                new String[]{permission},
                PERMISSION_REQUEST_CODE);
    }
}
