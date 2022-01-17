package com.example.sistemidigitali.model;

import static com.example.sistemidigitali.debugUtility.Debug.println;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.Image;
import android.media.metrics.Event;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Size;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.WindowManager;
import android.widget.Toast;

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
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.example.sistemidigitali.customEvents.AllowUpdatePolicyChangeEvent;
import com.example.sistemidigitali.customEvents.UpdateDetectionsRectsEvent;
import com.example.sistemidigitali.views.AnalyzeActivity;
import com.example.sistemidigitali.views.MainActivity;
import com.google.common.util.concurrent.ListenableFuture;

import org.greenrobot.eventbus.EventBus;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.task.vision.detector.Detection;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executor;

import com.example.sistemidigitali.customEvents.ImageSavedEvent;

public class CameraProvider {

    private ListenableFuture<ProcessCameraProvider> provider;
    private Camera camera;
    private int currentLensOrientation;

    private PreviewView pview;
    private ImageCapture imageCapt;
    private ImageAnalysis imageAnalysis;
    private CustomObjectDetector objectDetector;
    private boolean liveDetection;

    private MainActivity context;
    private Thread analyzerThread;

    @SuppressLint("ClickableViewAccessibility")
    public CameraProvider(MainActivity context, PreviewView pview) {
        this.context = context;
        this.pview = pview;
        this.liveDetection = false;
        this.startCamera(CameraSelector.LENS_FACING_BACK);

        //Handler for the pintch-to-zoom gesture
        ScaleGestureDetector scaleGestureDetector = new ScaleGestureDetector(this.context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                camera.getCameraControl().setZoomRatio(camera.getCameraInfo().getZoomState().getValue().getZoomRatio() * detector.getScaleFactor());
                return true;
            }
        });

        this.pview.setOnTouchListener((view, motionEvent) -> {
            scaleGestureDetector.onTouchEvent(motionEvent);

            //Focus on finger-up gesture
            if(motionEvent.getAction() == MotionEvent.ACTION_UP) {
                //If liveDetection is enabled, the tap-to-focus gesture is disabled.
                if(liveDetection) {
                    EventBus.getDefault().postSticky(motionEvent);
                } else {
                    MeteringPointFactory factory = this.pview.getMeteringPointFactory();
                    MeteringPoint point = factory.createPoint(motionEvent.getX(), motionEvent.getY());
                    this.camera.getCameraControl().startFocusAndMetering(new FocusMeteringAction.Builder(point).build());
                }
            }

            return true;
        });
    }

    public void setObjectDetector(CustomObjectDetector objectDetector) {
        this.objectDetector = objectDetector;
    }

    public void setLiveDetection(boolean liveDetection) {
        EventBus.getDefault().postSticky(new AllowUpdatePolicyChangeEvent(liveDetection));
        this.liveDetection = liveDetection;
    }

    /**
     * @return The current context's main executor
     */
    private Executor getExecutor() {
        return ContextCompat.getMainExecutor(this.context);
    }

    /**
     * Create a new CameraX instance with the specified lens orientation,
     * unbinding all the previous use cases.
     * @param lensOrientation An int that indicates the lens orientation.
     */
    @SuppressLint("RestrictedApi")
    public void startCamera(int lensOrientation) {
        this.provider = ProcessCameraProvider.getInstance(this.context);
        this.provider.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = this.provider.get();
                cameraProvider.unbindAll(); //Clear usecases
                this.currentLensOrientation = lensOrientation;
                CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(this.currentLensOrientation).build();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(this.pview.getSurfaceProvider());

                Rect screenBounds = this.context.getWindowManager().getCurrentWindowMetrics().getBounds();

                println(screenBounds.width(), screenBounds.height());

                this.imageCapt = new ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                //.setTargetResolution(new Size(screenBounds.width(), screenBounds.height()))
                                .build();
                this.imageAnalysis = new ImageAnalysis.Builder()
                                // enable the following line if RGBA output is needed.
                                //.setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .setOutputImageRotationEnabled(true)

                                .build();

                this.imageAnalysis.setAnalyzer(this.getExecutor(), (proxy) -> this.analyze(proxy));
                this.camera = cameraProvider.bindToLifecycle(this.context, cameraSelector, this.imageAnalysis, preview, this.imageCapt);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, this.getExecutor());
    }

    /**
     * Switches the current camera lens orientation.
     * If the current lens is the backward facing one, then the front facing lens is selected.
     * Otherwise the backward facing lens is selected.
     */
    public void switchCamera() {
        int newLensOrientation = this.currentLensOrientation == CameraSelector.LENS_FACING_BACK ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
        this.startCamera(newLensOrientation);
    }

    /**
     * Take a picture of the current preview view frame and
     * saves it to the phone gallery.
     * If the saving is successful an AnalyzeActivity is started by
     * asynchronously passing it the picture's uri.
     */
    @SuppressLint("SimpleDateFormat")
    public void captureImage() {
        //Es. SISDIG_2021127_189230.jpg
        String pictureName = "SISDIG_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".jpeg";

        this.imageCapt.takePicture(
                this.getExecutor(),
                new ImageCapture.OnImageCapturedCallback(){
                    @Override
                    public void onCaptureSuccess(ImageProxy image) {
                        //Sources:
                        //https://stackoverflow.com/questions/56904485/how-to-save-an-image-in-android-q-using-mediastore
                        //https://developer.android.com/reference/android/content/ContentResolver#insert(android.net.Uri,%20android.content.ContentValues)
                        //https://developer.android.com/training/data-storage/use-cases#share-media-all
                        //https://developer.android.com/reference/androidx/camera/core/ImageCapture.OnImageCapturedCallback

                        //Create the picture's metadata
                        ContentValues newPictureDetails = new ContentValues();
                        newPictureDetails.put(MediaStore.Images.Media._ID, pictureName);
                        newPictureDetails.put(MediaStore.Images.Media.ORIENTATION, String.valueOf(-image.getImageInfo().getRotationDegrees()));
                        newPictureDetails.put(MediaStore.Images.Media.DISPLAY_NAME, pictureName);
                        newPictureDetails.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                        newPictureDetails.put(MediaStore.Images.Media.WIDTH, image.getWidth());
                        newPictureDetails.put(MediaStore.Images.Media.HEIGHT, image.getHeight());
                        newPictureDetails.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/SistemiDigitaliM");

                        //Add picture to MediaStore in order to make it accessible to other apps
                        //The result of the insert is the handle to the picture inside the MediaStore
                        Uri picturePublicUri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, newPictureDetails);

                        //Saves the image in the background and post the result on the EventBus
                        new Thread(() -> {
                            try {
                                OutputStream stream = context.getContentResolver().openOutputStream(picturePublicUri);

                                Bitmap bitmapImage = convertImageProxyToBitmap(image, currentLensOrientation == CameraSelector.LENS_FACING_FRONT);
                                if (!bitmapImage.compress(Bitmap.CompressFormat.JPEG, 100, stream)) {
                                    throw new Exception("Image compression failed");
                                }

                                stream.close();
                                image.close();
                                EventBus.getDefault().postSticky(new ImageSavedEvent("success", picturePublicUri));
                            } catch (Exception e) {
                                //Remove the allocated space in the MediaStore if the picture can't be saved
                                context.getContentResolver().delete(picturePublicUri, new Bundle());
                                EventBus.getDefault().postSticky(new ImageSavedEvent(e.getMessage(), picturePublicUri));
                                Toast.makeText(context, "Error saving image", Toast.LENGTH_SHORT).show();
                            }
                        }).start();

                        //Open a new activity and passes it the picture's uri
                        context.startActivity(new Intent(context, AnalyzeActivity.class));
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        exception.printStackTrace();
                        Toast.makeText(context, "Error capturing image", Toast.LENGTH_SHORT).show();
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
    @SuppressLint("UnsafeOptInUsageError")
    public void analyze(@NonNull ImageProxy imageProxy) {
        this.analyzerThread = new Thread(() -> {
            if (this.objectDetector != null && this.liveDetection) {
                int rectsWidth = imageProxy.getWidth();
                int rectsHeight = imageProxy.getHeight();

                TensorImage tensorImage = new TensorImage();
                tensorImage.load(imageProxy.getImage());

                List<Detection> detections = this.objectDetector.detect(tensorImage);
                EventBus.getDefault().post(new UpdateDetectionsRectsEvent(detections, rectsWidth, rectsHeight, currentLensOrientation == CameraSelector.LENS_FACING_FRONT, new Matrix()));
            }
            imageProxy.close();
        });
        this.analyzerThread.start();
    }

    /**
     * Converts the passed ImageProxy to a Bitmap image.
     * Source: https://stackoverflow.com/questions/56772967/converting-imageproxy-to-bitmap
     * @param image An ImageProxy
     * @param flipNeeded True if image needs to be mirrored on the y-axis, false otherwise.
     * @return The corresponding Bitmap image
     */
    @SuppressLint("UnsafeOptInUsageError")
    private Bitmap convertImageProxyToBitmap(@NonNull ImageProxy image, boolean flipNeeded) {
        return convertImageToBitmap(image.getImage(), image.getImageInfo().getRotationDegrees(), flipNeeded);
    }

    private Bitmap convertImageToBitmap(@NonNull Image image, int rotationDegree, boolean flipNeeded) {
        //rotationDegree = 180; //Fix per simo
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
