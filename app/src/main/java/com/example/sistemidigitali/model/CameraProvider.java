package com.example.sistemidigitali.model;

import android.content.Context;
import android.os.Environment;
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

import com.example.sistemidigitali.MainActivity;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.Date;
import java.util.concurrent.Executor;

public class CameraProvider extends AppCompatActivity implements ImageAnalysis.Analyzer {

    private ListenableFuture<ProcessCameraProvider> provider;
    private PreviewView pview;
    private ImageCapture imageCapt;
    private ImageAnalysis imageAn;

    private Context context;

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
        File photoDir = this.context.getExternalFilesDir("/CameraXPhotos");
        //File photoDir = this.context.getExternalFilesDir(Environment.DIRECTORY_PICTURES + "/CameraXPhotos");
        //File photoDir = this.context.getExternalFilesDir(Environment.DIRECTORY_DCIM + "/CameraXPhotos");
        //File photoDir = this.context.getExternalFilesDir(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.getPath() + "/CameraXPhotos");

        if(!photoDir.exists()) {
            photoDir.mkdir();
        }

        Date date = new Date();
        String timestamp = String.valueOf(date.getTime());
        String photoFilePath = photoDir.getAbsolutePath() + "/" + timestamp + ".jpg";
        File photoFile = new File(photoFilePath);

        this.imageCapt.takePicture(
                new ImageCapture.OutputFileOptions.Builder(photoFile).build(),
                this.getExecutor(),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
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
