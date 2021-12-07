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

    /**
     * Checks if the passed context has all the requested permissions
     * @param context a Context object
     * @param permissions an array of Strings representing all the permissions
     * @return true if the passed context has all the requested permission
     */
    public boolean checkPermission(Context context, String[] permissions) {
        for(String permission : permissions) {
            if(ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Requests all the requested permissions for the passed activity
     * @param activity
     * @param permissions
     */
    public void requestPermission(Activity activity, String[] permissions) {
        ActivityCompat.requestPermissions(
                activity,
                permissions,
                PERMISSION_REQUEST_CODE);
    }
}
