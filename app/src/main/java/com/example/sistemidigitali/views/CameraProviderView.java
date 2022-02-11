package com.example.sistemidigitali.views;

import static com.example.sistemidigitali.debugUtility.Debug.println;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.Image;
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
import com.google.common.util.concurrent.ListenableFuture;

import org.greenrobot.eventbus.EventBus;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.task.vision.detector.Detection;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraProviderView {

    public static CameraCharacteristics cameraCharacteristics;
    public static Bitmap lastFrameCaptured;

    private static CustomObjectDetector objectDetector;
    private static int currentLensOrientation = CameraSelector.LENS_FACING_BACK;

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
    private final CustomGestureDetector customGestureDetector;
    private final Executor analyzeExecutor;
    private final ExecutorService imageCaptureExecutors;

    private boolean isCameraAvailable;

    @SuppressLint("ClickableViewAccessibility")
    public CameraProviderView(MainActivity context, PreviewView previewView, CustomGestureDetector customGestureDetector) {
        this.context = context;
        this.liveDetection = false;
        this.customGestureDetector = customGestureDetector;
        this.imageCaptureExecutors = Executors.newFixedThreadPool(2);
        this.analyzeExecutor = Executors.newSingleThreadExecutor();
        this.currentDisplayRotation = this.context.getDisplay().getRotation() * 90;

        this.previewView = previewView;
        this.previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        this.previewView.getPreviewStreamState().observe(this.context, streamState -> this.isCameraAvailable = streamState == PreviewView.StreamState.STREAMING);
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
            try {
                if(objectDetector == null) objectDetector = new CustomObjectDetector(context, CustomObjectDetectorType.HIGH_PERFORMANCE);
                EventBus.getDefault().postSticky(new CustomObjectDetectorAvailableEvent(context, objectDetector, CustomObjectDetectorType.HIGH_PERFORMANCE));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void setLiveDetection(boolean liveDetection) {
        this.liveDetection = liveDetection;
        EventBus.getDefault().post(new UpdateDetectionsRectsEvent(new ArrayList<>(), false, null));
        EventBus.getDefault().post(new AllowUpdatePolicyChangeEvent(this.context, this.liveDetection));
    }

    /**
     * Create a new CameraX instance with the specified lens orientation,
     * unbinding all the previous use cases.
     * @param lensOrientation An int that indicates the lens orientation.
     */
    @SuppressLint({"RestrictedApi", "UnsafeOptInUsageError"})
    public void startCamera(int lensOrientation) {
        EventBus.getDefault().post(new AllowUpdatePolicyChangeEvent(this.context,false));

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
                EventBus.getDefault().post(new UpdateDetectionsRectsEvent(new ArrayList<>(), false, null));
                EventBus.getDefault().post(new AllowUpdatePolicyChangeEvent(this.context,true));
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
     * Take a picture of the current preview view frame and
     * saves it to the phone gallery.
     * If the saving is successful an AnalyzeActivity is started by
     * asynchronously passing it the picture's uri.
     */
    @SuppressLint({"UnsafeOptInUsageError, SimpleDateFormat", "RestrictedApi"})
    public void captureImage() {
        if(!this.isCameraAvailable) return;

        this.isCameraAvailable = false;
        this.liveDetection = false;

        //Take the picture

        this.imageCapt.takePicture(
                this.imageCaptureExecutors,
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy imageProxy) {
                        lastFrameCaptured = convertImageToBitmap(imageProxy.getImage(), getRotationDegree(imageProxy),currentLensOrientation == CameraSelector.LENS_FACING_FRONT);
                        imageProxy.close();
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        exception.printStackTrace();
                    }
                }
        );
        this.imageCapt.takePicture(
                this.imageCaptureExecutors,
                new ImageCapture.OnImageCapturedCallback(){
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy imageProxy) {
                        /*
                         * Whenever the required amount of images have been captured,
                         * open a new analyze activity which will handle any error
                         */
                        context.startActivity(new Intent(context, AnalyzeActivity.class));
                        isCameraAvailable = true;

                        Bitmap bitmapImage = convertImageToBitmap(imageProxy.getImage(), getRotationDegree(imageProxy),currentLensOrientation == CameraSelector.LENS_FACING_FRONT);
                        EventBus.getDefault().postSticky(new PictureTakenEvent(bitmapImage, "success"));
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        exception.printStackTrace();
                    }
                }
        );
    }


    /**
     * Analyzes the given an imageProxy, and update the main view associated
     * with this cameraProvider by drawing all the detections' rectangles on it.
     * The next analysis will be executed only after the current one closes the imageProxy.
     * @param imageProxy The imageProxy of the image to analyze
     */
    @SuppressLint({"UnsafeOptInUsageError", "RestrictedApi"})
    public void analyze(@NonNull ImageProxy imageProxy) {
        if (!this.liveDetection || !this.isCameraAvailable) {
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

        EventBus.getDefault().post(new UpdateDetectionsRectsEvent(detections, this.flipNeeded, matrix));
        imageProxy.close();
    }

    private int getRotationDegree(ImageProxy imageProxy) {
        int rotationDirection = this.currentLensOrientation == CameraSelector.LENS_FACING_BACK ? 1 : -1;
        int constantRotation = imageProxy.getImageInfo().getRotationDegrees() - this.camera.getCameraInfo().getSensorRotationDegrees();
        return this.camera.getCameraInfo().getSensorRotationDegrees() - this.currentDisplayRotation + constantRotation * rotationDirection;
    }

    /**
     * Converts the passed ImageProxy to a Bitmap image.
     * Source: https://stackoverflow.com/questions/56772967/converting-imageproxy-to-bitmap
     * @param image An ImageProxy
     * @param flipNeeded True if image needs to be mirrored on the y-axis, false otherwise.
     * @return The corresponding Bitmap image
     */
    private Bitmap convertImageToBitmap(@NonNull Image image, int rotationDegree, boolean flipNeeded) {
        ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
        byteBuffer.rewind();
        byte[] bytes = new byte[byteBuffer.capacity()];
        byteBuffer.get(bytes);

        BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;

        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, decodeOptions);
        Matrix matrix = new Matrix();
        matrix.postRotate(rotationDegree);
        if(flipNeeded) matrix.preScale(1.0f, -1.0f); //flip the image on the y-axis

        int width = Math.min(image.getWidth(), bitmap.getWidth());
        int height = Math.min(image.getHeight(), bitmap.getHeight());
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, flipNeeded);
        return bitmap;
    }

}
