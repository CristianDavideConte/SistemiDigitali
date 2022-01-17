package com.example.sistemidigitali.views;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bogdwellers.pinchtozoom.ImageMatrixTouchHandler;
import com.example.sistemidigitali.customEvents.UpdateDetectionsRectsEvent;
import com.example.sistemidigitali.model.CustomObjectDetector;
import com.example.sistemidigitali.R;
import com.google.android.material.chip.Chip;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.vision.detector.Detection;

import java.io.IOException;
import java.util.List;

import com.example.sistemidigitali.customEvents.ImageSavedEvent;

public class AnalyzeActivity extends AppCompatActivity {
    private float MAX_FONT_SIZE = 70F;

    private Uri imageUri;
    private Bitmap originalImage;
    private TensorImage originalImageTensor;

    private ImageView analyzeView;
    private LiveDetectionView liveDetectionViewAnalyze;
    private Chip analyzeButton;
    private CustomObjectDetector objectDetector;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analyze);

        this.analyzeView = findViewById(R.id.analyzeView);
        this.liveDetectionViewAnalyze = findViewById(R.id.liveDetectionViewAnalyze);
        this.analyzeButton = findViewById(R.id.analyzeButton);
    }

    /**
     * Registers this instance of AnalyzeActivity on the EventBus,
     * so that it can receive async messages from other activities.
     */
    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    /**
     * Unregisters this instance of AnalyzeActivity on the EventBus,
     * so that it can no longer receive async messages from other activities.
     */
    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    /**
     * Function that is invoked by the EventBus (that's why it's public)
     * whenever a ImageSavedEvent is published by other activities.
     * It is used to asynchronously load an image into the
     * preview view of this AnalyzeActivity.
     * @param event An ImageSavedEvent that contains the result of the image saving operation.
     */
    @SuppressLint("ClickableViewAccessibility")
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onPictureUriAvailable(ImageSavedEvent event) {
        //If the picture is not available, go back to previous activity
        if(!event.getError().equals("success")) {
            Toast.makeText(this, event.getError(), Toast.LENGTH_SHORT).show();
            this.finish();
            return;
        }

        try {
            this.imageUri = event.getUri();
            ImageDecoder.Source source = ImageDecoder.createSource(this.getContentResolver(), this.imageUri);
            Bitmap bitmapImage = ImageDecoder.decodeBitmap(source);
            this.originalImage = bitmapImage.copy(Bitmap.Config.ARGB_8888, true);
            this.originalImageTensor = TensorImage.fromBitmap(originalImage);
            this.objectDetector = new CustomObjectDetector(this);

            try {
                this.analyzeButton.setOnCheckedChangeListener((view, isChecked) -> {
                    this.originalImage = bitmapImage.copy(Bitmap.Config.ARGB_8888, true);
                    if(isChecked) {
                        this.analyzeButton.setCheckable(false);
                        this.analyzeButton.setText("Clear");
                        this.detectObjects(this.originalImage);
                        //this.detectObjects();
                    } else {
                        this.analyzeButton.setText("Analyze");
                        this.analyzeView.setImageBitmap(this.originalImage);
                        /*
                        this.liveDetectionViewAnalyze.setDetections(new ArrayList<>(), 1, 1, false);
                        this.liveDetectionViewAnalyze.invalidate();
                        */
                    }
                });
            } catch (Exception e) {
                this.analyzeButton.setCheckable(false);
                this.analyzeButton.setTextColor(Color.WHITE);
                int[][] states = new int[][] {
                        new int[] { android.R.attr.state_enabled}, // enabled
                        new int[] {-android.R.attr.state_enabled}, // disabled
                        new int[] {-android.R.attr.state_checked}, // unchecked
                        new int[] { android.R.attr.state_pressed}  // pressed
                };

                int[] colors = new int[] { Color.RED, Color.RED, Color.RED, Color.RED };
                this.analyzeButton.setChipBackgroundColor(new ColorStateList(states, colors));
            }

            this.analyzeView.setOnTouchListener(new ImageMatrixTouchHandler(this)); //Handles pitch-to-zoom on image views
            this.analyzeView.setImageBitmap(bitmapImage);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Uses this AnalyzeActivity's objectDetector to analyze the given Bitmap image and
     * draws a rectangle around each detected object labeling it.
     * @param bitmapImage The Bitmap image to analyze.
     */
    private void detectObjects(Bitmap bitmapImage) {
        new Thread(
                () -> {
                    List<Detection> detections = this.objectDetector.detect(this.originalImageTensor);
                    Canvas canvas = new Canvas(bitmapImage);
                    Paint boxPaint = new Paint();
                    Paint textPaint = new Paint();
                    boxPaint.setStyle(Paint.Style.STROKE);
                    textPaint.setStyle(Paint.Style.FILL_AND_STROKE);
                    boxPaint.setStrokeWidth(10);
                    textPaint.setStrokeWidth(2F);
                    textPaint.setTextSize(MAX_FONT_SIZE);
                    boxPaint.setColor(Color.RED);
                    textPaint.setColor(Color.GREEN);

                    runOnUiThread(() -> {
                        detections.parallelStream().forEach((obj) -> {
                            RectF boundingBox = obj.getBoundingBox();

                            canvas.drawRect(boundingBox, boxPaint);
                            Category category = obj.getCategories().get(0);
                            String accuracy = String.format("%.2f", category.getScore() * 100);
                            String label = category.getLabel();

                            canvas.drawText(accuracy + "% " + label, boundingBox.left, boundingBox.top, textPaint);
                        });

                        this.analyzeView.setImageResource(0);
                        this.analyzeView.draw(canvas);
                        this.analyzeView.setImageBitmap(bitmapImage);
                        this.analyzeButton.setCheckable(true);
                    });
                }
        ).start();
    }


    private void detectObjects2() {
        new Thread(
                () -> {
                    float scaleX = this.originalImage.getWidth() / this.analyzeView.getWidth();
                    float scaleY = this.originalImage.getHeight() / this.analyzeView.getHeight();

                    List<Detection> detections = this.objectDetector.detect(this.originalImageTensor);
                    EventBus.getDefault().postSticky(new UpdateDetectionsRectsEvent(detections, this.originalImage.getWidth(), this.originalImage.getHeight(), false));
                    this.analyzeButton.setCheckable(true);
                }
        ).start();
    }
}