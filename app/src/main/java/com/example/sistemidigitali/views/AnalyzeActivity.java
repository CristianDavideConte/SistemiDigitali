package com.example.sistemidigitali.views;

import static com.example.sistemidigitali.debugUtility.Debug.println;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bogdwellers.pinchtozoom.ImageMatrixTouchHandler;
import com.example.sistemidigitali.R;
import com.example.sistemidigitali.customEvents.AllowUpdatePolicyChangeEvent;
import com.example.sistemidigitali.customEvents.CustomObjectDetectorAvailableEvent;
import com.example.sistemidigitali.customEvents.EndOfGestureEvent;
import com.example.sistemidigitali.customEvents.GestureIsMoveEvent;
import com.example.sistemidigitali.customEvents.GestureIsZoomEvent;
import com.example.sistemidigitali.customEvents.ImageSavedEvent;
import com.example.sistemidigitali.customEvents.OverlayVisibilityChangeEvent;
import com.example.sistemidigitali.customEvents.UpdateDetectionsRectsEvent;
import com.example.sistemidigitali.enums.CustomObjectDetectorType;
import com.example.sistemidigitali.model.CustomGestureDetector;
import com.example.sistemidigitali.model.CustomObjectDetector;
import com.example.sistemidigitali.model.DistanceCalculator;
import com.example.sistemidigitali.model.ToastMessagesManager;
import com.google.android.material.chip.Chip;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.task.vision.detector.Detection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AnalyzeActivity extends AppCompatActivity {
    private static CustomObjectDetector objectDetector;

    private Bitmap originalImage;
    private TensorImage originalImageTensor;

    private View backgroundOverlayAnalyze;
    private ImageView analyzeView;
    private LiveDetectionView liveDetectionViewAnalyze;
    private Chip analyzeButton;
    private Chip calcDistanceButton;
    private List<Detection> detections;

    private ImageMatrixTouchHandler zoomHandler;
    private Executor analyzer;

    private ToastMessagesManager toastMessagesManager;
    private CustomGestureDetector customGestureDetector;

    private RelativeLayout loadingIndicator;
    private DistanceCalculator distanceCalculator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analyze);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        this.backgroundOverlayAnalyze = findViewById(R.id.backgroundOverlayAnalyze);
        this.analyzeView = findViewById(R.id.analyzeView);
        this.liveDetectionViewAnalyze = findViewById(R.id.liveDetectionViewAnalyze);
        this.analyzeButton = findViewById(R.id.analyzeButton);
        this.calcDistanceButton = findViewById(R.id.calcDistanceButton);
        this.loadingIndicator = findViewById(R.id.loadingIndicatorPanel);

        this.distanceCalculator = new DistanceCalculator();
        this.toastMessagesManager = new ToastMessagesManager(this, Toast.LENGTH_SHORT);
        this.analyzer = Executors.newSingleThreadExecutor();
        this.customGestureDetector = new CustomGestureDetector();
        this.analyzeButton.setOnClickListener((view) -> this.toastMessagesManager.showToastIfNeeded());

        new Thread(() -> {
            try {
                if(objectDetector == null) objectDetector = new CustomObjectDetector(this, CustomObjectDetectorType.HIGH_ACCURACY);
                EventBus.getDefault().postSticky(new CustomObjectDetectorAvailableEvent(this, objectDetector, CustomObjectDetectorType.HIGH_ACCURACY));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
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
     * Clears the screen when the application is resumed
     * to avoid graphical artifacts
     */
    @Override
    protected void onResume() {
        super.onResume();
        EventBus.getDefault().post(new UpdateDetectionsRectsEvent(new ArrayList<>(), false, null));
        if(this.analyzeButton.isChecked()) this.detectObjects();
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
            println("CAMERA IS CLOSED ERROR");
            EventBus.getDefault().removeStickyEvent(event);
            this.toastMessagesManager.showToast(event.getError());
            this.finish();
            return;
        }

        try {
            ImageDecoder.Source source = ImageDecoder.createSource(this.getContentResolver(), event.getUri());
            this.originalImage = ImageDecoder.decodeBitmap(source).copy(Bitmap.Config.ARGB_8888, true);
            this.originalImageTensor = TensorImage.fromBitmap(originalImage);
            this.analyzeView.setImageBitmap(this.originalImage);

            this.calcDistanceButton.setOnClickListener((view) -> {
                if(this.calcDistanceButton.isChecked()){
                    this.originalImage = this.distanceCalculator.getDisparityMap(this.originalImage, this.originalImage);
                    this.analyzeView.setImageBitmap(this.originalImage);
                }
            });
            this.zoomHandler = new ImageMatrixTouchHandler(this);
            this.analyzeView.setOnTouchListener((view, motionEvent) -> {
                if(!this.customGestureDetector.shouldListenToTouchEvents()) return true;

                if(this.analyzeButton.isChecked()) customGestureDetector.update(motionEvent);

                return zoomHandler.onTouch(view, motionEvent);
            });
            this.loadingIndicator.setVisibility(View.GONE);
            if(objectDetector != null) this.analyzeButton.setCheckable(true);
        } catch (IOException exception) {
            this.toastMessagesManager.showToast(exception.getMessage());
            this.finish();
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onCustomObjectDetectorAvailable(CustomObjectDetectorAvailableEvent event) {
        if(event.getContext() != this) return;
        this.analyzeButton.setOnClickListener((view) -> {});
        this.analyzeButton.setOnCheckedChangeListener((view, isChecked) -> {
            if(isChecked) {
                this.loadingIndicator.setVisibility(View.VISIBLE);
                this.analyzeButton.setText(". . .");
                this.analyzeButton.setCheckable(false);
                this.detectObjects();
            } else {
                this.analyzeButton.setText("Analyze");
                EventBus.getDefault().post(new AllowUpdatePolicyChangeEvent(this, false));
            }
        });
        if(this.originalImage != null) this.analyzeButton.setCheckable(true);
        this.toastMessagesManager.hideToast();
        EventBus.getDefault().removeStickyEvent(event);
    }

    @SuppressLint("WrongConstant")
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onOverlayVisibilityChange(OverlayVisibilityChangeEvent event) {
        this.backgroundOverlayAnalyze.setVisibility(event.getVisibility());
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void onGestureIsZoom(GestureIsZoomEvent event) {
        EventBus.getDefault().post(new AllowUpdatePolicyChangeEvent(this, false));
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void onGestureIsMove(GestureIsMoveEvent event) {
        EventBus.getDefault().post(new AllowUpdatePolicyChangeEvent(this, false));
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void onEndOfGesture(EndOfGestureEvent event) {
        this.detectObjects();
    }

    private void detectObjects() {
        this.analyzer.execute(() -> {
            this.detections = objectDetector.detect(this.originalImageTensor);
            EventBus.getDefault().post(new UpdateDetectionsRectsEvent(detections, false, this.analyzeView.getImageMatrix()));
            EventBus.getDefault().post(new AllowUpdatePolicyChangeEvent(this, true));

            if(!this.analyzeButton.isCheckable()) {
                runOnUiThread(() -> {
                        this.loadingIndicator.setVisibility(View.GONE);
                        this.analyzeButton.setText("Clear");
                        this.analyzeButton.setCheckable(true);
                });
            }
        });
    }
}