package com.example.sistemidigitali.views;

import static com.example.sistemidigitali.debugUtility.Debug.println;
import static com.example.sistemidigitali.model.CustomDepthEstimator.PX_TO_M_CONVERSION_FACTOR;
import static com.example.sistemidigitali.model.CustomDepthEstimator.STANDARD_FACE_WIDTH_PX;
import static com.example.sistemidigitali.model.CustomDepthEstimator.STANDARD_RESOLUTION_HEIGHT;
import static com.example.sistemidigitali.model.CustomDepthEstimator.STANDARD_RESOLUTION_WIDTH;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Bundle;
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
    private ImageView depthMapView;
    private LiveDetectionView liveDetectionViewAnalyze;
    private Chip analyzeButton;
    private FloatingActionButton saveImageButton;
    private FloatingActionButton showDepthMapButton;
    private List<Detection> detections;
    private List<DetectionLine> detectionLines;
    private ProgressBar analyzeLoadingIndicator;
    private ProgressBar saveLoadingIndicator;
    private ProgressBar depthMapLoadingIndicator;
    private ImageUtility imageUtility;

    private Executor distanceCalculatorExecutor;
    private Executor analyzerExecutor;
    private Executor imageSaverExecutor;
    private Executor showDepthMapExecutor;

    private ToastMessagesManager toastMessagesManager;
    private CustomVibrator customVibrator;

    private boolean frameIsFromGallery;
    private boolean gestureWasHold;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analyze);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        this.backgroundOverlayAnalyze = findViewById(R.id.backgroundOverlayAnalyze);
        this.analyzeView = findViewById(R.id.analyzeView);
        this.depthMapView = findViewById(R.id.depthMapView);
        this.liveDetectionViewAnalyze = findViewById(R.id.liveDetectionViewAnalyze);
        this.analyzeButton = findViewById(R.id.analyzeButton);
        this.saveImageButton = findViewById(R.id.saveImageButton);
        this.showDepthMapButton = findViewById(R.id.showDepthMapButton);
        this.analyzeLoadingIndicator = findViewById(R.id.analyzeLoadingIndicator);
        this.saveLoadingIndicator = findViewById(R.id.saveLoadingIndicator);
        this.depthMapLoadingIndicator = findViewById(R.id.showDepthMapLoadingIndicator);

        this.frameIsFromGallery = false;
        this.gestureWasHold = false;
        this.customVibrator = new CustomVibrator(this);
        this.imageUtility = new ImageUtility(this);
        this.liveDetectionViewAnalyze.setMinimumAccuracyForDetectionsDisplay(0.0F);
        this.toastMessagesManager = new ToastMessagesManager(this, Toast.LENGTH_SHORT);
        this.distanceCalculatorExecutor = Executors.newSingleThreadExecutor();
        this.analyzerExecutor = Executors.newSingleThreadExecutor();
        this.imageSaverExecutor = Executors.newSingleThreadExecutor();
        this.showDepthMapExecutor = Executors.newSingleThreadExecutor();
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
        this.frameIsFromGallery = event.isFromGallery();
        this.liveDetectionViewAnalyze.adjustSelectedStrokeWidth(image.getWidth());
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

        this.showDepthMapButton.setOnClickListener((view) -> {
            this.customVibrator.vibrateLight();
            this.showDepthMapButton.setClickable(false);
            this.depthMapLoadingIndicator.setVisibility(View.VISIBLE);
            this.showDepthMapExecutor.execute(() -> {
                if(this.depthMap == null) {
                    this.depthMap = depthEstimator.getDepthMap(this.originalImageBuffer);
                    this.depthMapImage = this.imageUtility.convertFloatArrayToBitmap(this.depthMap, TARGET_DEPTH_MAP_WIDTH, TARGET_DEPTH_MAP_HEIGHT);
                }
                runOnUiThread(() -> {
                    if (this.depthMapView.getVisibility() == View.VISIBLE) {
                        this.analyzeView.setVisibility(View.VISIBLE);
                        this.depthMapView.setVisibility(View.GONE);
                    } else {
                        this.depthMapView.setImageBitmap(Bitmap.createScaledBitmap(this.depthMapImage, this.frame.getWidth(), this.frame.getHeight(), true));
                        this.depthMapView.setVisibility(View.VISIBLE);
                        this.analyzeView.setVisibility(View.GONE);
                    }
                    this.depthMapLoadingIndicator.setVisibility(View.GONE);
                    this.showDepthMapButton.setClickable(true);
                });
            });
        });

        this.saveImageButton.setOnClickListener((view) -> {
            this.customVibrator.vibrateLight();
            this.saveImageButton.setClickable(false);
            this.saveLoadingIndicator.setVisibility(View.VISIBLE);
            this.imageSaverExecutor.execute(() -> {
                List<Bitmap> images = new ArrayList<>();
                if(!this.frameIsFromGallery) images.add(this.frame);
                if(this.depthMapImage == null) {
                    this.depthMap = depthEstimator.getDepthMap(this.originalImageBuffer);
                    this.depthMapImage = this.imageUtility.convertFloatArrayToBitmap(this.depthMap, TARGET_DEPTH_MAP_WIDTH, TARGET_DEPTH_MAP_HEIGHT);
                }
                //The depth map image is always size x size,
                //before saving it, it needs to be scaled to the right proportions
                images.add(Bitmap.createScaledBitmap(this.depthMapImage, this.frame.getWidth(), this.frame.getHeight(), true));
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
            /**
             * Calculate the depth map if necessary
             */
            final List<Detection> selectedDetections = this.liveDetectionViewAnalyze.getSelectedDetections();
            if (selectedDetections.size() < 2) return;

            this.depthMap = depthEstimator.getDepthMap(this.originalImageBuffer);
            this.depthMapImage = this.imageUtility.convertFloatArrayToBitmap(this.depthMap, TARGET_DEPTH_MAP_WIDTH, TARGET_DEPTH_MAP_HEIGHT);
            this.detectionLines = new ArrayList<>();

            /**
             * Calculate the distance between the selected detections
             */
            final Detection furthestDetection = getFurthestDetection(); //Furthest based on the depth map informations

            final RectF boundingBoxDetection1 = selectedDetections.get(0).getBoundingBox();
            final RectF boundingBoxDetection2 = selectedDetections.get(1).getBoundingBox();
            final RectF boundingBoxFurthestDetection = furthestDetection.getBoundingBox();

            final double resolutionScalingFactor = STANDARD_RESOLUTION_WIDTH / this.frame.getWidth();

            //Useful variables
            final double frameCenterX = this.frame.getWidth() * 0.5;
            final double frameCenterY = this.frame.getHeight() * 0.5;

            final double detection1CenterX = boundingBoxDetection1.centerX();
            final double detection2CenterX = boundingBoxDetection2.centerX();
            final double detection1CenterY = boundingBoxDetection1.centerY();
            final double detection2CenterY = boundingBoxDetection2.centerY();

            final double detection1Right = boundingBoxDetection1.right;
            final double detection2Right = boundingBoxDetection2.right;
            final double detection1Left = boundingBoxDetection1.left;
            final double detection2Left = boundingBoxDetection2.left;

            final double detection1Top = boundingBoxDetection1.top;
            final double detection2Top = boundingBoxDetection2.top;
            final double detection1Bottom = boundingBoxDetection1.bottom;
            final double detection2Bottom = boundingBoxDetection2.bottom;

            final double detection1Width = boundingBoxDetection1.width() ;//* resolutionScalingFactor;
            final double detection2Width = boundingBoxDetection2.width() ;//* resolutionScalingFactor;



            //These are the distances (in meters) from the observer to the center of the detections.
            //N.B. The detections and the observer can have different heights (these distances may have an angle).
            final double distanceMax = getMaxDistanceFromObserver(furthestDetection);
            final double distance1 =
                    (STANDARD_FACE_WIDTH_PX / detection1Width) * distanceMax /
                    (STANDARD_FACE_WIDTH_PX / boundingBoxFurthestDetection.width());
            final double distance2 =
                    (STANDARD_FACE_WIDTH_PX / detection2Width) * distanceMax /
                    (STANDARD_FACE_WIDTH_PX / boundingBoxFurthestDetection.width());

            //These are the distances (in meters) between every detection and the center (x-axis) of the frame,
            //scaled by taking into account the distance the detection is at.
            final double x1 = detection1CenterX > frameCenterX ?
                    distance1 * (frameCenterX - detection1Left) * PX_TO_M_CONVERSION_FACTOR :
                    distance1 * (frameCenterX - detection1Right) * PX_TO_M_CONVERSION_FACTOR;
            final double x2 = detection2CenterX > frameCenterX ?
                    distance2 * (frameCenterX - detection2Left) * PX_TO_M_CONVERSION_FACTOR :
                    distance2 * (frameCenterX - detection2Right) * PX_TO_M_CONVERSION_FACTOR;

            //These are the distances (in meters) between every detection and the center (y-axis) of the frame,
            //scaled by taking into account the distance the detection is at.
            final double y1 = detection1CenterY > frameCenterY ?
                    distance1 * (frameCenterY - detection1Top) * PX_TO_M_CONVERSION_FACTOR :
                    distance1 * (frameCenterY - detection1Bottom) * PX_TO_M_CONVERSION_FACTOR;
            final double y2 = detection2CenterY > frameCenterY ?
                    distance2 * (frameCenterY - detection2Top) * PX_TO_M_CONVERSION_FACTOR :
                    distance2 * (frameCenterY - detection2Bottom) * PX_TO_M_CONVERSION_FACTOR;

            //These are the normalized distances between the observer and the detections.
            //"Normalized" means: these are the distances between the lowest between the observer and every detection,
            //and the corresponding lowered point on the other (makes the observer and the detection's heights the same).
            final double distance1Projection = Math.sqrt(distance1 * distance1 - y1 * y1);
            final double distance2Projection = Math.sqrt(distance2 * distance2 - y2 * y2);

            //These are the depths (in meters) of every detection from the observer point of view,
            final double z1 = Math.sqrt(Math.abs(distance1Projection * distance1Projection - x1 * x1));
            final double z2 = Math.sqrt(Math.abs(distance2Projection * distance2Projection - x2 * x2));

            println("DETECTION 1:\n",
                    distance1,
                    distance1Projection,
                    x1,
                    y1,
                    z1,
                    "DETECTION 2:\n",
                    distance2,
                    distance2Projection,
                    x2,
                    y2,
                    z2
            );

            final double distance = getDistanceBetweenTwoPoints(x1, y1, z1, x2, y2, z2);

            /**
             * Draw the detections' lines
             */
            final List<Integer> colors = getDepthLineColors(distance);

            final double centersDistance = x1 - x2;
            double startX = centersDistance > 0 ? detection1Right : detection1Left;
            double endX   = centersDistance > 0 ? detection2Left : detection2Right;
            if (Math.abs(startX - endX) * resolutionScalingFactor < 500 &&
                    Math.abs(detection1CenterY - detection2CenterY) * resolutionScalingFactor > 200) {
                        startX = boundingBoxDetection1.centerX();
                        endX = boundingBoxDetection2.centerX();
            }
            this.detectionLines.add(
                    new DetectionLine(
                            startX,
                            detection1CenterY,
                            endX,
                            detection2CenterY,
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


    private double getMaxDistanceFromObserver(Detection furthestDetection) {
        return STANDARD_FACE_WIDTH_PX / furthestDetection.getBoundingBox().width();
    }

    private Detection getFurthestDetection() {
        final RectF imageRect = new RectF(0, 0, this.frame.getWidth(), this.frame.getHeight());
        this.analyzeView.getImageMatrix().mapRect(imageRect);

        //The scales are used to get the coordinates of the bounding boxes with respect to the frame
        //instead of the whole screen.
        final float scaleX = (float) TARGET_DEPTH_MAP_WIDTH  / (float) this.frame.getWidth();
        final float scaleY = (float) TARGET_DEPTH_MAP_HEIGHT / (float) this.frame.getHeight();

        double minAvgDepth = Float.POSITIVE_INFINITY;
        Detection furthestDetection = null;

        for(Detection detection : this.detections) {
            final RectF boundingBoxDetection = detection.getBoundingBox();
            final double avgDepth = depthEstimator.getAverageDepthInDetection(
                    depthMap,
                    TARGET_DEPTH_MAP_WIDTH,
                    TARGET_DEPTH_MAP_HEIGHT,
                    (boundingBoxDetection.left - imageRect.left) * scaleX,
                    boundingBoxDetection.width() * scaleX,
                    (boundingBoxDetection.top - imageRect.top) * scaleY,
                    boundingBoxDetection.height() * scaleY
            );

            if(avgDepth <= minAvgDepth) {
                furthestDetection = detection;
                minAvgDepth = avgDepth;
            }
        }
        return furthestDetection;
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
        return Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1) + (z2 - z1) * (z2 - z1)); //In meters
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
}