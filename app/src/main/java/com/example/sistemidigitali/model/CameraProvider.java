package com.example.sistemidigitali.model;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.concurrent.Executor;

public class CameraProvider extends AppCompatActivity implements ImageAnalysis.Analyzer {

    private ListenableFuture<ProcessCameraProvider> provider;
    private PreviewView pview;
    private ImageCapture imageCapt;
    private ImageAnalysis imageAn;

    private Context context;
    private ContentProvider contentProvider;

    @Override
    public void analyze(@NonNull ImageProxy image) {

    }

    public CameraProvider(Context context, PreviewView pview) {
        this.context = context;
        this.pview = pview;
        provider = ProcessCameraProvider.getInstance(this.context);
        provider.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = provider.get();
                startCamera(cameraProvider);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, this.getExecutor());
    }

    private Executor getExecutor() {
        return ContextCompat.getMainExecutor(this.context);
    }

    public void startCamera(ProcessCameraProvider cameraProvider) {
        cameraProvider.unbindAll(); //Clear usecases
        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build(); //backward facing camera

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(pview.getSurfaceProvider());

        imageCapt = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build();
        imageAn = new ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
        imageAn.setAnalyzer(this.getExecutor(), this);

        cameraProvider.bindToLifecycle((LifecycleOwner) this.context, cameraSelector, preview, imageCapt, imageAn);
    }

    public void capturePhoto() {
        //Save in: \Android\data\com.example.sistemidigitali\files + path inside the brackets
        //See https://developer.android.com/about/versions/11/privacy/storage
        File photoDir = this.context.getExternalFilesDir("/CameraXPhotos");
        //File photoDir = this.context.getExternalFilesDir(Environment.DIRECTORY_PICTURES + "/CameraXPhotos");
        //File photoDir = this.context.getExternalFilesDir(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.getPath() + "/CameraXPhotos");

        if(!photoDir.exists()) {
            System.out.println("RESET DIR");
            photoDir.mkdir();
        }

        //Es. SISDIG_2021127_189230.jpg
        GregorianCalendar calendar = new GregorianCalendar();
        String photoName = "SISDIG_" +
                            calendar.get(Calendar.YEAR) +
                            calendar.get(Calendar.WEEK_OF_MONTH) +
                            calendar.get(Calendar.DAY_OF_MONTH) + "_" +
                            calendar.getTimeInMillis() + ".jpg";
        File photoFile = new File(photoDir.getAbsolutePath() + "/" + photoName);

        this.imageCapt.takePicture(
                new ImageCapture.OutputFileOptions.Builder(photoFile).build(),
                this.getExecutor(),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        //Create the picture's metadata
                        ContentValues newPictureDetails = new ContentValues();
                        newPictureDetails.put(MediaStore.Images.Media.DISPLAY_NAME, photoName);
                        newPictureDetails.put(MediaStore.Images.Media.MIME_TYPE, "image/jpg");
                        newPictureDetails.put(MediaStore.Images.Media.WIDTH, imageCapt.getResolutionInfo().getResolution().getWidth());
                        newPictureDetails.put(MediaStore.Images.Media.HEIGHT, imageCapt.getResolutionInfo().getResolution().getHeight());

                        //Add picture to MediaStore in order to make it accessible to other apps
                        //The result of the insert is the handle to the picture inside the MediaStore
                        //https://developer.android.com/reference/android/content/ContentResolver#insert(android.net.Uri,%20android.content.ContentValues)
                        //https://developer.android.com/training/data-storage/use-cases#share-media-all
                        context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, newPictureDetails);

                        Toast.makeText(context, "Picture taken", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        exception.printStackTrace();
                        Toast.makeText(context, "Error saving photo" + exception.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }
}
