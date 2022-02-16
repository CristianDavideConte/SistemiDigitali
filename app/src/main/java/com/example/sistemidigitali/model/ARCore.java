package com.example.sistemidigitali.model;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.exceptions.UnavailableException;

public class ARCore {

    private Activity context;

    public ARCore(Activity context) {
        this.context = context;
    }

    // Verify that ARCore is installed and using the current version.
    private boolean isARCoreSupportedAndUpToDate() {
        ArCoreApk.Availability availability = ArCoreApk.getInstance().checkAvailability(this.context);
        switch (availability) {
            case SUPPORTED_INSTALLED:
                return true;

            case SUPPORTED_APK_TOO_OLD:
            case SUPPORTED_NOT_INSTALLED:
                try {
                    // Request ARCore installation or update if needed.
                    ArCoreApk.InstallStatus installStatus = ArCoreApk.getInstance().requestInstall(this.context, true);
                    switch (installStatus) {
                        case INSTALL_REQUESTED:
                            Log.i("ARCORE", "ARCore installation requested.");
                            return false;
                        case INSTALLED:
                            return true;
                    }
                } catch (UnavailableException e) {
                    Log.e("ARCORE", "ARCore not installed", e);
                }
                return false;

            case UNSUPPORTED_DEVICE_NOT_CAPABLE:
                // This device is not supported for AR.
                return false;

            case UNKNOWN_CHECKING:
                // ARCore is checking the availability with a remote query.
                // This function should be called again after waiting 200 ms to determine the query result.
            case UNKNOWN_ERROR:
            case UNKNOWN_TIMED_OUT:
                // There was an error checking for AR availability. This may be due to the device being offline.
                // Handle the error appropriately.
        }
        return true;
    }
}
