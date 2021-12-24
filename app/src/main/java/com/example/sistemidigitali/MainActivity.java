package com.example.sistemidigitali;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageButton;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.sistemidigitali.model.AnalyzeActivity;
import com.example.sistemidigitali.model.CameraProvider;
import com.example.sistemidigitali.model.ImageSavedEvent;
import com.example.sistemidigitali.model.Permission;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.greenrobot.eventbus.EventBus;

public class MainActivity extends AppCompatActivity {

    public static final String ACTIVITY_IMAGE = "com.example.sistemidigitali.IMAGE";

    private Permission permission;
    private CameraProvider cameraProvider;

    private ImageButton galleryButton;
    private FloatingActionButton shutterButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.permission = new Permission(this);
        this.cameraProvider = new CameraProvider(this,  findViewById(R.id.previewView));
        this.galleryButton = findViewById(R.id.galleryButton);
        this.shutterButton = findViewById(R.id.shutterButton);

        ActivityResultLauncher<String> mGetContent = registerForActivityResult(new ActivityResultContracts.GetContent(), (uri) -> {
            this.showAnalyzeActivity(uri); //If the file picked is an image the analyze activity is launched
        });
        this.galleryButton.setOnClickListener((view) -> mGetContent.launch("image/*")); //Shows the file picker
        this.shutterButton.setOnClickListener((view) -> this.cameraProvider.captureImage());

        //Request all the permissions needed if not already available
        String [] permissions = {Manifest.permission.CAMERA,
                                 Manifest.permission.READ_EXTERNAL_STORAGE,
                                 Manifest.permission.WRITE_EXTERNAL_STORAGE};
        if(!this.permission.checkPermission(this, permissions)) {
            this.permission.requestPermission(this, permissions);
        }
    }

    private void showAnalyzeActivity(Uri picturePublicUri) {
        if(picturePublicUri == null) return;

        //Open a new activity and passes it the picture's uri
        EventBus.getDefault().postSticky(new ImageSavedEvent("success"));
        Intent intent = new Intent(this, AnalyzeActivity.class);
        intent.putExtra(MainActivity.ACTIVITY_IMAGE, picturePublicUri);
        this.startActivity(intent);
    }
}