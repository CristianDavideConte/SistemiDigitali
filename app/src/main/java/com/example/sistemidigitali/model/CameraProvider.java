package com.example.sistemidigitali.model;

import static com.example.sistemidigitali.debugUtility.Debug.println;

import android.app.Activity;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.example.sistemidigitali.R;
import com.google.common.util.concurrent.ListenableFuture;

import org.tensorflow.lite.task.vision.detector.Detection;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executor;

public class CameraProvider implements ImageAnalysis.Analyzer {

    private ListenableFuture<ProcessCameraProvider> provider;
    private PreviewView pview;
    private ImageView imageView;
    private ImageCapture imageCapt;
    private ImageAnalysis imageAn;

    private Activity context;
    private ContentProvider contentProvider;

    public CameraProvider(Activity context, PreviewView pview, ImageView imageView) {
        this.context = context;
        this.pview = pview;
        this.imageView = imageView;
        provider = ProcessCameraProvider.getInstance(this.context);
        provider.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = provider.get();
                startCamera(cameraProvider);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, this.getExecutor());
    }

    public void startCamera(ProcessCameraProvider cameraProvider) {
        cameraProvider.unbindAll(); //Clear usecases
        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build(); //backward facing camera

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(this.pview.getSurfaceProvider());

        this.imageCapt = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build();
        this.imageAn = new ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
        this.imageAn.setAnalyzer(this.getExecutor(), this);

        cameraProvider.bindToLifecycle((LifecycleOwner) this.context, cameraSelector, preview, this.imageCapt, this.imageAn);
    }

    public void capturePhoto() {
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
                        OutputStream stream = null;

                        try {
                            //Add picture to MediaStore in order to make it accessible to other apps
                            //The result of the insert is the handle to the picture inside the MediaStore
                            Uri picturePublicUri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, newPictureDetails);

                            stream = context.getContentResolver().openOutputStream(picturePublicUri);
                            Bitmap bitmapImage = convertImageProxyToBitmap(image);

                            CustomObjectDetector objectDetector = new CustomObjectDetector(context);
                            List<Detection> objs = objectDetector.detect(bitmapImage);

                            imageView.setWillNotDraw(false);
                            Canvas canvas = new Canvas(bitmapImage);
                            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                            p.setColor(Color.BLUE);
                            p.setTextSize(50);
                            println("DETECTED OBJS: " + objs.size());
                            for (Detection obj : objs) {
                                int top = (int) obj.getBoundingBox().top;
                                int right = (int) obj.getBoundingBox().right;
                                int bottom = (int) obj.getBoundingBox().bottom;
                                int left = (int) obj.getBoundingBox().left;


                                canvas.drawRect(new Rect(left, top, right, top + 10), p);
                                canvas.drawRect(new Rect(right + 10, top, right, bottom), p);
                                canvas.drawRect(new Rect(left, bottom + 10, right, bottom), p);
                                canvas.drawRect(new Rect(left, top, left + 10, bottom), p);

                                println("LABEL: " + obj.getCategories().get(0).getLabel());
                                canvas.drawText(obj.getCategories().get(0).getLabel(), 0.5f * (right + left),top - 50, p);
                            }

                            imageView.setImageResource(0);
                            imageView.draw(canvas);
                            imageView.setImageBitmap(bitmapImage);

                            CustomPoseDetector poseDetector = new CustomPoseDetector(imageView, bitmapImage, stream);
                            poseDetector.analyze(image);

                            Toast.makeText(context, "Picture Taken", Toast.LENGTH_SHORT).show();
                        } catch (Exception exception) {
                            exception.printStackTrace();
                            Toast.makeText(context, "Error saving photo: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
                        } finally {
                            context.findViewById(R.id.analyzeLayout).setVisibility(View.VISIBLE);
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        exception.printStackTrace();
                        Toast.makeText(context, "Error saving photo" + exception.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }


    private Executor getExecutor() {
        return ContextCompat.getMainExecutor(this.context);
    }

    /**
     * Converts the passed ImageProxy to a Bitmap image.
     * Source: https://stackoverflow.com/questions/56772967/converting-imageproxy-to-bitmap
     * @param image an ImageProxy
     * @return the corresponding Bitmap image
     */
    private Bitmap convertImageProxyToBitmap(ImageProxy image) {
        ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
        byteBuffer.rewind();
        byte[] bytes = new byte[byteBuffer.capacity()];
        byteBuffer.get(bytes);

        BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        Matrix matrix = new Matrix();
        matrix.postRotate(image.getImageInfo().getRotationDegrees());
        bitmap = Bitmap.createBitmap(bitmap, 0,0, image.getWidth(), image.getHeight(), matrix, false);

        return bitmap;
    }

    @Override
    public void analyze(@NonNull ImageProxy image) {

    }
}
