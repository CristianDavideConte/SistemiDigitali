package com.example.sistemidigitali.views;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bogdwellers.pinchtozoom.ImageMatrixTouchHandler;
import com.example.sistemidigitali.R;
import com.example.sistemidigitali.customEvents.AllowUpdatePolicyChangeEvent;
import com.example.sistemidigitali.customEvents.EndOfGestureEvent;
import com.example.sistemidigitali.customEvents.ImageSavedEvent;
import com.example.sistemidigitali.customEvents.OverlayVisibilityChangeEvent;
import com.example.sistemidigitali.customEvents.UpdateDetectionsRectsEvent;
import com.example.sistemidigitali.model.CustomGestureDetector;
import com.example.sistemidigitali.model.CustomObjectDetector;
import com.google.android.material.chip.Chip;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.task.vision.detector.Detection;

import java.io.IOException;
import java.util.List;

public class AnalyzeActivity extends AppCompatActivity {
    private Bitmap originalImage;
    private TensorImage originalImageTensor;

    private View backgroundOverlayAnalyze;
    private ImageView analyzeView;
    private LiveDetectionView liveDetectionViewAnalyze;
    private Chip analyzeButton;
    private CustomObjectDetector objectDetector;
    private List<Detection> detections;

    private ImageMatrixTouchHandler zoomHandler;
    private Thread analyzerThread;

    private CustomGestureDetector customGestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analyze);

        this.backgroundOverlayAnalyze = findViewById(R.id.backgroundOverlayAnalyze);
        this.analyzeView = findViewById(R.id.analyzeView);
        this.liveDetectionViewAnalyze = findViewById(R.id.liveDetectionViewAnalyze);
        this.analyzeButton = findViewById(R.id.analyzeButton);

        this.customGestureDetector = new CustomGestureDetector();

        EventBus.getDefault().postSticky(new AllowUpdatePolicyChangeEvent(false));
    }

    /**
     * Registers this instance of AnalyzeActivity on the EventBus,
     * so that it can receive async messages from other activities.
     */
    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
        EventBus.getDefault().register(this.liveDetectionViewAnalyze);
        EventBus.getDefault().register(this.customGestureDetector);
    }

    /**
     * Unregisters this instance of AnalyzeActivity on the EventBus,
     * so that it can no longer receive async messages from other activities.
     */
    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);
        EventBus.getDefault().unregister(this.liveDetectionViewAnalyze);
        EventBus.getDefault().unregister(this.customGestureDetector);
        super.onStop();
    }

    /**
     * Function that is invoked by the EventBus (that's why it's public)
     * whenever a ImageSavedEvent is published by other activities.
     * It is used to asynchronously load an image into the
     * preview view of this AnalyzeActivity.
     * @param event An ImageSavedEvent that contains the result of the image saving operation.
     */
    @SuppressLint("ClickableViewAccessibility")
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onImageSaved(ImageSavedEvent event) {
        //If the picture is not available, go back to previous activity
        if(!event.getError().equals("success")) {
            Toast.makeText(this, event.getError(), Toast.LENGTH_SHORT).show();
            this.finish();
            return;
        }

        try {
            ImageDecoder.Source source = ImageDecoder.createSource(this.getContentResolver(), event.getUri());
            this.originalImage = ImageDecoder.decodeBitmap(source).copy(Bitmap.Config.ARGB_8888, true);
            this.originalImageTensor = TensorImage.fromBitmap(originalImage);
            this.objectDetector = new CustomObjectDetector(this);
            this.analyzeView.setImageBitmap(this.originalImage);
            this.analyzeButton.setOnCheckedChangeListener((view, isChecked) -> {
                if(isChecked) {
                    this.analyzeButton.setCheckable(false);
                    this.analyzeButton.setText(". . .");
                    this.detectObjects();
                } else {
                    this.analyzeButton.setText("Analyze");
                    EventBus.getDefault().postSticky(new AllowUpdatePolicyChangeEvent(false));
                }
            });

            this.zoomHandler = new ImageMatrixTouchHandler(this);
            this.analyzeView.setOnTouchListener((view, motionEvent) -> {
                if(!this.customGestureDetector.shouldListenToTouchEvents()) return true;

                if(this.analyzeButton.isChecked()) {
                    customGestureDetector.update(motionEvent);
                }
                return zoomHandler.onTouch(view, motionEvent);
            });
        } catch (IOException exception) {
            Toast.makeText(this, exception.getMessage(), Toast.LENGTH_SHORT).show();
            this.finish();
            return;
        }
    }


    @SuppressLint("WrongConstant")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onOverlayVisibilityChange(OverlayVisibilityChangeEvent event) {
        int visibility = event.getVisibility();
        this.backgroundOverlayAnalyze.setVisibility(visibility);
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void onEndOfGesture(EndOfGestureEvent event) {
        this.detectObjects();
    }

    private void detectObjects() {
        this.analyzerThread = new Thread(() -> {
            this.detections = this.objectDetector.detect(this.originalImageTensor);
            EventBus.getDefault().postSticky(new AllowUpdatePolicyChangeEvent(true));
            EventBus.getDefault().post(new UpdateDetectionsRectsEvent(detections, 0, 0, false, this.analyzeView.getImageMatrix()));

            if(!this.analyzeButton.isCheckable()) {
                runOnUiThread(() -> {
                        this.analyzeButton.setText("Clear");
                        this.analyzeButton.setCheckable(true);
                });
            }
        });
        this.analyzerThread.start();
    }
}