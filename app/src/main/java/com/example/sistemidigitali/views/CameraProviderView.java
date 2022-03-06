package com.example.sistemidigitali.views;

import static com.example.sistemidigitali.debugUtility.Debug.println;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.util.Size;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;

import com.example.sistemidigitali.customEvents.AllowUpdatePolicyChangeEvent;
import com.example.sistemidigitali.customEvents.CustomObjectDetectorAvailableEvent;
import com.example.sistemidigitali.customEvents.PictureTakenEvent;
import com.example.sistemidigitali.customEvents.UpdateDetectionsRectsEvent;
import com.example.sistemidigitali.enums.CustomObjectDetectorType;
import com.example.sistemidigitali.model.CustomGestureDetector;
import com.example.sistemidigitali.model.CustomObjectDetector;
import com.example.sistemidigitali.model.ImageUtility;
import com.google.common.util.concurrent.ListenableFuture;

import org.greenrobot.eventbus.EventBus;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.task.vision.detector.Detection;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraProviderView {

    public static CameraCharacteristics cameraCharacteristics;

    private static CustomObjectDetector objectDetector;
    private static int currentLensOrientation = CameraSelector.LENS_FACING_BACK;

    private final CustomGestureDetector customGestureDetector;
    private final ImageUtility imageUtility;

    private ListenableFuture<ProcessCameraProvider> provider;
    private Camera camera;

    private final PreviewView previewView;
    private Preview preview;
    private ImageCapture imageCapt;
    private ImageAnalysis imageAnalysis;
    private boolean liveDetection;
    private boolean flipNeeded;

    private final int currentDisplayRotation;
    private final MainActivity context;
    private final Executor analyzeExecutor;
    private final ExecutorService imageCaptureExecutors;

    private boolean isCameraAvailable;

    @SuppressLint("ClickableViewAccessibility")
    public CameraProviderView(MainActivity context, PreviewView previewView, CustomGestureDetector customGestureDetector) {
        this.context = context;
        this.isCameraAvailable = true;
        this.imageUtility = new ImageUtility(this.context);
        this.customGestureDetector = customGestureDetector;
        this.imageCaptureExecutors = Executors.newFixedThreadPool(2);
        this.analyzeExecutor = Executors.newSingleThreadExecutor();
        this.currentDisplayRotation = this.context.getDisplay().getRotation() * 90;

        this.previewView = previewView;
        this.previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        this.previewView.getPreviewStreamState().observe(this.context, (streamState) -> this.isCameraAvailable = streamState == PreviewView.StreamState.STREAMING);
        this.startCamera(currentLensOrientation);

        //Handler for the pinch-to-zoom gesture
        ScaleGestureDetector scaleGestureDetector = new ScaleGestureDetector(this.context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                camera.getCameraControl().setZoomRatio(camera.getCameraInfo().getZoomState().getValue().getZoomRatio() * detector.getScaleFactor());
                return true;
            }
        });

        this.previewView.setOnTouchListener((view, motionEvent) -> {
            scaleGestureDetector.onTouchEvent(motionEvent);
            this.customGestureDetector.update(motionEvent);

            //Focus on finger-up gesture
            //If liveDetection is enabled, the tap-to-focus gesture is disabled.
            if(motionEvent.getAction() == MotionEvent.ACTION_UP && !this.liveDetection) {
                MeteringPointFactory factory = this.previewView.getMeteringPointFactory();
                MeteringPoint point = factory.createPoint(motionEvent.getX(), motionEvent.getY());
                this.camera.getCameraControl().startFocusAndMetering(new FocusMeteringAction.Builder(point).build());
            }

            return true;
        });

        new Thread(() -> {
            if(objectDetector == null) objectDetector = new CustomObjectDetector(context, CustomObjectDetectorType.HIGH_PERFORMANCE);
            EventBus.getDefault().postSticky(new CustomObjectDetectorAvailableEvent(context, objectDetector, CustomObjectDetectorType.HIGH_PERFORMANCE));
        }).start();
    }

    public void setLiveDetection(boolean liveDetection) {
        this.liveDetection = liveDetection;
        EventBus.getDefault().post(new UpdateDetectionsRectsEvent(this.context, new ArrayList<>(), false, null, new ArrayList<>()));
        EventBus.getDefault().post(new AllowUpdatePolicyChangeEvent(this.context, this.liveDetection));
    }

    /**
     * Create a new CameraX instance with the specified lens orientation,
     * unbinding all the previous use cases.
     * @param lensOrientation An int that indicates the lens orientation.
     */
    @SuppressLint({"RestrictedApi", "UnsafeOptInUsageError"})
    private void startCamera(int lensOrientation) {
        currentLensOrientation = lensOrientation;
        this.flipNeeded = currentLensOrientation == CameraSelector.LENS_FACING_FRONT;

        this.provider = ProcessCameraProvider.getInstance(this.context);
        this.provider.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = this.provider.get();
                cameraProvider.unbindAll(); //Clear use cases

                //Camera Selector
                CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(currentLensOrientation).build();

                //Preview View
                this.preview = new Preview.Builder().build();
                preview.setSurfaceProvider(this.previewView.getSurfaceProvider());

                //Image Capture
                this.imageCapt = new ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .setTargetRotation(Surface.ROTATION_0)
                                .build();

                //Image Analysis (default resolution: 480x640 portrait, 640x480 landscape)
                this.imageAnalysis = new ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .setOutputImageRotationEnabled(true)
                                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                                    .build();
                this.imageAnalysis.setAnalyzer(this.analyzeExecutor, this::analyze);

                UseCaseGroup useCaseGroup = new UseCaseGroup.Builder()
                                            .addUseCase(this.preview)
                                            .addUseCase(this.imageCapt)
                                            .addUseCase(this.imageAnalysis)
                                            .setViewPort(this.previewView.getViewPort())
                                            .build();

                this.camera = cameraProvider.bindToLifecycle(this.context, cameraSelector, useCaseGroup);

                CameraManager cameraManager = (CameraManager) this.context.getSystemService(Context.CAMERA_SERVICE);
                cameraCharacteristics = cameraManager.getCameraCharacteristics(Camera2CameraInfo.from(this.camera.getCameraInfo()).getCameraId());
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                EventBus.getDefault().post(new UpdateDetectionsRectsEvent(this.context, new ArrayList<>(), false, null, new ArrayList<>()));
            }
        }, this.context.getMainExecutor());
    }

    /**
     * Switches the current camera lens orientation.
     * If the current lens is the backward facing one, then the front facing lens is selected.
     * Otherwise the backward facing lens is selected.
     */
    public void switchCamera() {
        this.startCamera(currentLensOrientation == CameraSelector.LENS_FACING_BACK ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK);
    }

    /**
     * Take numOfFrames pictures.
     * If the saving is successful an AnalyzeActivity is started by
     * asynchronously passing it the picture's uri.
     */
    @SuppressLint({"UnsafeOptInUsageError, SimpleDateFormat", "RestrictedApi"})
    public void captureImages(int numOfFrames) {
        if(!this.isCameraAvailable) return;

        int i = numOfFrames;
        ArrayList<Bitmap> frames = new ArrayList<>();

        //Take the required amount of pictures
        while(i-- > 0) {
            this.imageCapt.takePicture(
                this.imageCaptureExecutors,
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy imageProxy) {
                        Bitmap frame = imageUtility.convertImageToBitmap(imageProxy.getImage(), getRotationDegree(imageProxy), currentLensOrientation == CameraSelector.LENS_FACING_FRONT);
                        frames.add(frame);

                        imageProxy.close();

                        /*
                         * Whenever the required amount of images have been captured,
                         * open a new analyze activity which will handle any error
                         */
                        if (frames.size() == numOfFrames) {
                            EventBus.getDefault().postSticky(new PictureTakenEvent(frames, "success"));
                            context.startActivity(new Intent(context, AnalyzeActivity.class));
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {}
                }
            );
        }
    }


    /**
     * Analyzes the given an imageProxy, and update the main view associated
     * with this cameraProvider by drawing all the detections' rectangles on it.
     * The next analysis will be executed only after the current one closes the imageProxy.
     * @param imageProxy The imageProxy of the image to analyze
     */
    @SuppressLint({"UnsafeOptInUsageError", "RestrictedApi"})
    public void analyze(@NonNull ImageProxy imageProxy) {
        if (!this.liveDetection) { //!cameraAvailable
            imageProxy.close();
            return;
        }

        final TensorImage tensorImage = new TensorImage();
        tensorImage.load(imageProxy.getImage());

        final Size originalImageResolution = this.imageCapt.getResolutionInfo().getResolution();
        final float analyzeImageWidth  = imageProxy.getWidth();
        final float analyzeImageHeight = imageProxy.getHeight();
        final float screenWidth  = this.previewView.getWidth();
        final float screenHeight = this.previewView.getHeight();

        final float translateX = this.flipNeeded ? 0.5F * (analyzeImageWidth - screenWidth) : 0.5F * (screenWidth - analyzeImageWidth);
        final float translateY = 0.5F * (screenHeight - analyzeImageHeight);
        float scaleX = originalImageResolution.getWidth()  / screenWidth;
        float scaleY = originalImageResolution.getHeight() / screenHeight;
        final float scalingFactor = scaleX > scaleY ? screenHeight / analyzeImageHeight : screenWidth / analyzeImageWidth;

        //previewView's scale type is FILL_CENTER, so
        //the transformations have the center of the previewView as the pivot
        Matrix matrix = new Matrix();
        matrix.preTranslate(translateX, translateY);
        matrix.postScale(scalingFactor, scalingFactor, 0.5F * screenWidth, 0.5F * screenHeight);


        long init = System.currentTimeMillis();
        List<Detection> detections = CameraProviderView.objectDetector.detect(tensorImage);
        println(System.currentTimeMillis() - init);

        EventBus.getDefault().post(new UpdateDetectionsRectsEvent(this.context, detections, this.flipNeeded, matrix, new ArrayList<>()));
        imageProxy.close();
    }

    private int getRotationDegree(ImageProxy imageProxy) {
        int rotationDirection = currentLensOrientation == CameraSelector.LENS_FACING_BACK ? 1 : -1;
        int constantRotation = imageProxy.getImageInfo().getRotationDegrees() - this.camera.getCameraInfo().getSensorRotationDegrees();
        return this.camera.getCameraInfo().getSensorRotationDegrees() - this.currentDisplayRotation + constantRotation * rotationDirection;
    }
}
