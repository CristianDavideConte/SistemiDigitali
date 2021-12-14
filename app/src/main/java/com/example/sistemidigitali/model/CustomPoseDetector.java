package com.example.sistemidigitali.model;

import static com.example.sistemidigitali.debugUtility.Debug.println;

import com.example.sistemidigitali.debugUtility.Debug.*;
import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.drawable.BitmapDrawable;
import android.media.Image;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.PoseLandmark;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CustomPoseDetector implements ImageAnalysis.Analyzer {
    private PoseDetector detector; //Google's pose detector
    private Pose detectionResult;
    private ImageView imageView;
    private Bitmap bitmapImage;
    private OutputStream stream;

    public CustomPoseDetector(ImageView imageView, Bitmap bitmapImage, OutputStream stream) {
        PoseDetectorOptions options =
                new PoseDetectorOptions.Builder()
                        .setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE)
                        .build();
        this.detector = PoseDetection.getClient(options);
        this.imageView = imageView;
        this.bitmapImage = bitmapImage;
        this.stream = stream;
    }

    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        try {
            @SuppressLint("UnsafeOptInUsageError") Image mediaImage = imageProxy.getImage();
            if (mediaImage != null) {
                InputImage image =
                        InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

                Task<Pose> result =
                        detector.process(image)
                                .addOnSuccessListener(
                                        new OnSuccessListener<Pose>() {
                                            @Override
                                            public void onSuccess(Pose pose) {
                                                println("LANDMARKS: " + pose.getAllPoseLandmarks().size());
                                                println("BOCCA SX: " + pose.getPoseLandmark(PoseLandmark.LEFT_MOUTH).getInFrameLikelihood());
                                                println("BOCCA DX: " + pose.getPoseLandmark(PoseLandmark.RIGHT_MOUTH).getInFrameLikelihood());
                                                println("MANO SX: " + pose.getPoseLandmark(PoseLandmark.LEFT_WRIST).getInFrameLikelihood());
                                                println("MANO DX: " + pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST).getInFrameLikelihood());
                                                println("OCCHIO DX: " + pose.getPoseLandmark(PoseLandmark.RIGHT_EYE).getInFrameLikelihood());

                                                PointF leftMouth = pose.getPoseLandmark(PoseLandmark.LEFT_MOUTH).getPosition();
                                                PointF rightMouth = pose.getPoseLandmark(PoseLandmark.RIGHT_MOUTH).getPosition();


                                                imageView.setWillNotDraw(false);
                                                Canvas canvas = new Canvas(bitmapImage);
                                                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                                                p.setColor(Color.BLUE);

                                                for(PoseLandmark mark : pose.getAllPoseLandmarks()) {
                                                    p.setColor(Color.rgb((int)(255 * Math.random()),
                                                                         (int)(255 * Math.random()),
                                                                         (int)(255 * Math.random())));
                                                    canvas.drawCircle(mark.getPosition().x, mark.getPosition().y, 10, p);
                                                }

                                                imageView.setImageBitmap(bitmapImage);
                                                if(!((BitmapDrawable)imageView.getDrawable()).getBitmap().compress(Bitmap.CompressFormat.JPEG, 100, stream)){
                                                    println("ERROR SAVING STUFF");
                                                }

                                                imageProxy.close();
                                                try {
                                                    stream.close();
                                                } catch (IOException e) {
                                                    e.printStackTrace();
                                                }
                                            }
                                        })
                                .addOnFailureListener(
                                        new OnFailureListener() {
                                            @Override
                                            public void onFailure(@NonNull Exception e) {
                                                println("POSE DETECTION FAILED");
                                                e.printStackTrace();
                                            }
                                        });
            } else throw new Exception("NOT IF");
        } catch (Exception e) {
            println("ANALYZE FAILED");
            e.printStackTrace();
        }
    }

    public Pose getDetectionResult() throws InterruptedException {
        final Lock lock = new ReentrantLock();
        Thread thread = new Thread(() -> {
            lock.lock();
            while(this.detectionResult == null);
            lock.unlock();
        });
        thread.start();

        Thread.sleep(1000);
        lock.lock();
        lock.unlock();
        return this.detectionResult;
    }
}
