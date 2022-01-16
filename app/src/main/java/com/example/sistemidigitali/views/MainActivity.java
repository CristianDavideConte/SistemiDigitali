package com.example.sistemidigitali.views;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.sistemidigitali.R;
import com.example.sistemidigitali.customEvents.ImageSavedEvent;
import com.example.sistemidigitali.customEvents.OverlayVisibilityChangeEvent;
import com.example.sistemidigitali.model.CameraProvider;
import com.example.sistemidigitali.model.CustomObjectDetector;
import com.example.sistemidigitali.model.Permission;
import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.tensorflow.lite.task.vision.detector.Detection;

import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static final String ACTIVITY_IMAGE = "com.example.sistemidigitali.IMAGE";

    private Permission permission;
    private CameraProvider cameraProvider;

    private View backgroundOverlayMain;
    private LiveDetectionView liveDetectionViewMain;

    private Chip liveDetectionSwitch;
    private FloatingActionButton hideUIButton;

    private FloatingActionButton switchCameraButton;
    private FloatingActionButton galleryButton;
    private FloatingActionButton shutterButton;

    private int isUIVisible;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.permission = new Permission();
        this.cameraProvider = new CameraProvider(this,  findViewById(R.id.previewView));
        this.backgroundOverlayMain = findViewById(R.id.backgroundOverlayMain);
        this.liveDetectionViewMain = findViewById(R.id.liveDetectionViewMain);
        this.liveDetectionSwitch = findViewById(R.id.liveDetectionSwitch);
        this.hideUIButton = findViewById(R.id.hideUIButton);
        this.switchCameraButton = findViewById(R.id.switchCameraButton);
        this.galleryButton = findViewById(R.id.galleryButton);
        this.shutterButton = findViewById(R.id.shutterButton);

        try {
            this.cameraProvider.setObjectDetector(new CustomObjectDetector(this));
            this.liveDetectionSwitch.setOnCheckedChangeListener((view, isChecked) -> this.cameraProvider.setLiveDetection(isChecked));
        } catch (IOException e) {
            this.liveDetectionSwitch.setCheckable(false);
            this.liveDetectionSwitch.setTextColor(Color.WHITE);
            int[][] states = new int[][] {
                    new int[] { android.R.attr.state_enabled}, // enabled
                    new int[] {-android.R.attr.state_enabled}, // disabled
                    new int[] {-android.R.attr.state_checked}, // unchecked
                    new int[] { android.R.attr.state_pressed}  // pressed
            };

            int[] colors = new int[] { Color.RED, Color.RED, Color.RED, Color.RED };
            this.liveDetectionSwitch.setChipBackgroundColor(new ColorStateList(states, colors));
        }

        ActivityResultLauncher<String> mGetContent = registerForActivityResult(new ActivityResultContracts.GetContent(), (uri) -> {
            this.showAnalyzeActivity(uri); //If the file picked is an image the analyze activity is launched
        });

        this.hideUIButton.setOnClickListener((view) -> this.changeUIVisibility());
        this.switchCameraButton.setOnClickListener((view) -> this.cameraProvider.switchCamera());
        this.galleryButton.setOnClickListener((view) -> mGetContent.launch("image/*")); //Shows the file picker for images only
        this.shutterButton.setOnClickListener((view) -> this.cameraProvider.captureImage());

        //Request all the permissions needed if not already available
        String [] permissions = {Manifest.permission.CAMERA,
                                 Manifest.permission.READ_EXTERNAL_STORAGE,
                                 Manifest.permission.WRITE_EXTERNAL_STORAGE};
        if(!this.permission.checkPermission(this, permissions)) {
            this.permission.requestPermission(this, permissions);
        }
    }
    /**
     * Registers this instance of MainActivity on the EventBus,
     * so that it can receive async messages from other activities.
     */
    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    /**
     * Unregisters this instance of MainActivity on the EventBus,
     * so that it can no longer receive async messages from other activities.
     */
    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    @SuppressLint("WrongConstant")
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void OverlayVisibilityChange(OverlayVisibilityChangeEvent event) {
        this.backgroundOverlayMain.setVisibility(event.getVisibility());
    }

    /**
     * Starts a new instance of AnalyzeActivity and passes it
     * the given picture's uri if valid.
     * @param picturePublicUri A picture's Uri.
     */
    private void showAnalyzeActivity(Uri picturePublicUri) {
        if(picturePublicUri == null) return;

        //Open a new activity and passes it the picture's uri
        EventBus.getDefault().postSticky(new ImageSavedEvent("success"));
        Intent intent = new Intent(this, AnalyzeActivity.class);
        intent.putExtra(MainActivity.ACTIVITY_IMAGE, picturePublicUri);
        this.startActivity(intent);
    }

    private void changeUIVisibility() {
        this.isUIVisible = this.isUIVisible == View.VISIBLE ? View.GONE : View.VISIBLE;
        this.liveDetectionSwitch.setVisibility(this.isUIVisible);
        this.switchCameraButton.setVisibility(this.isUIVisible);
        this.shutterButton.setVisibility(this.isUIVisible);
        this.galleryButton.setVisibility(this.isUIVisible);
    }

    public void drawDetectionRects(List<Detection> detections, float rectsWidth, float rectsHeight, boolean flipNeeded) {
        this.liveDetectionViewMain.setDetections(detections, rectsWidth, rectsHeight, flipNeeded);
        this.liveDetectionViewMain.invalidate();
    }

    public void showPopDetails(View view, MotionEvent motionEvent) {
        this.liveDetectionViewMain.showPopDetails(view, motionEvent);
    }
}