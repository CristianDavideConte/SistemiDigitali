package com.example.sistemidigitali.views;

import static com.example.sistemidigitali.debugUtility.Debug.print;
import static com.example.sistemidigitali.debugUtility.Debug.println;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
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
import org.opencv.core.Point;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.task.vision.detector.Detection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AnalyzeActivity extends AppCompatActivity {
    private static CustomObjectDetector objectDetector;

    private Bitmap frame1, frame2;
    private Map<Bitmap, float[]> framesPositions;
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
    private Executor imageSaverExecutor;

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

        this.framesPositions = new HashMap<>();
        this.imageSaver = new ImageSaver(this);
        this.distanceCalculator = new DistanceCalculator();
        this.toastMessagesManager = new ToastMessagesManager(this, Toast.LENGTH_SHORT);
        this.distanceCalculatorExecutor = Executors.newSingleThreadExecutor();
        this.analyzerExecutor = Executors.newSingleThreadExecutor();
        this.imageSaverExecutor = Executors.newSingleThreadExecutor();
        this.customGestureDetector = new CustomGestureDetector();
        this.analyzeButton.setOnClickListener((view) -> this.toastMessagesManager.showToastIfNeeded());
        this.calcDistanceButton.setOnClickListener((view) -> this.toastMessagesManager.showToastIfNeeded());

        new Thread(() -> {
            if(objectDetector == null) objectDetector = new CustomObjectDetector(this, CustomObjectDetectorType.HIGH_ACCURACY);
            EventBus.getDefault().postSticky(new CustomObjectDetectorAvailableEvent(this, objectDetector, CustomObjectDetectorType.HIGH_ACCURACY));
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
        EventBus.getDefault().post(new UpdateDetectionsRectsEvent(this, new ArrayList<>(), false, null));
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
        List<Bitmap> frames = event.getFrames();

        //If the picture is not available, go back to previous activity
        if(!event.getError().equals("success") || frames.size() < 2) {
            EventBus.getDefault().removeStickyEvent(event);
            this.toastMessagesManager.showToast(event.getError());
            this.finish();
            return;
        }

        Bitmap frame1 = frames.get(0);
        Bitmap frame2 = frames.get(1);
        this.framesPositions = event.getFramesPositions();
        println("INIT ANALYZE");
        if(this.framesPositions.containsKey(frame1) && this.framesPositions.containsKey(frame2)) {
            final float x1 = this.framesPositions.get(frame1)[0];
            final float x2 = this.framesPositions.get(frame2)[0];
            final float y1 = this.framesPositions.get(frame1)[1];
            final float y2 = this.framesPositions.get(frame2)[1];
            final float z1 = this.framesPositions.get(frame1)[2];
            final float z2 = this.framesPositions.get(frame2)[2];

            println("xyz 1: ", x1, y1, z1);
            println("xyz 2: ", x2, y2, z2);

            println("DELTAX: ", x2 - x1);
            println("DELTAY: ", y2 - y1);
            println("DELTAZ: ", z2 - z1);

            if(x1 > x2) {
                loadAnalyzeComponents(frame2);
                loadDistanceCalculationComponents(frame1);
                return;
            }
        }

        loadAnalyzeComponents(frame1);
        loadDistanceCalculationComponents(frame2);
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
            this.saveImageButton.setClickable(false);
            this.saveLoadingIndicator.setVisibility(View.VISIBLE);
            this.imageSaverExecutor.execute(() -> {
                List<Bitmap> images = new ArrayList<>();
                images.add(this.frame1);
                images.add(this.frame2);
                images.add(this.distanceCalculator.getDisparityBitmap(this.frame1, this.frame2));
                this.imageSaver.saveImages(images);
            });
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
        this.frame2 = image;
        //this.frame2 = image.copy(Bitmap.Config.ARGB_8888, true);
        this.calcDistanceButton.setOnClickListener((view) -> {});
        this.calcDistanceButton.setOnCheckedChangeListener((view, isChecked) -> {
            if(isChecked) {
                this.calcDistanceButton.setText(". . .");
                this.calcDistanceButton.setCheckable(false);
                this.calculateDistance();
            } else {
                this.analyzeView.setImageBitmap(frame1);
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
                EventBus.getDefault().post(new UpdateDetectionsRectsEvent(this, new ArrayList<>(), false, null));
            }
        });

        if(this.frame1 != null) this.analyzeButton.setCheckable(true);

        this.toastMessagesManager.hideToast();
        EventBus.getDefault().removeStickyEvent(event);
    }

    @SuppressLint("WrongConstant")
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onOverlayVisibilityChange(OverlayVisibilityChangeEvent event) {
        int visibility = event.getVisibility();
        if(visibility == View.VISIBLE) this.detectObjects(); //Fixes a multitasking related rects-displaying bug
        this.backgroundOverlayAnalyze.setVisibility(visibility);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onImageSaved(ImageSavedEvent event) {
        this.saveLoadingIndicator.setVisibility(View.GONE);
        this.saveImageButton.setClickable(true);

        if(event.getError().equals("success")) return;

        this.saveImageButton.setOnClickListener((view) -> {
            this.toastMessagesManager.showToast();
        });
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void onGestureIsZoom(GestureIsZoomEvent event) {
        EventBus.getDefault().post(new UpdateDetectionsRectsEvent(this, new ArrayList<>(), false, null));
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void onGestureIsMove(GestureIsMoveEvent event) {
        EventBus.getDefault().post(new UpdateDetectionsRectsEvent(this, new ArrayList<>(), false, null));
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void onEndOfGesture(EndOfGestureEvent event) {
        if(this.zoomHandler.isAnimating()) {
            EventBus.getDefault().post(new AllowUpdatePolicyChangeEvent(this, false));
            while (this.zoomHandler.isAnimating());
            EventBus.getDefault().post(new UpdateDetectionsRectsEvent(this, new ArrayList<>(), false, null));
            EventBus.getDefault().post(new AllowUpdatePolicyChangeEvent(this, true));
        }
        this.detectObjects();
    }


    private void detectObjects() {
        this.analyzerExecutor.execute(() -> {
            this.detections = objectDetector.detect(this.originalImageTensor);
            EventBus.getDefault().post(new UpdateDetectionsRectsEvent(this, this.detections, false, this.analyzeView.getImageMatrix()));

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
            Bitmap disparityMap = this.distanceCalculator.getDisparityBitmap(this.frame1, this.frame2);
            //this.frame1 = this.distanceCalculator.getDisparityMap(this.frame1, this.frame2);
            /*println(this.distanceCalculator.getDistance(
                    new Point(disparityMap.getWidth() / 2F, disparityMap.getHeight() / 2F),
                    new Point(disparityMap.getWidth() / 2F + 300, disparityMap.getHeight() / 2F),
                    160,
                    this.distanceCalculator.getDisparityMap()
                    )
            );*/
            runOnUiThread(() -> {
                this.analyzeView.setImageBitmap(disparityMap);
                this.calcDistanceButton.setText("Clear");
                this.calcDistanceButton.setCheckable(true);
            });
        });
    }
}