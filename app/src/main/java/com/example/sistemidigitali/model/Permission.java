package com.example.sistemidigitali.model;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class Permission extends AppCompatActivity {

    final static int PERMISSION_REQUEST_CODE = 100; //Arbitrary code for camera

    public Permission() {
        super();
    }

    /**
     * Checks if the passed context has all the requested permissions
     * @param context The Context that all the permissions should be checked for.
     * @param permissions An array of Strings representing all the permissions.
     * @return True if the passed context has all the requested permission, false otherwise.
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
     * Requests all the given permissions for the passed activity.
     * @param activity The Activity that all the permissions should be checked for.
     * @param permissions An array of Strings representing all the permissions.
     */
    public void requestPermission(Activity activity, String[] permissions) {
        ActivityCompat.requestPermissions(
                activity,
                permissions,
                PERMISSION_REQUEST_CODE);
    }
}
