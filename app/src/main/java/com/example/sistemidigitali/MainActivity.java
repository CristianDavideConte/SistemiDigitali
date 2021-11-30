package com.example.sistemidigitali;

import android.os.Bundle;

import com.example.sistemidigitali.model.Permission;
import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.view.View;

import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.view.PreviewView;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.sistemidigitali.databinding.ActivityMainBinding;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageView;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, ImageAnalysis.Analyzer {

    private Permission permission;

    private Button picture_bt, analysis_bt;
    private PreviewView pview;
    private ImageView imview;
    private ImageCapture imageCapt;
    private ImageAnalysis imageAn;
    private boolean analysis_on;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        permission = new Permission();
        picture_bt = findViewById(R.id.picture_bt);
        analysis_bt = findViewById(R.id.analysis_bt);
        pview = findViewById(R.id.previewView);
        imview = findViewById(R.id.imageView);

        picture_bt.setOnClickListener(this);
        analysis_bt.setOnClickListener((e) -> this.onClick2());
        this.analysis_on = false;
    }

    @Override
    public void analyze(@NonNull ImageProxy image) {
    }

    @Override
    public void onClick(View v) {
        //TODO:
        //Check why permission is null -> nullPointerException
        if(!this.permission.checkPermission()) {
            this.permission.requestPermission();
        }
        System.out.println("PICTURE");
    }

    public void onClick2() {
        System.out.println("ANALYZE");
    }
}