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
import com.example.sistemidigitali.customEvents.ClearSelectedDetectionEvent;
import com.example.sistemidigitali.customEvents.ImageSavedEvent;
import com.example.sistemidigitali.customEvents.NeuralNetworkAvailableEvent;
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
import org.tensorflow.lite.task.vision.detector.Detection;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AnalyzeActivity extends AppCompatActivity {
    private static final double SAFE_DISTANCE_M = 1.0; //In Meters

    private static final double STANDARD_FACE_WIDTH_M = 0.152; //In Meters
    private static final double STANDARD_FACE_HEIGHT_M = 0.232; //In Meters
    private static final double STANDARD_FACE_WIDTH_PX = 211.67; //In Pixels
    private static final double STANDARD_FACE_HEIGHT_PX = 333.9583; //In Pixels

    private static final double PX_TO_M_CONVERSION_FACTOR_V1 = 1 * STANDARD_FACE_WIDTH_M  / STANDARD_FACE_WIDTH_PX;
    private static final double PX_TO_M_CONVERSION_FACTOR_V2 = 1 * STANDARD_FACE_HEIGHT_M / STANDARD_FACE_HEIGHT_PX;
    private static final double PX_TO_M_CONVERSION_FACTOR = (PX_TO_M_CONVERSION_FACTOR_V1 + PX_TO_M_CONVERSION_FACTOR_V2) / 2.0;

    private static final int TARGET_DEPTH_MAP_WIDTH = 256;
    private static final int TARGET_DEPTH_MAP_HEIGHT = 256;

    private static final int MAX_SELECTABLE_DETECTIONS = 2;

    private static CustomObjectDetector objectDetector;
    private static CustomDepthEstimator depthEstimator;

    private Bitmap frame;
    private float[] depthMap;
    private Bitmap depthMapImage;
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
        this.liveDetectionViewAnalyze.setMinimumAccuracyForDetectionsDisplay(0.0F);
        this.toastMessagesManager = new ToastMessagesManager(this, Toast.LENGTH_SHORT);
        this.distanceCalculatorExecutor = Executors.newSingleThreadExecutor();
        this.analyzerExecutor = Executors.newSingleThreadExecutor();
        this.imageSaverExecutor = Executors.newSingleThreadExecutor();
        this.analyzeButton.setOnClickListener((view) -> {
            this.customVibrator.vibrateLight();
            this.toastMessagesManager.showToastIfNeeded();
        });
        this.saveImageButton.setOnClickListener((view) -> {
            this.customVibrator.vibrateLight();
            this.toastMessagesManager.showToastIfNeeded();
        });

        new Thread(() -> {
            if(objectDetector == null) objectDetector = new CustomObjectDetector(this, CustomObjectDetectorType.HIGH_ACCURACY);
            if(depthEstimator == null) depthEstimator = new CustomDepthEstimator(this);
            EventBus.getDefault().postSticky(new NeuralNetworkAvailableEvent(this));
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
        final Bitmap image = event.getFrames().get(0);
        this.liveDetectionViewAnalyze.setWidthAndHeight(image.getWidth(), image.getHeight());
        loadAnalyzeComponents(image);
    }

    /**
     * Loads the first frame needed for generating a disparity map and
     * sets the analyzeView's bitmap with all the needed listeners.
     * @param image The bitmap associated with the image.
     */
    @SuppressLint("ClickableViewAccessibility")
    private void loadAnalyzeComponents(Bitmap image) {
        this.frame = image.copy(Bitmap.Config.ARGB_8888, true);
        this.originalImageBuffer = this.imageUtility.convertBitmapToBytebuffer(this.frame, TARGET_DEPTH_MAP_WIDTH, TARGET_DEPTH_MAP_HEIGHT);
        this.analyzeView.setImageBitmap(this.frame);

        final GestureDetector gestureDetector = new GestureDetector( this, new GestureDetector.SimpleOnGestureListener() {
            public void onLongPress(MotionEvent motionEvent) {
                println("FRAME:",frame.getWidth(),"x",frame.getHeight());
                for(Detection detection : detections) {
                    println("WIDTH x HEIGHT", detection.getBoundingBox().width(),"x",detection.getBoundingBox().height());
                    println("DISTANCE FROM CAMERA", depthEstimator.getDistanceFromObserver(detection.getBoundingBox(), frame.getWidth(), frame.getHeight()));
                    println("ANGLE", depthEstimator.getAngleFromScreenCenter(detection.getBoundingBox(), frame.getWidth(), frame.getHeight()) * 90 / Math.PI);
                }

                gestureWasHold = true;
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
    public void onNeuralNetworkAvailable(NeuralNetworkAvailableEvent event) {
        if(event.getContext() != this) return;
        this.analyzeButton.setOnClickListener((view) -> {});
        this.analyzeButton.setOnCheckedChangeListener((view, isChecked) -> {
            this.customVibrator.vibrateLight();
            if(isChecked) {
                this.analyzeLoadingIndicator.setVisibility(View.VISIBLE);
                this.analyzeButton.setText(". . .");
                this.analyzeButton.setCheckable(false);
                this.detectObjects(this.liveDetectionViewAnalyze.getSelectedDetections().size() == MAX_SELECTABLE_DETECTIONS);
            } else {
                this.analyzeButton.setText("Analyze");
                EventBus.getDefault().postSticky(new ClearSelectedDetectionEvent());
                EventBus.getDefault().post(new UpdateDetectionsRectsEvent(this, new ArrayList<>(), false, null, new ArrayList<>()));
            }
        });

        this.saveImageButton.setOnClickListener((view) -> {
            this.customVibrator.vibrateLight();
            this.saveImageButton.setClickable(false);
            this.saveLoadingIndicator.setVisibility(View.VISIBLE);
            this.imageSaverExecutor.execute(() -> {
                List<Bitmap> images = new ArrayList<>();
                images.add(this.frame);
                if(this.depthMapImage == null) {
                    this.depthMap = depthEstimator.getDepthMap(this.originalImageBuffer);
                    this.depthMapImage = this.imageUtility.convertFloatArrayToBitmap(this.depthMap, TARGET_DEPTH_MAP_WIDTH, TARGET_DEPTH_MAP_HEIGHT);
                }
                //The depth map image is always size x size,
                //before saving it, it needs to be scaled to the right proportions
                final float widthScalingFactor;
                final float heightScalingFactor;
                final float scalingFactor = (float)(this.frame.getHeight()) / (float)(this.frame.getWidth());
                if(scalingFactor > 1) {
                    widthScalingFactor = 1;
                    heightScalingFactor = scalingFactor;
                } else {
                    widthScalingFactor = 1 / scalingFactor;
                    heightScalingFactor = 1;
                }
                images.add(Bitmap.createScaledBitmap(this.depthMapImage, (int)(this.depthMapImage.getWidth() * widthScalingFactor), (int)(this.depthMapImage.getHeight() * heightScalingFactor), true));
                this.imageUtility.saveImages(images);
            });
        });

        if(this.frame != null) this.analyzeButton.setCheckable(true);

        this.toastMessagesManager.hideToast();
        EventBus.getDefault().removeStickyEvent(event);
    }

    @SuppressLint("WrongConstant")
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onOverlayVisibilityChange(OverlayVisibilityChangeEvent event) {
        final int visibility = event.getVisibility();
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
            this.detections = objectDetector.detect(this.frame);
            this.detections.parallelStream().forEach(detection -> this.analyzeView.getImageMatrix().mapRect(detection.getBoundingBox()));
            this.detectionLines = new ArrayList<>();

            if(shouldAlsoCalculateDistances) calculateDistance();
            else EventBus.getDefault().post(new UpdateDetectionsRectsEvent(this, this.detections, false, this.analyzeView.getImageMatrix(), this.detectionLines));

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
            final List<Detection> selectedDetections = this.liveDetectionViewAnalyze.getSelectedDetections();
            if(selectedDetections.size() < 2) return;

            this.depthMap = depthEstimator.getDepthMap(this.originalImageBuffer);
            this.depthMapImage = this.imageUtility.convertFloatArrayToBitmap(this.depthMap, TARGET_DEPTH_MAP_WIDTH, TARGET_DEPTH_MAP_HEIGHT);
            this.detectionLines = new ArrayList<>();

            final RectF boundingBoxDetection1 = selectedDetections.get(0).getBoundingBox();
            final RectF boundingBoxDetection2 = selectedDetections.get(1).getBoundingBox();

            final double distanceFromObserver1 = depthEstimator.getDistanceFromObserver(boundingBoxDetection1, this.frame.getWidth(), this.frame.getHeight());
            final double distanceFromObserver2 = depthEstimator.getDistanceFromObserver(boundingBoxDetection2, this.frame.getWidth(), this.frame.getHeight());

            final double distanceFromScreenCenter1 = depthEstimator.getPerspectiveWidth(boundingBoxDetection1, this.frame.getWidth(), this.frame.getHeight());
            final double distanceFromScreenCenter2 = depthEstimator.getPerspectiveWidth(boundingBoxDetection2, this.frame.getWidth(), this.frame.getHeight());

            final double depth1 = Math.sqrt(distanceFromObserver1 * distanceFromObserver1 - distanceFromScreenCenter1 * distanceFromScreenCenter1);
            final double depth2 = Math.sqrt(distanceFromObserver2 * distanceFromObserver2 - distanceFromScreenCenter2 * distanceFromScreenCenter2);

            final double deltaZBetweenTwoDetections = Math.abs(depth2 - depth1);
            final double deltaXBetweenTwoDetections = Math.abs(distanceFromScreenCenter1) + Math.abs(distanceFromScreenCenter2);

            final double distance = Math.sqrt(deltaZBetweenTwoDetections * deltaZBetweenTwoDetections + deltaXBetweenTwoDetections * deltaXBetweenTwoDetections);

            println("DISTANCE FROM OBSERVER 1", distanceFromObserver1);
            println("DISTANCE FROM OBSERVER 2", distanceFromObserver2);
            println("DISTANCE FROM SCREEN CENTER 1", distanceFromScreenCenter1);
            println("DISTANCE FROM SCREEN CENTER 2", distanceFromScreenCenter2);
            println("DEPTH 1", depth1);
            println("DEPTH 2", depth2);

            println("deltaZBetweenTwoDetections", deltaZBetweenTwoDetections);
            println("deltaXBetweenTwoDetections", deltaXBetweenTwoDetections);
            println("DISTANCE", distance);

            //If the mask is correctly worn, the safe distance doesn't matter
            final List<Integer> colors = this.getWearingMode(selectedDetections.get(0)) == WearingModeEnum.MRCW || this.getWearingMode(selectedDetections.get(1)) == WearingModeEnum.MRCW ?
                    getDepthLineColors(SAFE_DISTANCE_M) : getDepthLineColors(distance);

            float startX, endX;
            final float centersDistance = boundingBoxDetection1.centerX() - boundingBoxDetection2.centerX();
            if (centersDistance < 0) {
                startX = boundingBoxDetection1.centerX() + boundingBoxDetection1.width() / 2;
                endX = boundingBoxDetection2.centerX() - boundingBoxDetection2.width() / 2;
            } else {
                startX = boundingBoxDetection1.centerX() - boundingBoxDetection1.width() / 2;
                endX = boundingBoxDetection2.centerX() + boundingBoxDetection2.width() / 2;
            }
            if(Math.abs(startX - endX) < 100) {
                startX = boundingBoxDetection1.centerX();
                endX = boundingBoxDetection2.centerX();
            }
            this.detectionLines.add(
                    new DetectionLine(
                            startX,
                            boundingBoxDetection1.centerY(),
                            endX,
                            boundingBoxDetection2.centerY(),
                            String.format("%.2f", distance) + "M",
                            colors.get(0),
                            colors.get(1),
                            boundingBoxDetection1.height() * 0.25F,
                            boundingBoxDetection2.height() * 0.25F
                    )
            );
            EventBus.getDefault().post(new UpdateDetectionsRectsEvent(this, this.detections, false, null, this.detectionLines));
        });
    }


    @SuppressLint("DefaultLocale")
    private void calculateDistance2() {
        this.distanceCalculatorExecutor.execute(() -> {
            final List<Detection> selectedDetections = this.liveDetectionViewAnalyze.getSelectedDetections();
            if(selectedDetections.size() < 2) return;

            this.depthMap = depthEstimator.getDepthMap(this.originalImageBuffer);
            this.depthMapImage = this.imageUtility.convertFloatArrayToBitmap(this.depthMap, TARGET_DEPTH_MAP_WIDTH, TARGET_DEPTH_MAP_HEIGHT);
            this.detectionLines = new ArrayList<>();

            final DisplayMetrics displayMetrics = this.getResources().getDisplayMetrics();

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

            //If the mask is correctly worn, the safe distance doesn't matter
            final List<Integer> colors = this.getWearingMode(selectedDetections.get(0)) == WearingModeEnum.MRCW || this.getWearingMode(selectedDetections.get(1)) == WearingModeEnum.MRCW ?
                                         getDepthLineColors(SAFE_DISTANCE_M) : getDepthLineColors(distance);

            float startX, endX;
            final float centersDistance = boundingBoxDetection1.centerX() - boundingBoxDetection2.centerX();
            if (centersDistance < 0) {
                startX = boundingBoxDetection1.centerX() + boundingBoxDetection1.width() / 2;
                endX = boundingBoxDetection2.centerX() - boundingBoxDetection2.width() / 2;
            } else {
                startX = boundingBoxDetection1.centerX() - boundingBoxDetection1.width() / 2;
                endX = boundingBoxDetection2.centerX() + boundingBoxDetection2.width() / 2;
            }
            if(Math.abs(startX - endX) < 100) {
                startX = boundingBoxDetection1.centerX();
                endX = boundingBoxDetection2.centerX();
            }
            this.detectionLines.add(
                    new DetectionLine(
                            startX,
                            boundingBoxDetection1.centerY(),
                            endX,
                            boundingBoxDetection2.centerY(),
                            String.format("%.2f", distance) + "M",
                            colors.get(0),
                            colors.get(1),
                            boundingBoxDetection1.height() * 0.25F,
                            boundingBoxDetection2.height() * 0.25F
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

        final float scaleX = (float) TARGET_DEPTH_MAP_WIDTH  / (float) this.frame.getWidth();
        final float scaleY = (float) TARGET_DEPTH_MAP_HEIGHT / (float) this.frame.getHeight();

        return depthEstimator.getDistancePhonePerson(
                this.depthMap,
                TARGET_DEPTH_MAP_WIDTH,
                TARGET_DEPTH_MAP_HEIGHT,
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
     * @return The 3D distance between two points
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