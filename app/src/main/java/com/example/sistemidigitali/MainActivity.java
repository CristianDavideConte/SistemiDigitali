package com.example.sistemidigitali;

import android.Manifest;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.sistemidigitali.model.CameraProvider;
import com.example.sistemidigitali.model.Permission;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    public static final String ACTIVITY_MESSAGE = "com.example.sistemidigitali.MESSAGE";
    public static final String ACTIVITY_IMAGE = "com.example.sistemidigitali.IMAGE";

    private Permission permission;
    private CameraProvider cameraProvider;

    private Button picture_bt, analysis_bt;
    private ImageView imview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imview = findViewById(R.id.imageView);
        imview.setWillNotDraw(false);
        permission = new Permission(this);
        cameraProvider = new CameraProvider(this,  findViewById(R.id.previewView), imview);
        picture_bt = findViewById(R.id.picture_bt);
        analysis_bt = findViewById(R.id.analysis_bt);

        picture_bt.setOnClickListener(this);
        analysis_bt.setOnClickListener((e) -> this.onClickAnalyze());

        //Request all the permissions needed if not already available
        String [] permissions = {Manifest.permission.CAMERA,
                                 Manifest.permission.READ_EXTERNAL_STORAGE,
                                 Manifest.permission.WRITE_EXTERNAL_STORAGE};
        if(!this.permission.checkPermission(this, permissions)) {
            this.permission.requestPermission(this, permissions);
        }
    }

    @Override
    public void onClick(View v) {
        this.cameraProvider.capturePhoto();
    }

    public void onClickAnalyze() {
        findViewById(R.id.analyzeLayout).setVisibility(View.GONE);
        System.out.println("ANALYZE");
    }
}