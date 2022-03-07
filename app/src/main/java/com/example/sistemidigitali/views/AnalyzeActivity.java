package com.example.sistemidigitali.views;

import static com.example.sistemidigitali.debugUtility.Debug.println;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.sistemidigitali.R;
import com.example.sistemidigitali.customEvents.CustomObjectDetectorAvailableEvent;
import com.example.sistemidigitali.customEvents.ImageSavedEvent;
import com.example.sistemidigitali.customEvents.OverlayVisibilityChangeEvent;
import com.example.sistemidigitali.customEvents.PictureTakenEvent;
import com.example.sistemidigitali.customEvents.UpdateDetectionsRectsEvent;
import com.example.sistemidigitali.enums.CustomObjectDetectorType;
import com.example.sistemidigitali.enums.WearingModeEnum;
import com.example.sistemidigitali.model.CustomDepthEstimator;
import com.example.sistemidigitali.model.CustomObjectDetector;
import com.example.sistemidigitali.model.CustomVibrator;
import com.example.sistemidigitali.model.DetectionLine;
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
    private static final double SAFE_DISTANCE_M = 1.0; //In Meters

    private static final double STANDARD_FACE_WIDTH_M = 0.147; //In Meters
    private static final double STANDARD_FACE_HEIGHT_M = 0.234; //In Meters
    private static final double STANDARD_FACE_WIDTH_PX = 211.67; //In Pixels
    private static final double STANDARD_FACE_HEIGHT_PX = 333.9583; //In Pixels

    private static final double PX_TO_M_CONVERSION_FACTOR_V1 = 1 * STANDARD_FACE_WIDTH_M  / STANDARD_FACE_WIDTH_PX;
    private static final double PX_TO_M_CONVERSION_FACTOR_V2 = 1 * STANDARD_FACE_HEIGHT_M / STANDARD_FACE_HEIGHT_PX;
    private static final double PX_TO_M_CONVERSION_FACTOR = (PX_TO_M_CONVERSION_FACTOR_V1 + PX_TO_M_CONVERSION_FACTOR_V2) / 2.0;

    private static final int TARGET_DEPTH_BITMAP_WIDTH = 256;
    private static final int TARGET_DEPTH_BITMAP_HEIGHT = 256;
    private static final int TARGET_DEPTH_MAP_WIDTH = 256*2;
    private static final int TARGET_DEPTH_MAP_HEIGHT = 256*2;

    private static final int MAX_SELECTABLE_DETECTIONS = 2;

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
    private FloatingActionButton saveImageButton;
    private List<Detection> detections;
    private List<DetectionLine> detectionLines;

    private Executor distanceCalculatorExecutor;
    private Executor analyzerExecutor;
    private Executor imageSaverExecutor;

    private ToastMessagesManager toastMessagesManager;

    private ProgressBar analyzeLoadingIndicator;
    private ProgressBar saveLoadingIndicator;
    private ImageUtility imageUtility;

    private boolean gestureWasHold;
    private CustomVibrator customVibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analyze);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        this.backgroundOverlayAnalyze = findViewById(R.id.backgroundOverlayAnalyze);
        this.analyzeView = findViewById(R.id.analyzeView);
        this.liveDetectionViewAnalyze = findViewById(R.id.liveDetectionViewAnalyze);
        this.analyzeButton = findViewById(R.id.analyzeButton);
        this.saveImageButton = findViewById(R.id.saveImageButton);
        this.analyzeLoadingIndicator = findViewById(R.id.analyzeLoadingIndicator);
        this.saveLoadingIndicator = findViewById(R.id.saveLoadingIndicator);

        this.gestureWasHold = false;
        this.customVibrator = new CustomVibrator(this);
        this.imageUtility = new ImageUtility(this);
        this.toastMessagesManager = new ToastMessagesManager(this, Toast.LENGTH_SHORT);
        this.distanceCalculatorExecutor = Executors.newSingleThreadExecutor();
        this.analyzerExecutor = Executors.newSingleThreadExecutor();
        this.imageSaverExecutor = Executors.newSingleThreadExecutor();
        this.analyzeButton.setOnClickListener((view) -> this.toastMessagesManager.showToastIfNeeded());

        new Thread(() -> {
            if(objectDetector == null) objectDetector = new CustomObjectDetector(this, CustomObjectDetectorType.HIGH_ACCURACY);
            EventBus.getDefault().postSticky(new CustomObjectDetectorAvailableEvent(this, objectDetector, CustomObjectDetectorType.HIGH_ACCURACY));
        }).start();

        new Thread(() -> {
            if(depthEstimator == null) depthEstimator = new CustomDepthEstimator(this);
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
    }

    /**
     * Avoids graphical artifacts
     */
    @Override
    protected void onResume() {
        super.onResume();
        if(this.analyzeButton.isChecked()) {
            this.detectObjects(this.liveDetectionViewAnalyze.getSelectedDetections().size() == MAX_SELECTABLE_DETECTIONS);
        }
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
        loadAnalyzeComponents(event.getFrames().get(0));
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
            this.customVibrator.vibrateLight();
            this.saveImageButton.setClickable(false);
            this.saveLoadingIndicator.setVisibility(View.VISIBLE);
            this.imageSaverExecutor.execute(() -> {
                List<Bitmap> images = new ArrayList<>();
                images.add(this.frame);
                if(this.depthMapImage != null) images.add(this.depthMapImage);
                this.imageUtility.saveImages(images);
            });
        });

        final GestureDetector gestureDetector = new GestureDetector( this, new GestureDetector.SimpleOnGestureListener() {
            public void onLongPress(MotionEvent motionEvent) {
                gestureWasHold = true;
                if(depthEstimator == null) {
                    toastMessagesManager.showToast();
                    return;
                }
                final int previousSelectedDetections = liveDetectionViewAnalyze.getSelectedDetections().size();
                if(liveDetectionViewAnalyze.onHold(motionEvent) == MAX_SELECTABLE_DETECTIONS && previousSelectedDetections < MAX_SELECTABLE_DETECTIONS) {
                    calculateDistance();
                }
            }
        });
        this.analyzeView.setOnTouchListener((view, motionEvent) -> {
            if(this.analyzeButton.isChecked()) gestureDetector.onTouchEvent(motionEvent);
            if(motionEvent.getAction() == MotionEvent.ACTION_UP) {
                if(this.gestureWasHold) this.gestureWasHold = false;
                else EventBus.getDefault().post(motionEvent);
            }
            return true;
        });
        this.backgroundOverlayAnalyze.setOnTouchListener((view, MotionEvent) -> true); //Avoids the event propagation to the analyzeView
        this.analyzeLoadingIndicator.setVisibility(View.GONE);
        if(objectDetector != null) this.analyzeButton.setCheckable(true);
    }


    /**
     * Sets all the listeners needed for detecting faces inside the image and
     * drawing the detections' rects.
     */
    @Subscribe(sticky = true, threadMode = ThreadMode.ASYNC)
    public void onCustomObjectDetectorAvailable(CustomObjectDetectorAvailableEvent event) {
        if(event.getContext() != this) return;
        this.analyzeButton.setOnClickListener((view) -> {});
        this.analyzeButton.setOnCheckedChangeListener((view, isChecked) -> {
            this.customVibrator.vibrateLight();
            if(isChecked) {
                this.analyzeLoadingIndicator.setVisibility(View.VISIBLE);
                this.analyzeButton.setText(". . .");
                this.analyzeButton.setCheckable(false);
                this.detectObjects(false);
            } else {
                this.liveDetectionViewAnalyze.setSelectedDetections(new ArrayList<>());
                this.analyzeButton.setText("Analyze");
                EventBus.getDefault().post(new UpdateDetectionsRectsEvent(this, new ArrayList<>(), false, null, new ArrayList<>()));
            }
        });

        if(this.frame != null) this.analyzeButton.setCheckable(true);

        this.toastMessagesManager.hideToast();
        EventBus.getDefault().removeStickyEvent(event);
    }

    @SuppressLint("WrongConstant")
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onOverlayVisibilityChange(OverlayVisibilityChangeEvent event) {
        final int visibility = event.getVisibility();
        if(visibility == View.VISIBLE) { //Fixes a multitasking related rects-displaying bug
            this.detectObjects(this.liveDetectionViewAnalyze.getSelectedDetections().size() == MAX_SELECTABLE_DETECTIONS);
        }
        this.backgroundOverlayAnalyze.setVisibility(visibility);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onImageSaved(ImageSavedEvent event) {
        this.saveLoadingIndicator.setVisibility(View.GONE);
        this.saveImageButton.setClickable(true);

        if(event.getError().equals("success")) return;

        this.saveImageButton.setOnClickListener((view) -> {
            this.toastMessagesManager.showToast("Saving operation failed");
        });
    }

    /**
     * Starts (in background) the face detection on the analyzeView image and
     * once done, update all the needed components accordingly.
     */
    private void detectObjects(boolean shouldAlsoCalculateDistances) {
        this.analyzerExecutor.execute(() -> {
            //this.originalImageTensor = TensorImage.fromBitmap(this.frame);

            this.detections = objectDetector.detect(this.originalImageTensor);
            this.detections.parallelStream().forEach(detection -> this.analyzeView.getImageMatrix().mapRect(detection.getBoundingBox()));
            this.detectionLines = new ArrayList<>();

            if(shouldAlsoCalculateDistances) calculateDistance();
            else EventBus.getDefault().post(new UpdateDetectionsRectsEvent(this, this.detections, false, null, this.detectionLines));

            if(!this.analyzeButton.isCheckable()) {
                runOnUiThread(() -> {
                    this.analyzeLoadingIndicator.setVisibility(View.GONE);
                    this.analyzeButton.setText("Clear");
                    this.analyzeButton.setCheckable(true);
                });
            }
        });
    }

    @SuppressLint("DefaultLocale")
    private void calculateDistance() {
        this.distanceCalculatorExecutor.execute(() -> {
            this.depthMap = depthEstimator.getDepthMap(this.originalImageBuffer);
            this.depthMapImage = this.imageUtility.convertFloatArrayToBitmap(this.depthMap, TARGET_DEPTH_BITMAP_WIDTH, TARGET_DEPTH_BITMAP_HEIGHT);
            this.detectionLines = new ArrayList<>();

            final DisplayMetrics displayMetrics = this.getResources().getDisplayMetrics();
            final List<Detection> selectedDetections = this.liveDetectionViewAnalyze.getSelectedDetections();

            final RectF boundingBoxDetection1 = selectedDetections.get(0).getBoundingBox();
            final RectF boundingBoxDetection2 = selectedDetections.get(1).getBoundingBox();

            final double z1m = this.getZ(boundingBoxDetection1);
            final double z2m = this.getZ(boundingBoxDetection2);
            //z1m = this.getZ(boundingBoxDetection1);
            //z2m = this.getZ(boundingBoxDetection2);

            final double x1m = Math.abs(displayMetrics.widthPixels / 2.0 - boundingBoxDetection1.centerX()) * PX_TO_M_CONVERSION_FACTOR / z1m;
            final double y1m = Math.abs(displayMetrics.heightPixels / 2.0 - boundingBoxDetection1.centerY()) * PX_TO_M_CONVERSION_FACTOR / z1m;

            final double x2m = Math.abs(displayMetrics.widthPixels / 2.0 - boundingBoxDetection2.centerX()) * PX_TO_M_CONVERSION_FACTOR / z2m;
            final double y2m = Math.abs(displayMetrics.heightPixels / 2.0 - boundingBoxDetection2.centerY()) * PX_TO_M_CONVERSION_FACTOR / z2m;

            final double distance = getDistanceBetweenTwoPoints(x1m, y1m, z1m, x2m, y2m, z2m);

            final double areaDetection1 = boundingBoxDetection1.width() * boundingBoxDetection1.height();
            final double areaDetection2 = boundingBoxDetection2.width() * boundingBoxDetection2.height();

            //If the mask is correctly worn, the safe distance doesn't matter
            final List<Integer> colors = this.getWearingMode(selectedDetections.get(0)) == WearingModeEnum.MRCW || this.getWearingMode(selectedDetections.get(1)) == WearingModeEnum.MRCW ?
                                         getDepthLineColors(SAFE_DISTANCE_M) : getDepthLineColors(distance);

            final int detectionLineStartCorrectionXFactor, detectionLineEndCorrectionXFactor;
            if (boundingBoxDetection1.centerX() < boundingBoxDetection2.centerX()) {
                detectionLineStartCorrectionXFactor = +1;
                detectionLineEndCorrectionXFactor = -1;
            } else {
                detectionLineStartCorrectionXFactor = -1;
                detectionLineEndCorrectionXFactor = +1;
            }
            this.detectionLines.add(
                    new DetectionLine(
                            boundingBoxDetection1.centerX() + boundingBoxDetection1.width() / 2 * detectionLineStartCorrectionXFactor,
                            boundingBoxDetection1.centerY(),
                            boundingBoxDetection2.centerX() + boundingBoxDetection2.width() / 2 * detectionLineEndCorrectionXFactor,
                            boundingBoxDetection2.centerY(),
                            String.format("%.2f", distance) + "M",
                            colors.get(0),
                            colors.get(1),
                            (float) Math.max(0.0, areaDetection1 / areaDetection2),
                            (float) Math.max(0.0, areaDetection2 / areaDetection1)
                    )
            );
            EventBus.getDefault().post(new UpdateDetectionsRectsEvent(this, this.detections, false, null, this.detectionLines));
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

    private List<Integer> getDepthLineColors(double distance) {
        List<Integer> colors = new ArrayList<>();
        if(distance >= SAFE_DISTANCE_M) {
            colors.add(WearingModeEnum.MRCW.getBackgroundColor());
            colors.add(WearingModeEnum.MRCW.getTextColor());
        } else {
            colors.add(WearingModeEnum.MRNW.getBackgroundColor());
            colors.add(WearingModeEnum.MRNW.getTextColor());
        }
        return colors;
    }

    private WearingModeEnum getWearingMode(Detection detection) {
        try{
            String[] labelParts = detection.getCategories().get(0).getLabel().split("_");
            return WearingModeEnum.valueOf(labelParts[0]);
        } catch (IllegalArgumentException e) { //Test mode
            return WearingModeEnum.TEST;
        }
    }
}