package com.example.sistemidigitali.views;

import static com.example.sistemidigitali.debugUtility.Debug.println;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Matrix;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.OrientationEventListener;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;

import com.example.sistemidigitali.R;
import com.example.sistemidigitali.customEvents.AllowUpdatePolicyChangeEvent;
import com.example.sistemidigitali.customEvents.ImageSavedEvent;
import com.example.sistemidigitali.customEvents.OverlayVisibilityChangeEvent;
import com.example.sistemidigitali.customEvents.UpdateDetectionsRectsEvent;
import com.example.sistemidigitali.model.CameraProvider;
import com.example.sistemidigitali.model.CustomGestureDetector;
import com.example.sistemidigitali.model.Permission;
import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
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
    private CustomGestureDetector customGestureDetector;
    private int currentOrientation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.permission = new Permission();
        this.customGestureDetector = new CustomGestureDetector();
        this.backgroundOverlayMain = findViewById(R.id.backgroundOverlayMain);
        this.liveDetectionViewMain = findViewById(R.id.liveDetectionViewMain);
        this.liveDetectionSwitch = findViewById(R.id.liveDetectionSwitch);
        this.hideUIButton = findViewById(R.id.hideUIButton);
        this.switchCameraButton = findViewById(R.id.switchCameraButton);
        this.galleryButton = findViewById(R.id.galleryButton);
        this.shutterButton = findViewById(R.id.shutterButton);

        try {
            MainActivity context = this;
            this.cameraProvider = new CameraProvider(context,  findViewById(R.id.previewView), customGestureDetector);
            if(!this.cameraProvider.isObjectDetectorInitialized()) throw new IOException();
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

            int errorColor = Color.rgb(240,72,84);
            int[] colors = new int[] { errorColor, errorColor, errorColor, errorColor };
            this.liveDetectionSwitch.setChipBackgroundColor(new ColorStateList(states, colors));
            this.liveDetectionSwitch.setOnClickListener((view) -> Toast.makeText(this, "Unavailable", Toast.LENGTH_SHORT).show());
        }

        //If the file picked is an image the analyze activity is launched
        ActivityResultLauncher<String> mGetContent = registerForActivityResult(new ActivityResultContracts.GetContent(), this::showAnalyzeActivity);

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

        //Debug only
        //this.liveDetectionSwitch.performClick();
    }

    /**
     * Registers this instance of MainActivity on the EventBus,
     * so that it can receive async messages from other activities.
     */
    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
        EventBus.getDefault().register(this.liveDetectionViewMain);
        EventBus.getDefault().register(this.customGestureDetector);
        EventBus.getDefault().post(new UpdateDetectionsRectsEvent(new ArrayList<>(), false, new Matrix()));
        EventBus.getDefault().post(new AllowUpdatePolicyChangeEvent(true));
        this.liveDetectionSwitch.setChecked(false);
    }

    /**
     * Unregisters this instance of MainActivity on the EventBus,
     * so that it can no longer receive async messages from other activities.
     */
    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);
        EventBus.getDefault().unregister(this.liveDetectionViewMain);
        EventBus.getDefault().unregister(this.customGestureDetector);
        super.onStop();
    }

    @SuppressLint("WrongConstant")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onOverlayVisibilityChange(OverlayVisibilityChangeEvent event) {
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
        EventBus.getDefault().postSticky(new ImageSavedEvent("success", picturePublicUri));
        Intent intent = new Intent(this, AnalyzeActivity.class);
        this.startActivity(intent);
    }

    private void changeUIVisibility() {
        this.isUIVisible = this.isUIVisible == View.VISIBLE ? View.GONE : View.VISIBLE;
        this.liveDetectionSwitch.setVisibility(this.isUIVisible);
        this.switchCameraButton.setVisibility(this.isUIVisible);
        this.shutterButton.setVisibility(this.isUIVisible);
        this.galleryButton.setVisibility(this.isUIVisible);
    }
}