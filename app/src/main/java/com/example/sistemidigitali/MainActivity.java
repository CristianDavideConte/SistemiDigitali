package com.example.sistemidigitali;

import android.Manifest;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.sistemidigitali.model.CameraProvider;
import com.example.sistemidigitali.model.Permission;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MainActivity extends AppCompatActivity {
    public static final String ACTIVITY_IMAGE = "com.example.sistemidigitali.IMAGE";

    private Permission permission;
    private CameraProvider cameraProvider;

    private FloatingActionButton shutterButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.permission = new Permission(this);
        this.cameraProvider = new CameraProvider(this,  findViewById(R.id.previewView));
        this.shutterButton = findViewById(R.id.shutterButton);
        this.shutterButton.setOnClickListener((view) -> this.cameraProvider.captureImage());

        //Request all the permissions needed if not already available
        String [] permissions = {Manifest.permission.CAMERA,
                                 Manifest.permission.READ_EXTERNAL_STORAGE,
                                 Manifest.permission.WRITE_EXTERNAL_STORAGE};
        if(!this.permission.checkPermission(this, permissions)) {
            this.permission.requestPermission(this, permissions);
        }
    }
}