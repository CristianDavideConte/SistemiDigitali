package com.example.sistemidigitali.views;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.sistemidigitali.R;
import com.example.sistemidigitali.customEvents.CustomObjectDetectorAvailableEvent;
import com.example.sistemidigitali.customEvents.OverlayVisibilityChangeEvent;
import com.example.sistemidigitali.customEvents.PictureTakenEvent;
import com.example.sistemidigitali.model.CustomGestureDetector;
import com.example.sistemidigitali.model.Permission;
import com.example.sistemidigitali.model.ToastMessagesManager;
import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.opencv.android.OpenCVLoader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private final String [] permissions = {Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    private Permission permission;
    private CameraProviderView cameraProviderView;

    private View backgroundOverlayMain;
    private LiveDetectionView liveDetectionViewMain;

    private Chip liveDetectionSwitch;
    private FloatingActionButton hideUIButton;

    private FloatingActionButton switchCameraButton;
    private FloatingActionButton galleryButton;
    private FloatingActionButton shutterButton;

    private int isUIVisible;

    private ToastMessagesManager toastMessagesManager;
    private CustomGestureDetector customGestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        if (OpenCVLoader.initDebug()) {
            Log.d("myTag", "OpenCV loaded");
        }

        this.permission = new Permission();
        this.customGestureDetector = new CustomGestureDetector();
        this.toastMessagesManager = new ToastMessagesManager(this, Toast.LENGTH_SHORT);
        this.backgroundOverlayMain = findViewById(R.id.backgroundOverlayMain);
        this.liveDetectionViewMain = findViewById(R.id.liveDetectionViewMain);
        this.liveDetectionSwitch = findViewById(R.id.liveDetectionSwitch);
        this.hideUIButton = findViewById(R.id.hideUIButton);
        this.switchCameraButton = findViewById(R.id.switchCameraButton);
        this.galleryButton = findViewById(R.id.galleryButton);
        this.shutterButton = findViewById(R.id.shutterButton);

        this.cameraProviderView = new CameraProviderView(this,  findViewById(R.id.previewView), customGestureDetector);
        this.switchCameraButton.setOnClickListener((view) -> this.cameraProviderView.switchCamera());
        this.liveDetectionSwitch.setOnClickListener((view) -> this.toastMessagesManager.showToastIfNeeded());
        this.shutterButton.setOnClickListener((view) -> this.cameraProviderView.captureImages(2));


        //If the files picked are two images the analyze activity is launched
        ActivityResultLauncher<String> filePicker = registerForActivityResult(new ActivityResultContracts.GetMultipleContents(), this::showAnalyzeActivity);
        this.galleryButton.setOnClickListener((view) -> filePicker.launch("image/*")); //Shows the file picker for images only
        this.hideUIButton.setOnClickListener((view) -> this.changeUIVisibility());


        //Request all the permissions needed if not already available
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
        EventBus.getDefault().register(this.liveDetectionViewMain);
        EventBus.getDefault().register(this.customGestureDetector);
        EventBus.getDefault().removeStickyEvent(PictureTakenEvent.class);
        this.cameraProviderView.setLiveDetection(this.liveDetectionSwitch.isChecked());
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

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onCustomObjectDetectorAvailable(CustomObjectDetectorAvailableEvent event) {
        if(event.getContext() != this) return;
        this.liveDetectionSwitch.setOnClickListener((view) -> {});
        this.liveDetectionSwitch.setOnCheckedChangeListener((view, isChecked) -> this.cameraProviderView.setLiveDetection(isChecked));
        this.liveDetectionSwitch.setCheckable(true);
        this.toastMessagesManager.hideToast();
        EventBus.getDefault().removeStickyEvent(event);
    }

    @SuppressLint("WrongConstant")
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onOverlayVisibilityChange(OverlayVisibilityChangeEvent event) {
        this.backgroundOverlayMain.setVisibility(event.getVisibility());
    }

    /**
     * Starts a new instance of AnalyzeActivity and passes it
     * the given picture's uri if valid.
     * @param picturePublicUri A picture's Uri.
     */
    private void showAnalyzeActivity(List<Uri> picturePublicUri) {
        if(picturePublicUri.size() == 0) return;
        if(picturePublicUri.size() != 2) {
            this.toastMessagesManager.showToast("Select 2 images");
            return;
        }
        try {
            List<Bitmap> frames = new ArrayList<>();
            for(Uri uri : picturePublicUri) {
                Bitmap frame = ImageDecoder.decodeBitmap(ImageDecoder.createSource(this.getContentResolver(), uri)).copy(Bitmap.Config.ARGB_8888, false);
                frames.add(frame);
            }
            EventBus.getDefault().postSticky(new PictureTakenEvent(frames, "success"));
            Intent intent = new Intent(this, AnalyzeActivity.class);
            this.startActivity(intent);
        } catch (IOException e) {
            e.printStackTrace();
            this.toastMessagesManager.showToast("Invalid Images");
        }
    }

    private void changeUIVisibility() {
        this.isUIVisible = this.isUIVisible == View.VISIBLE ? View.GONE : View.VISIBLE;
        this.liveDetectionSwitch.setVisibility(this.isUIVisible);
        this.switchCameraButton.setVisibility(this.isUIVisible);
        this.shutterButton.setVisibility(this.isUIVisible);
        this.galleryButton.setVisibility(this.isUIVisible);
    }
}