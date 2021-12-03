package com.example.sistemidigitali;

import android.Manifest;
import android.os.Bundle;

import com.example.sistemidigitali.model.CameraProvider;
import com.example.sistemidigitali.model.Permission;
import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.view.View;

import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import com.google.common.util.concurrent.ListenableFuture;

import android.widget.Button;
import android.widget.ImageView;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, ImageAnalysis.Analyzer {

    private Permission permission;
    private CameraProvider cameraProvider;

    private Button picture_bt, analysis_bt;
    private ImageView imview;
    private boolean analysis_on;
    private ListenableFuture<ProcessCameraProvider> provider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        permission = new Permission(this);
        cameraProvider = new CameraProvider(this,  findViewById(R.id.previewView));
        picture_bt = findViewById(R.id.picture_bt);
        analysis_bt = findViewById(R.id.analysis_bt);
        imview = findViewById(R.id.imageView);

        picture_bt.setOnClickListener(this);
        analysis_bt.setOnClickListener((e) -> this.onClickAnalyze());
        this.analysis_on = false;
    }

    @Override
    public void analyze(@NonNull ImageProxy image) {
    }

    @Override
    public void onClick(View v) {
        //Request permissions if not already available
        if(!this.permission.checkPermission(this)) {
            this.permission.requestPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            this.permission.requestPermission(this, Manifest.permission.CAMERA);
        }

        this.cameraProvider.capturePhoto();
    }

    public void onClickAnalyze() {
        //Request permissions if not already available
        if(!this.permission.checkPermission(this)) {
            this.permission.requestPermission(this, Manifest.permission.CAMERA);
        }
        System.out.println("ANALYZE");
    }
}