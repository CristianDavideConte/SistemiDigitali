package com.example.sistemidigitali.views;

import static com.example.sistemidigitali.debugUtility.Debug.println;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Size;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.Surface;

import androidx.annotation.NonNull;
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
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;

import com.example.sistemidigitali.customEvents.AllowUpdatePolicyChangeEvent;
import com.example.sistemidigitali.customEvents.CameraAvailabilityChangeEvent;
import com.example.sistemidigitali.customEvents.CustomObjectDetectorAvailableEvent;
import com.example.sistemidigitali.customEvents.ImageSavedEvent;
import com.example.sistemidigitali.customEvents.UpdateDetectionsRectsEvent;
import com.example.sistemidigitali.enums.CustomObjectDetectorType;
import com.example.sistemidigitali.model.CustomGestureDetector;
import com.example.sistemidigitali.model.CustomObjectDetector;
import com.example.sistemidigitali.views.AnalyzeActivity;
import com.example.sistemidigitali.views.MainActivity;
import com.google.common.util.concurrent.ListenableFuture;

import org.greenrobot.eventbus.EventBus;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.task.vision.detector.Detection;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class CameraProviderView {

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
    private final Executor imageCaptureExecutor;

    @SuppressLint("ClickableViewAccessibility")
    public CameraProviderView(MainActivity context, PreviewView previewView, CustomGestureDetector customGestureDetector) {
        this.context = context;
        this.liveDetection = false;
        this.customGestureDetector = customGestureDetector;
        this.imageCaptureExecutor = Executors.newSingleThreadExecutor();
        this.currentDisplayRotation = this.context.getDisplay().getRotation() * 90;

        this.previewView = previewView;
        this.previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        this.previewView.getPreviewStreamState().observe(this.context, streamState -> {
            EventBus.getDefault().post(new CameraAvailabilityChangeEvent(this.previewView.getPreviewStreamState().getValue() == PreviewView.StreamState.STREAMING));
        });
        this.startCamera(currentLensOrientation);

        //Handler for the pintch-to-zoom gesture
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
    @SuppressLint("RestrictedApi")
    public void startCamera(int lensOrientation) {
        EventBus.getDefault().post(new AllowUpdatePolicyChangeEvent(this.context,false));

        currentLensOrientation = lensOrientation;
        this.flipNeeded = currentLensOrientation == CameraSelector.LENS_FACING_FRONT;

        this.provider = ProcessCameraProvider.getInstance(this.context);
        this.provider.addListener(() -> {
            try {
                //Camera Selector
                CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(currentLensOrientation).build();

                //Preview View
                this.preview = new Preview.Builder().build();
                preview.setSurfaceProvider(this.previewView.getSurfaceProvider());

                //Image Capture
                this.imageCapt = new ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .setTargetRotation(Surface.ROTATION_0)
                                //.setTargetResolution(new Size(1920, 1080))
                                .build();

                //Image Analysis
                this.imageAnalysis = new ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .setOutputImageRotationEnabled(true)
                                    .setTargetResolution(new Size(240, 320)) //Default: 480x640
                                    .build();
                this.imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), this::analyze);


                UseCaseGroup useCaseGroup = new UseCaseGroup.Builder()
                                            .addUseCase(this.preview)
                                            .addUseCase(this.imageCapt)
                                            .addUseCase(this.imageAnalysis)
                                            .setViewPort(this.previewView.getViewPort())
                                            .build();

                ProcessCameraProvider cameraProvider = this.provider.get();
                cameraProvider.unbindAll(); //Clear usecases
                this.camera = cameraProvider.bindToLifecycle(this.context, cameraSelector, useCaseGroup);
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
        EventBus.getDefault().postSticky(new CameraAvailabilityChangeEvent(false));
        liveDetection = false;

        //Take the picture
        this.imageCapt.takePicture(
                this.imageCaptureExecutor,
                new ImageCapture.OnImageCapturedCallback(){
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy image) {
                        //Immediately open a new analyze activity which will handle any error
                        context.startActivity(new Intent(context, AnalyzeActivity.class));

                        //Es. SISDIG_2021127_189230.jpg
                        final String pictureName = "SISDIG_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".jpeg";

                        //Sources:
                        //https://stackoverflow.com/questions/56904485/how-to-save-an-image-in-android-q-using-mediastore
                        //https://developer.android.com/reference/android/content/ContentResolver#insert(android.net.Uri,%20android.content.ContentValues)
                        //https://developer.android.com/training/data-storage/use-cases#share-media-all
                        //https://developer.android.com/reference/androidx/camera/core/ImageCapture.OnImageCapturedCallback

                        //Create the picture's metadata
                        ContentValues newPictureDetails = new ContentValues();
                        newPictureDetails.put(MediaStore.Images.Media._ID, pictureName);
                        newPictureDetails.put(MediaStore.Images.Media.DISPLAY_NAME, pictureName);
                        newPictureDetails.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                        newPictureDetails.put(MediaStore.Images.Media.WIDTH, image.getWidth());
                        newPictureDetails.put(MediaStore.Images.Media.HEIGHT, image.getHeight());
                        newPictureDetails.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/SistemiDigitaliM");

                        //Add picture to MediaStore in order to make it accessible to other apps
                        //The result of the insert is the handle to the picture inside the MediaStore
                        Uri picturePublicUri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, newPictureDetails);

                        //Saves the image in the background and post the result on the EventBus
                        try {
                            OutputStream stream = context.getContentResolver().openOutputStream(picturePublicUri);
                            int rotationDirection = currentLensOrientation == CameraSelector.LENS_FACING_BACK ? 1 : -1;
                            int constantRotation = image.getImageInfo().getRotationDegrees() - camera.getCameraInfo().getSensorRotationDegrees();
                            int rotationDegree = camera.getCameraInfo().getSensorRotationDegrees() - currentDisplayRotation + constantRotation * rotationDirection;

                            Bitmap bitmapImage = convertImageToBitmap(image.getImage(), rotationDegree,currentLensOrientation == CameraSelector.LENS_FACING_FRONT);
                            if (!bitmapImage.compress(Bitmap.CompressFormat.JPEG, 100, stream)) {
                                throw new Exception("Image compression failed");
                            }

                            stream.close();
                            image.close();
                            EventBus.getDefault().postSticky(new ImageSavedEvent("success", picturePublicUri));
                        } catch (Exception e) {
                            e.printStackTrace();
                            //Remove the allocated space in the MediaStore if the picture can't be saved
                            context.getContentResolver().delete(picturePublicUri, new Bundle());
                            EventBus.getDefault().postSticky(new ImageSavedEvent(e.getMessage(), picturePublicUri));
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        exception.printStackTrace();
                        EventBus.getDefault().postSticky(new ImageSavedEvent(exception.getMessage(), Uri.parse("")));
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
        if (!this.liveDetection) {
            imageProxy.close();
            return;
        }

        try {
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
            List<Detection> detections = objectDetector.detect(tensorImage);
            println(System.currentTimeMillis() - init);

            EventBus.getDefault().post(new UpdateDetectionsRectsEvent(detections, this.flipNeeded, matrix));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            imageProxy.close();
        }
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
