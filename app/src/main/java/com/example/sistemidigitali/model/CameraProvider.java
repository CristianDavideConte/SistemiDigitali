package com.example.sistemidigitali.model;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.example.sistemidigitali.MainActivity;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Executor;

public class CameraProvider {

    private ListenableFuture<ProcessCameraProvider> provider;
    private PreviewView pview;
    private ImageCapture imageCapt;

    private Activity context;

    public CameraProvider(Activity context, PreviewView pview) {
        this.context = context;
        this.pview = pview;
        provider = ProcessCameraProvider.getInstance(this.context);
        provider.addListener(() -> {
            try {
                this.startCamera(provider.get());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, this.getExecutor());
    }

    public void startCamera(ProcessCameraProvider cameraProvider) {
        cameraProvider.unbindAll(); //Clear usecases
        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build(); //backward facing camera

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(this.pview.getSurfaceProvider());

        this.imageCapt = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build();

        cameraProvider.bindToLifecycle((LifecycleOwner) this.context, cameraSelector, preview, this.imageCapt);
    }

    public void captureImage() {
        //Es. SISDIG_2021127_189230.jpg
        String pictureName = "SISDIG_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".jpeg";

        this.imageCapt.takePicture(
                this.getExecutor(),
                new ImageCapture.OnImageCapturedCallback(){
                    @Override
                    public void onCaptureSuccess(ImageProxy image) {
                        //Sources:
                        //https://stackoverflow.com/questions/56904485/how-to-save-an-image-in-android-q-using-mediastore
                        //https://developer.android.com/reference/android/content/ContentResolver#insert(android.net.Uri,%20android.content.ContentValues)
                        //https://developer.android.com/training/data-storage/use-cases#share-media-all
                        //https://developer.android.com/reference/androidx/camera/core/ImageCapture.OnImageCapturedCallback

                        //Create the picture's metadata
                        ContentValues newPictureDetails = new ContentValues();
                        newPictureDetails.put(MediaStore.Images.Media._ID, pictureName);
                        newPictureDetails.put(MediaStore.Images.Media.ORIENTATION, String.valueOf(-image.getImageInfo().getRotationDegrees()));
                        newPictureDetails.put(MediaStore.Images.Media.DISPLAY_NAME, pictureName);
                        newPictureDetails.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                        newPictureDetails.put(MediaStore.Images.Media.WIDTH, image.getWidth());
                        newPictureDetails.put(MediaStore.Images.Media.HEIGHT, image.getHeight());
                        newPictureDetails.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/SistemiDigitaliM");
                        OutputStream stream = null;

                        try {
                            //Add picture to MediaStore in order to make it accessible to other apps
                            //The result of the insert is the handle to the picture inside the MediaStore
                            Uri picturePublicUri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, newPictureDetails);

                            stream = context.getContentResolver().openOutputStream(picturePublicUri);
                            Bitmap bitmapImage = convertImageProxyToBitmap(image);
                            if(!bitmapImage.compress(Bitmap.CompressFormat.JPEG, 100, stream)) {
                                throw new Exception("Can't save image on disk");
                            }

                            stream.close();
                            image.close();

                            //Open a new activity and pass it the picture's uri
                            Intent intent = new Intent(context, AnalyzeActivity.class);
                            intent.putExtra(MainActivity.ACTIVITY_IMAGE, picturePublicUri);
                            context.startActivity(intent);
                        } catch (Exception exception) {
                            exception.printStackTrace();
                            Toast.makeText(context, "Error saving photo: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        exception.printStackTrace();
                        Toast.makeText(context, "Error saving picture", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }


    private Executor getExecutor() {
        return ContextCompat.getMainExecutor(this.context);
    }

    /**
     * Converts the passed ImageProxy to a Bitmap image.
     * Source: https://stackoverflow.com/questions/56772967/converting-imageproxy-to-bitmap
     * @param image an ImageProxy
     * @return the corresponding Bitmap image
     */
    private Bitmap convertImageProxyToBitmap(ImageProxy image) {
        ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
        byteBuffer.rewind();
        byte[] bytes = new byte[byteBuffer.capacity()];
        byteBuffer.get(bytes);

        BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;

        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, decodeOptions);
        Matrix matrix = new Matrix();
        matrix.postRotate(image.getImageInfo().getRotationDegrees());
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, image.getWidth(), image.getHeight(), matrix, false);

        return bitmap;
    }
}
