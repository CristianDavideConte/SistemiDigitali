package com.example.sistemidigitali.model;

import static com.example.sistemidigitali.debugUtility.Debug.println;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.graphics.Rect;
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
import org.tensorflow.lite.task.vision.detector.Detection;

import java.io.IOException;
import java.util.List;

public class AnalyzeActivity extends AppCompatActivity {

    private Uri imageUri;
    private Bitmap originalImage;

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
                    List<Detection> objs = this.objectDetector.detect(bitmapImage);

                    runOnUiThread(() -> {
                        Canvas canvas = new Canvas(bitmapImage);
                        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                        p.setTextSize(70);

                        println("DETECTED OBJS: " + objs.size());

                        for (Detection obj : objs) {
                            println("LABEL: " + obj.getCategories().get(0).getLabel());
                            p.setColor(Color.rgb(
                                (int) (Math.random() * 255),
                                (int) (Math.random() * 255),
                                (int) (Math.random() * 255)
                            ));

                            int top = (int) obj.getBoundingBox().top;
                            int right = (int) obj.getBoundingBox().right;
                            int bottom = (int) obj.getBoundingBox().bottom;
                            int left = (int) obj.getBoundingBox().left;
                            int linesWidth = 10;

                            canvas.drawRect(new Rect(left,                   top,                 right,      top + linesWidth), p); //Top line
                            canvas.drawRect(new Rect(left,                   top,            left + linesWidth, bottom),           p); //Left line
                            canvas.drawRect(new Rect(left,               bottom - linesWidth, right,             bottom),           p); //Bottom line
                            canvas.drawRect(new Rect(right - linesWidth, top,                 right,             bottom),           p); //Right line

                            canvas.drawText(obj.getCategories().get(0).getLabel(), 0.5f * (right + left),0.5f * (top  + bottom), p);
                        }

                        this.analyzeView.setImageResource(0);
                        this.analyzeView.draw(canvas);
                        this.analyzeView.setImageBitmap(bitmapImage);
                    });
                }
        ).start();
    }
}