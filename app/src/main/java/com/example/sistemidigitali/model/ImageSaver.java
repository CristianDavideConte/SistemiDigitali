package com.example.sistemidigitali.model;

import static com.example.sistemidigitali.debugUtility.Debug.println;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;

import com.example.sistemidigitali.customEvents.ImageSavedEvent;

import org.greenrobot.eventbus.EventBus;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ImageSaver {
    private Context context;

    public ImageSaver(Context context) {
        this.context = context;
    }


    @SuppressLint("SimpleDateFormat")
    public void saveImage(Bitmap image) {
        if(image == null) {
            EventBus.getDefault().post(new ImageSavedEvent("Bitmap is null", null));
            return;
        }

        //Es. SISDIG_2021127_189230.jpg
        final String pictureName = "SISDIG_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".jpeg";

        //Sources:
        //https://stackoverflow.com/questions/56904485/how-to-save-an-image-in-android-q-using-mediastore
        //https://developer.android.com/reference/android/content/ContentResolver#insert(android.net.Uri,%20android.content.ContentValues)
        //https://developer.android.com/training/data-storage/use-cases#share-media-all
        //https://developer.android.com/reference/androidx/camera/core/ImageCapture.OnImageCapturedCallback

        //Create the picture's metadata
        ContentValues newPictureDetails = new ContentValues();
        newPictureDetails.put(MediaStore.Images.Media._ID, pictureName);
        newPictureDetails.put(MediaStore.Images.Media.DISPLAY_NAME, pictureName);
        newPictureDetails.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        newPictureDetails.put(MediaStore.Images.Media.WIDTH, image.getWidth());
        newPictureDetails.put(MediaStore.Images.Media.HEIGHT, image.getHeight());
        newPictureDetails.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/SistemiDigitaliM");

        //Add picture to MediaStore in order to make it accessible to other apps
        //The result of the insert is the handle to the picture inside the MediaStore
        Uri picturePublicUri = this.context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, newPictureDetails);

        //Saves the image and post the result on the EventBus
        try {
            OutputStream stream = this.context.getContentResolver().openOutputStream(picturePublicUri);
            boolean imageSavedCorrectly = image.compress(Bitmap.CompressFormat.JPEG, 95, stream);
            stream.close();

            if (!imageSavedCorrectly) throw new Exception("Image compression failed");

            EventBus.getDefault().post(new ImageSavedEvent("success", picturePublicUri));
        } catch (Exception e) {
            e.printStackTrace();
            //Remove the allocated space in the MediaStore if the picture can't be saved
            this.context.getContentResolver().delete(picturePublicUri, new Bundle());

            EventBus.getDefault().post(new ImageSavedEvent(e.getMessage(), picturePublicUri));
        }
    }

}
