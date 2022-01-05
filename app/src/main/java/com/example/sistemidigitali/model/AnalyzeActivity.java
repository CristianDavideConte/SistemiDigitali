package com.example.sistemidigitali.model;

import static com.example.sistemidigitali.debugUtility.Debug.println;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bogdwellers.pinchtozoom.ImageMatrixTouchHandler;
import com.example.sistemidigitali.MainActivity;
import com.example.sistemidigitali.R;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.task.vision.detector.Detection;

import java.io.IOException;
import java.util.List;

public class AnalyzeActivity extends AppCompatActivity {

    private Uri imageUri;
    private Bitmap originalImage;
    private TensorImage originalImageTensor;

    private ImageView analyzeView;
    private Button clearButton;
    private Button analyzeButton;
    private CustomObjectDetector objectDetector;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analyze);

        this.analyzeView = findViewById(R.id.analyzeView);
        this.clearButton = findViewById(R.id.buttonclearAnalysysButton);
        this.analyzeButton = findViewById(R.id.analyzeButton);

        //Get input informations
        this.imageUri = this.getIntent().getParcelableExtra(MainActivity.ACTIVITY_IMAGE);
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
    @Subscribe(sticky = true, threadMode = ThreadMode.ASYNC)
    public void onPictureUriAvailable(ImageSavedEvent event) {
        //If the picture is not available, go back to previous activity
        if(!event.getError().equals("success")) {
            runOnUiThread(() -> {
                Toast.makeText(this, event.getError(), Toast.LENGTH_SHORT).show();
                this.finish();
            });
            return;
        }

        try {
            ImageDecoder.Source source = ImageDecoder.createSource(this.getContentResolver(), this.imageUri);
            Bitmap bitmapImage = ImageDecoder.decodeBitmap(source);
            this.originalImage = bitmapImage.copy(Bitmap.Config.ARGB_8888, true);
            this.originalImageTensor = TensorImage.fromBitmap(originalImage);
            this.objectDetector = new CustomObjectDetector(this);

            this.clearButton.setOnClickListener((view) -> {
                this.originalImage = bitmapImage.copy(Bitmap.Config.ARGB_8888, true);
                this.analyzeView.setImageBitmap(this.originalImage);
            });
            this.analyzeButton.setOnClickListener((view) -> {
                this.originalImage = bitmapImage.copy(Bitmap.Config.ARGB_8888, true);
                this.detectObjects(this.originalImage);
            });

            runOnUiThread(() -> {
                this.analyzeView.setOnTouchListener(new ImageMatrixTouchHandler(this)); //Handles pitch-to-zoom on image views
                this.analyzeView.setImageBitmap(bitmapImage);
            });
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
                    List<Detection> objs = this.objectDetector.detect(this.originalImageTensor);
                    Canvas canvas = new Canvas(bitmapImage);
                    Paint boxPaint = new Paint();
                    Paint textPaint = new Paint();
                    boxPaint.setStyle(Paint.Style.STROKE);
                    boxPaint.setStrokeWidth(10);
                    textPaint.setTextSize(70);

                    runOnUiThread(() -> {
                        println("DETECTED OBJS: " + objs.size());

                        for (Detection obj : objs) {
                            println("LABEL: " + obj.getCategories().get(0).getLabel());
                            int color = Color.rgb(
                                    (int) (Math.random() * 255),
                                    (int) (Math.random() * 255),
                                    (int) (Math.random() * 255)
                            );
                            boxPaint.setColor(color);
                            textPaint.setColor(color);

                            RectF boundingBox = obj.getBoundingBox();
                            int top = (int) boundingBox.top;
                            int right = (int) boundingBox.right;
                            int bottom = (int) boundingBox.bottom;
                            int left = (int) boundingBox.left;

                            canvas.drawRect(left, top, right, bottom, boxPaint);
                            canvas.drawText(obj.getCategories().get(0).getLabel(), 0.5f * (right + left),0.5f * (top  + bottom), textPaint);
                        }

                        this.analyzeView.setImageResource(0);
                        this.analyzeView.draw(canvas);
                        this.analyzeView.setImageBitmap(bitmapImage);
                    });
                }
        ).start();
    }
}