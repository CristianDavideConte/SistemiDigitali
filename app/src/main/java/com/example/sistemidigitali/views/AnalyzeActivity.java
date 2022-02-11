package com.example.sistemidigitali.views;

import static com.example.sistemidigitali.debugUtility.Debug.println;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;

import com.bogdwellers.pinchtozoom.ImageMatrixTouchHandler;
import com.example.sistemidigitali.R;
import com.example.sistemidigitali.customEvents.AllowUpdatePolicyChangeEvent;
import com.example.sistemidigitali.customEvents.CustomObjectDetectorAvailableEvent;
import com.example.sistemidigitali.customEvents.EndOfGestureEvent;
import com.example.sistemidigitali.customEvents.GestureIsMoveEvent;
import com.example.sistemidigitali.customEvents.GestureIsZoomEvent;
import com.example.sistemidigitali.customEvents.ImageSavedEvent;
import com.example.sistemidigitali.customEvents.OverlayVisibilityChangeEvent;
import com.example.sistemidigitali.customEvents.PictureTakenEvent;
import com.example.sistemidigitali.customEvents.UpdateDetectionsRectsEvent;
import com.example.sistemidigitali.enums.CustomObjectDetectorType;
import com.example.sistemidigitali.model.CustomGestureDetector;
import com.example.sistemidigitali.model.CustomObjectDetector;
import com.example.sistemidigitali.model.DistanceCalculator;
import com.example.sistemidigitali.model.ImageSaver;
import com.example.sistemidigitali.model.ToastMessagesManager;
import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.task.vision.detector.Detection;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AnalyzeActivity extends AppCompatActivity {
    private static CustomObjectDetector objectDetector;

    private Bitmap frame1, frame2;
    private TensorImage originalImageTensor;

    private View backgroundOverlayAnalyze;
    private ImageView analyzeView;
    private LiveDetectionView liveDetectionViewAnalyze;
    private Chip analyzeButton;
    private Chip calcDistanceButton;
    private FloatingActionButton saveImageButton;
    private List<Detection> detections;

    private ImageMatrixTouchHandler zoomHandler;
    private Executor distanceCalculatorExecutor;
    private Executor analyzerExecutor;

    private ToastMessagesManager toastMessagesManager;
    private CustomGestureDetector customGestureDetector;

    private ProgressBar analyzeLoadingIndicator;
    private ProgressBar saveLoadingIndicator;
    private DistanceCalculator distanceCalculator;
    private ImageSaver imageSaver;

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
        this.saveImageButton = findViewById(R.id.saveImageButton);
        this.analyzeLoadingIndicator = findViewById(R.id.analyzeLoadingIndicator);
        this.saveLoadingIndicator = findViewById(R.id.saveLoadingIndicator);

        this.imageSaver = new ImageSaver(this);
        this.distanceCalculator = new DistanceCalculator();
        this.toastMessagesManager = new ToastMessagesManager(this, Toast.LENGTH_SHORT);
        this.distanceCalculatorExecutor = Executors.newSingleThreadExecutor();
        this.analyzerExecutor = Executors.newSingleThreadExecutor();
        this.customGestureDetector = new CustomGestureDetector();
        this.analyzeButton.setOnClickListener((view) -> this.toastMessagesManager.showToastIfNeeded());
        this.calcDistanceButton.setOnClickListener((view) -> this.toastMessagesManager.showToastIfNeeded());

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
     * Avoids graphical artifacts
     */
    @Override
    protected void onResume() {
        super.onResume();
        EventBus.getDefault().post(new UpdateDetectionsRectsEvent(new ArrayList<>(), false, null));
        if(this.analyzeButton.isChecked()) this.detectObjects();
        if(this.calcDistanceButton.isChecked()) this.calculateDistance();
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
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onPictureTaken(PictureTakenEvent event) {
        //If the picture is not available, go back to previous activity
        if(!event.getError().equals("success")) {
            EventBus.getDefault().removeStickyEvent(event);
            this.toastMessagesManager.showToast(event.getError());
            this.finish();
            return;
        }

        Bitmap image = event.getImage();

        loadAnalyzeComponents(image);
        loadDistanceCalculationComponents(image);
    }

    /**
     * Loads the first frame needed for generating a disparity map and
     * sets the analyzeView's bitmap with all the needed listeners.
     * @param image The bitmap associated with the image (frame1)
     */
    @SuppressLint("ClickableViewAccessibility")
    private void loadAnalyzeComponents(Bitmap image) {
        this.frame1 = image.copy(Bitmap.Config.ARGB_8888, true);
        this.originalImageTensor = TensorImage.fromBitmap(frame1);
        this.analyzeView.setImageBitmap(this.frame1);

        this.saveImageButton.setOnClickListener((view) -> {
            this.saveLoadingIndicator.setVisibility(View.VISIBLE);
            this.imageSaver.saveImage(this.frame1);
        });
        this.zoomHandler = new ImageMatrixTouchHandler(this);

        this.analyzeView.setOnTouchListener((view, motionEvent) -> {
            if(!this.customGestureDetector.shouldListenToTouchEvents()) return true;

            if(this.analyzeButton.isChecked()) customGestureDetector.update(motionEvent);

            return zoomHandler.onTouch(view, motionEvent);
        });
        if(objectDetector != null) this.analyzeButton.setCheckable(true);
        this.analyzeLoadingIndicator.setVisibility(View.GONE);
    }

    /**
     * Loads the second frame needed for generating a disparity map and
     * set all the needed listeners accordingly.
     * @param image The bitmap associated with the image (frame1)
     */
    private void loadDistanceCalculationComponents(Bitmap image) {
        this.frame2 = image.copy(Bitmap.Config.ARGB_8888, true);
        this.calcDistanceButton.setOnClickListener((view) -> {});
        this.calcDistanceButton.setOnCheckedChangeListener((view, isChecked) -> {
            if(isChecked) {
                this.calcDistanceButton.setText(". . .");
                this.calcDistanceButton.setCheckable(false);
                this.calculateDistance();
            } else {
                this.frame1 = this.frame2;
                this.analyzeView.setImageBitmap(frame2);
                this.calcDistanceButton.setText("Measure");
            }
        });
        this.calcDistanceButton.setCheckable(true);
    }


    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onCustomObjectDetectorAvailable(CustomObjectDetectorAvailableEvent event) {
        if(event.getContext() != this) return;
        this.analyzeButton.setOnClickListener((view) -> {});
        this.analyzeButton.setOnCheckedChangeListener((view, isChecked) -> {
            if(isChecked) {
                this.analyzeLoadingIndicator.setVisibility(View.VISIBLE);
                this.analyzeButton.setText(". . .");
                this.analyzeButton.setCheckable(false);
                this.detectObjects();
            } else {
                this.analyzeButton.setText("Analyze");
                EventBus.getDefault().post(new AllowUpdatePolicyChangeEvent(this, false));
            }
        });
        if(this.frame1 != null) this.analyzeButton.setCheckable(true);
        this.toastMessagesManager.hideToast();
        EventBus.getDefault().removeStickyEvent(event);
    }

    @SuppressLint("WrongConstant")
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onOverlayVisibilityChange(OverlayVisibilityChangeEvent event) {
        this.backgroundOverlayAnalyze.setVisibility(event.getVisibility());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onImageSaved(ImageSavedEvent event) {
        this.saveLoadingIndicator.setVisibility(View.GONE);
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
        this.analyzerExecutor.execute(() -> {
            this.detections = objectDetector.detect(this.originalImageTensor);
            EventBus.getDefault().post(new UpdateDetectionsRectsEvent(detections, false, this.analyzeView.getImageMatrix()));
            EventBus.getDefault().post(new AllowUpdatePolicyChangeEvent(this, true));

            if(!this.analyzeButton.isCheckable()) {
                runOnUiThread(() -> {
                    this.analyzeLoadingIndicator.setVisibility(View.GONE);
                    this.analyzeButton.setText("Clear");
                    this.analyzeButton.setCheckable(true);
                });
            }
        });
    }

    private void calculateDistance() {
        this.distanceCalculatorExecutor.execute(() -> {
            //FOR TEST PURPOSES ONLY
            this.frame1 = this.distanceCalculator.getDisparityMap(this.frame1, this.frame1);
            runOnUiThread(() -> {
                this.analyzeView.setImageBitmap(frame1);
                this.calcDistanceButton.setText("Clear");
                this.calcDistanceButton.setCheckable(true);
            });
        });
    }
}