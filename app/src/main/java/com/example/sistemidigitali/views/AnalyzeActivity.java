package com.example.sistemidigitali.views;

import static com.example.sistemidigitali.debugUtility.Debug.println;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bogdwellers.pinchtozoom.ImageMatrixTouchHandler;
import com.example.sistemidigitali.customEvents.AllowUpdatePolicyChangeEvent;
import com.example.sistemidigitali.customEvents.UpdateDetectionsRectsEvent;
import com.example.sistemidigitali.model.CustomObjectDetector;
import com.example.sistemidigitali.R;
import com.google.android.material.chip.Chip;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.task.vision.detector.Detection;

import java.io.IOException;
import java.util.List;

import com.example.sistemidigitali.customEvents.ImageSavedEvent;

public class AnalyzeActivity extends AppCompatActivity {
    private float MAX_FONT_SIZE = 70F;

    private Bitmap originalImage;
    private TensorImage originalImageTensor;

    private ImageView analyzeView;
    private LiveDetectionView liveDetectionViewAnalyze;
    private Chip analyzeButton;
    private CustomObjectDetector objectDetector;
    private List<Detection> detections;

    private ImageMatrixTouchHandler zoomHandler;
    private Thread analyzerThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analyze);

        this.analyzeView = findViewById(R.id.analyzeView);
        this.liveDetectionViewAnalyze = findViewById(R.id.liveDetectionViewAnalyze);
        this.analyzeButton = findViewById(R.id.analyzeButton);

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
    }

    /**
     * Unregisters this instance of AnalyzeActivity on the EventBus,
     * so that it can no longer receive async messages from other activities.
     */
    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);
        EventBus.getDefault().unregister(this.liveDetectionViewAnalyze);
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
                if(this.analyzeButton.isChecked()) {
                    this.detectObjects();
                }
                return zoomHandler.onTouch(view, motionEvent);
            });
        } catch (IOException exception) {
            Toast.makeText(this, exception.getMessage(), Toast.LENGTH_SHORT).show();
            this.finish();
            return;
        }
    }

    private void detectObjects() {
        if(this.analyzerThread != null) return;
        this.analyzerThread = new Thread(() -> {
            while(zoomHandler.isAnimating()) continue;
            this.detections = this.objectDetector.detect(this.originalImageTensor);
            EventBus.getDefault().postSticky(new AllowUpdatePolicyChangeEvent(true));
            EventBus.getDefault().post(new UpdateDetectionsRectsEvent(detections, 0, 0, false, this.analyzeView.getImageMatrix()));
            this.analyzerThread = null;

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