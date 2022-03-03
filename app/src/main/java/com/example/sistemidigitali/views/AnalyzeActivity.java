package com.example.sistemidigitali.views;

import static com.example.sistemidigitali.debugUtility.Debug.println;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.RectF;
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
import com.example.sistemidigitali.customEvents.CustomDepthEstimatorAvailableEvent;
import com.example.sistemidigitali.customEvents.CustomObjectDetectorAvailableEvent;
import com.example.sistemidigitali.customEvents.EndOfGestureEvent;
import com.example.sistemidigitali.customEvents.GestureIsMoveEvent;
import com.example.sistemidigitali.customEvents.GestureIsZoomEvent;
import com.example.sistemidigitali.customEvents.ImageSavedEvent;
import com.example.sistemidigitali.customEvents.OverlayVisibilityChangeEvent;
import com.example.sistemidigitali.customEvents.PictureTakenEvent;
import com.example.sistemidigitali.customEvents.UpdateDetectionsRectsEvent;
import com.example.sistemidigitali.enums.CustomObjectDetectorType;
import com.example.sistemidigitali.model.CustomDepthEstimator;
import com.example.sistemidigitali.model.CustomGestureDetector;
import com.example.sistemidigitali.model.CustomObjectDetector;
import com.example.sistemidigitali.model.ImageUtility;
import com.example.sistemidigitali.model.ToastMessagesManager;
import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.task.vision.detector.Detection;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AnalyzeActivity extends AppCompatActivity {

    private static final float PX_TO_METERS = 0.0002645833F;

    private static final int TARGET_DEPTH_BITMAP_WIDTH = 256;
    private static final int TARGET_DEPTH_BITMAP_HEIGHT = 256;
    private static final int TARGET_DEPTH_MAP_WIDTH = 256*2;
    private static final int TARGET_DEPTH_MAP_HEIGHT = 256*2;

    private static CustomObjectDetector objectDetector;
    private static CustomDepthEstimator depthEstimator;

    private Bitmap frame;
    private float[] depthMap;
    private Bitmap depthMapImage;
    private TensorImage originalImageTensor;
    private ByteBuffer originalImageBuffer;

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
    private ImageUtility imageUtility;

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

        this.imageUtility = new ImageUtility(this);
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

        new Thread(() -> {
            if(depthEstimator == null) depthEstimator = new CustomDepthEstimator(this);
            EventBus.getDefault().postSticky(new CustomDepthEstimatorAvailableEvent(this, depthEstimator));
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
     * Used to asynchronously load an image into the preview view of this AnalyzeActivity.
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
        List<Bitmap> frames = event.getFrames();
        loadAnalyzeComponents(frames.get(0));
    }

    /**
     * Loads the first frame needed for generating a disparity map and
     * sets the analyzeView's bitmap with all the needed listeners.
     * @param image The bitmap associated with the image.
     */
    @SuppressLint("ClickableViewAccessibility")
    private void loadAnalyzeComponents(Bitmap image) {
        this.frame = image.copy(Bitmap.Config.ARGB_8888, true);
        this.originalImageTensor = TensorImage.fromBitmap(this.frame);
        this.originalImageBuffer = this.imageUtility.convertBitmapToBytebuffer(this.frame, TARGET_DEPTH_MAP_WIDTH, TARGET_DEPTH_MAP_HEIGHT);
        this.analyzeView.setImageBitmap(this.frame);

        this.saveImageButton.setOnClickListener((view) -> {
            this.saveImageButton.setClickable(false);
            this.saveLoadingIndicator.setVisibility(View.VISIBLE);
            this.imageSaverExecutor.execute(() -> {
                List<Bitmap> images = new ArrayList<>();
                images.add(this.frame);
                if(this.depthMapImage != null) images.add(this.depthMapImage);
                this.imageUtility.saveImages(images);
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
     * Sets all the listeners needed for detecting faces inside the image and
     * drawing the detections' rects.
     */
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
                this.calcDistanceButton.setVisibility(View.GONE);
                EventBus.getDefault().post(new UpdateDetectionsRectsEvent(this, new ArrayList<>(), false, null));
            }
        });

        if(this.frame != null) this.analyzeButton.setCheckable(true);

        this.toastMessagesManager.hideToast();
        EventBus.getDefault().removeStickyEvent(event);
    }

    /**
     * Sets all the listeners needed for generating the image's depth map and
     * calculating the distance between detections.
     */
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onDepthEstimatorAvailable(CustomDepthEstimatorAvailableEvent event) {
        if(event.getContext() != this) return;
        this.calcDistanceButton.setOnClickListener((view) -> {});
        this.calcDistanceButton.setOnCheckedChangeListener((view, isChecked) -> {
            if(isChecked) {
                this.calcDistanceButton.setText(". . .");
                this.calcDistanceButton.setCheckable(false);
                this.calculateDistance();
            } else {
                //this.analyzeView.setImageBitmap(frame);
                this.calcDistanceButton.setText("Measure");
            }
        });
        this.calcDistanceButton.setCheckable(true);
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

    /**
     * Starts (in background) the face detection on the analyzeView image and
     * once done, update all the needed components accordingly.
     */
    private void detectObjects() {
        this.analyzerExecutor.execute(() -> {
            this.originalImageTensor = TensorImage.fromBitmap(this.frame);

            this.detections = objectDetector.detect(this.originalImageTensor);
            EventBus.getDefault().post(new UpdateDetectionsRectsEvent(this, this.detections, false, this.analyzeView.getImageMatrix()));

            if(!this.analyzeButton.isCheckable()) {
                runOnUiThread(() -> {
                    this.analyzeLoadingIndicator.setVisibility(View.GONE);
                    this.analyzeButton.setText("Clear");
                    this.analyzeButton.setCheckable(true);
                    if(this.detections.size() > 0) { //<----------- CHANGE TO >1 WHEN OUT OF TESTING
                        this.calcDistanceButton.setChecked(false);
                        this.calcDistanceButton.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
    }

    private void calculateDistance() {
        this.distanceCalculatorExecutor.execute(() -> {
            this.depthMap = depthEstimator.getDepthMap(this.originalImageBuffer);
            this.depthMapImage = this.imageUtility.convertFloatArrayToBitmap(this.depthMap, TARGET_DEPTH_BITMAP_WIDTH, TARGET_DEPTH_BITMAP_HEIGHT);

            if(this.detections.size() == 1) {
                RectF boundingBoxDetection1 = this.detections.get(0).getBoundingBox();
                println("IN METERS", this.getZ(boundingBoxDetection1));
            } else if(this.detections.size() > 1) {
                RectF boundingBoxDetection1 = this.detections.get(0).getBoundingBox();
                RectF boundingBoxDetection2 = this.detections.get(1).getBoundingBox();
                println("DISTANCE BETWEEN PEOPLE",
                        getDistanceBetweenTwoPoints(
                                boundingBoxDetection1.centerX() * PX_TO_METERS, //NOT CORRECT <----- X scales with distance
                                boundingBoxDetection1.centerY() * PX_TO_METERS, //NOT CORRECT <----- Y scales with distance
                                this.getZ(boundingBoxDetection1),
                                boundingBoxDetection2.centerX() * PX_TO_METERS, //NOT CORRECT <----- X scales with distance
                                boundingBoxDetection2.centerY() * PX_TO_METERS, //NOT CORRECT <----- Y scales with distance
                                this.getZ(boundingBoxDetection2)
                        )
                );
            }

            runOnUiThread(() -> {
                //this.analyzeView.setImageBitmap(depthMapImage);
                this.calcDistanceButton.setText("Clear");
                this.calcDistanceButton.setCheckable(true);
            });
        });
    }

    /**
     * Calculates the average Z value of a detection in the 3D space.
     * @param detectionBoundingBox The pre-zoomed/scaled/translate detection's bounding Box (coordinates adjusted to the analyzeView).
     * @return The average Z of the passed detectionBoundingBox.
     */
    private double getZ(RectF detectionBoundingBox) {
        //Rectangle of the currently zoomed/scaled/translate AnalyzeView's image
        final RectF imageRect = new RectF(0, 0, this.frame.getWidth(), this.frame.getHeight());
        this.analyzeView.getImageMatrix().mapRect(imageRect);

        final float scaleX = (float) TARGET_DEPTH_BITMAP_WIDTH  / (float) this.frame.getWidth();
        final float scaleY = (float) TARGET_DEPTH_BITMAP_HEIGHT / (float) this.frame.getHeight();

        return depthEstimator.getDistancePhonePerson(
                this.depthMap,
                TARGET_DEPTH_BITMAP_WIDTH,
                TARGET_DEPTH_BITMAP_HEIGHT,
                (detectionBoundingBox.left - imageRect.left) * scaleX,
                detectionBoundingBox.width() * scaleX,
                (detectionBoundingBox.top - imageRect.top) * scaleY,
                detectionBoundingBox.height() * scaleY
        ); //In meters
    }

    /**
     * Calculate the 3D distance between two points.
     * @param x1 x of P1
     * @param y1 y of P1
     * @param z1 z of P1
     * @param x2 x of P2
     * @param y2 y of P2
     * @param z2 z of P2
     * @return
     */
    private double getDistanceBetweenTwoPoints(double x1, double y1, double z1, double x2, double y2, double z2) {
        return Math.sqrt(Math.abs((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1) + (z2 - z1) + (z2 - z1))); //In meters
    }
}