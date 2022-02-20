package com.example.sistemidigitali.model;

import android.app.Activity;
import android.content.Context;
import android.media.Image;
import android.util.Log;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.exceptions.UnavailableException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ARCore {

    private Activity context;

    public ARCore(Activity context) {
        this.context = context;
    }

    //Verify that ARCore is installed and using the current version
    private boolean isARCoreSupportedAndUpToDate() {
        ArCoreApk.Availability availability = ArCoreApk.getInstance().checkAvailability(this.context);
        switch (availability) {
            case SUPPORTED_INSTALLED:
                return true;
            case SUPPORTED_APK_TOO_OLD:
                return false;
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
        }
        return true;
    }

    //Obtain the depth in millimeters for depthImage at coordinates (x, y)
    public int getMillimetersDepth(Image depthImage, int x, int y) {
        // The depth image has a single plane, which stores depth for each
        // pixel as 16-bit unsigned integers.
        Image.Plane plane = depthImage.getPlanes()[0];
        int byteIndex = x * plane.getPixelStride() + y * plane.getRowStride();
        ByteBuffer buffer = plane.getBuffer().order(ByteOrder.nativeOrder());
        return buffer.getShort(byteIndex);
    }
}
