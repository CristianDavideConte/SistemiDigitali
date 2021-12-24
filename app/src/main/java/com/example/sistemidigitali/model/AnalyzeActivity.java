package com.example.sistemidigitali.model;

import static com.example.sistemidigitali.debugUtility.Debug.println;

import android.content.Intent;
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
        Intent intent = getIntent();
        this.imageUri = intent.getParcelableExtra(MainActivity.ACTIVITY_IMAGE);
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

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

            runOnUiThread(() -> this.analyzeView.setImageBitmap(bitmapImage));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


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
                            canvas.drawRect(new Rect(left,                   top,            left + linesWidth, bottom), p);           //Left line
                            canvas.drawRect(new Rect(left,               bottom - linesWidth, right,             bottom), p);           //Bottom line
                            canvas.drawRect(new Rect(right - linesWidth, top,                 right,             bottom), p);           //Right line

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