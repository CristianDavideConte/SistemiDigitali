package com.example.sistemidigitali.model;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.annotation.NonNull;

import com.example.sistemidigitali.customEvents.ImageSavedEvent;

import org.greenrobot.eventbus.EventBus;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ImageUtility {
    private final Context context;

    public ImageUtility(Context context) {
        this.context = context;
    }

    /**
     * Converts the passed float[] to a Bitmap image.
     * Source:
     * https://github.com/isl-org/MiDaS/blob/b7fbf07a5d687653ec053757152f8f87efe49b4d/mobile/android/app/src/main/java/org/tensorflow/lite/examples/classification/CameraActivity.java#L532
     * @param imgArray a float array
     * @param width the width of the final bitmap
     * @param height the height of the final bitmap
     * @return The corresponding Bitmap image
     */
    public Bitmap convertFloatArrayToBitmap(float[] imgArray, int width, int height) {
        float maxVal = Float.NEGATIVE_INFINITY;
        float minVal = Float.POSITIVE_INFINITY;
        for (float cur : imgArray) {
            maxVal = Math.max(maxVal, cur);
            minVal = Math.min(minVal, cur);
        }
        float multiplier = 0;
        if ((maxVal - minVal) > 0) multiplier = 255 / (maxVal - Math.abs(minVal));

        int[] imgNormalized = new int[imgArray.length];
        for (int i = 0; i < imgArray.length; i++) {
            imgNormalized[i] = (int)(multiplier * (imgArray[i] - minVal)); //always between 0..255
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);

        for (int ii = 0; ii < width; ii++) {
            for (int jj = 0; jj < height; jj++) {
                final int index = ii + jj * width;
                if(index < imgArray.length) {
                    final int val = imgNormalized[index];
                    bitmap.setPixel(ii, jj, Color.rgb(val, val, val));
                }
            }
        }

        return bitmap;
    }

    /**
     * Converts the passed Image to a Bitmap image.
     * Source: https://stackoverflow.com/questions/56772967/converting-imageproxy-to-bitmap
     * @param image An Image (not null)
     * @param flipNeeded True if image needs to be mirrored on the y-axis, false otherwise.
     * @return The corresponding Bitmap image
     */
    public Bitmap convertImageToBitmap(@NonNull Image image, int rotationDegree, boolean flipNeeded) {
        ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
        byteBuffer.rewind();
        byte[] bytes = new byte[byteBuffer.capacity()];
        byteBuffer.get(bytes);

        BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        //decodeOptions.inSampleSize = 2; //Scale down the original image by this factor in both dimension
        decodeOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;

        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, decodeOptions);
        Matrix matrix = new Matrix();
        matrix.postRotate(rotationDegree);
        if(flipNeeded) matrix.preScale(1.0f, -1.0f); //flip the image on the y-axis

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, flipNeeded);
    }

    /**
     * Converts the passed Bitmap to a ByteBuffer.
     * @param bitmap A Bitmap
     * @return The corresponding ByteBuffer
     */
    public ByteBuffer convertBitmapToBytebuffer(Bitmap bitmap, int targetWidth, int targetHeight) {
        float[] mean = new float[]{123.675f,  116.28f, 103.53f};
        float[] std = new float[]{58.395f, 57.12f, 57.375f};

        //https://github.com/shubham0204/Realtime_MiDaS_Depth_Estimation_Android/blob/65cd321b029fafee3d5b9ae4783fabd512951719/app/src/main/java/com/shubham0204/ml/depthestimation/MiDASModel.kt#L52
        ImageProcessor imageProcessor =
                new ImageProcessor.Builder()
                        .add(new ResizeOp(targetHeight, targetWidth, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR))
                        .add(new NormalizeOp(mean, std))
                        .build();

        // Create a TensorImage object. This creates the tensor of the corresponding
        // tensor type (float32 in this case) that the TensorFlow Lite interpreter needs.
        TensorImage tensorImage = new TensorImage(DataType.FLOAT32);

        // Analysis code for every frame
        // Preprocess the image
        tensorImage.load(bitmap);
        tensorImage = imageProcessor.process(tensorImage);

        return tensorImage.getBuffer();
    }

    public void saveImages(List<Bitmap> images) {
        if(images.size() < 1) return;

        List<ImageSavedEvent> savingResults = new ArrayList<>();
        for(Bitmap image : images) {
            savingResults.add(this.saveImage(image));
        }

        for(ImageSavedEvent savingResult : savingResults) {
            if(!savingResult.getError().equals("success")) {
                EventBus.getDefault().post(savingResult);
                return;
            }
        }
        EventBus.getDefault().post(savingResults.get(0));
    }


    @SuppressLint("SimpleDateFormat")
    private ImageSavedEvent saveImage(Bitmap image) {
        if(image == null) return new ImageSavedEvent("Bitmap is null", null);

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

            return new ImageSavedEvent("success", picturePublicUri);
        } catch (Exception e) {
            e.printStackTrace();
            //Remove the allocated space in the MediaStore if the picture can't be saved
            this.context.getContentResolver().delete(picturePublicUri, new Bundle());

            return new ImageSavedEvent(e.getMessage(), picturePublicUri);
        }
    }

}
