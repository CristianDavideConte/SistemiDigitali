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

import androidx.appcompat.app.AppCompatActivity;

import com.example.sistemidigitali.MainActivity;
import com.example.sistemidigitali.R;

import org.tensorflow.lite.task.vision.detector.Detection;

import java.io.IOException;
import java.util.List;

public class AnalyzeActivity extends AppCompatActivity {

    Bitmap originalImage;

    ImageView analyzeView;
    Button clearButton;
    Button analyzeButton;
    CustomObjectDetector objectDetector;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analyze);

        this.analyzeView = findViewById(R.id.analyzeView);
        this.clearButton = findViewById(R.id.buttonclearAnalysysButton);
        this.analyzeButton = findViewById(R.id.analyzeButton);

        //Get input informations
        Intent intent = getIntent();
        Uri imageUri = intent.getParcelableExtra(MainActivity.ACTIVITY_IMAGE);

        ImageDecoder.Source source = ImageDecoder.createSource(this.getContentResolver(), imageUri);
        new Thread(
                () -> {
                    try {
                        Bitmap bitmapImage = ImageDecoder.decodeBitmap(source);
                        runOnUiThread(() -> {
                            try {
                                this.originalImage = bitmapImage.copy(Bitmap.Config.ARGB_8888, true);
                                objectDetector = new CustomObjectDetector(this);

                                analyzeView.setImageBitmap(bitmapImage);
                                clearButton.setOnClickListener((view) -> {
                                    this.originalImage = bitmapImage.copy(Bitmap.Config.ARGB_8888, true);
                                    this.analyzeView.setImageBitmap(this.originalImage);
                                });
                                analyzeButton.setOnClickListener((view) -> {
                                    this.originalImage = bitmapImage.copy(Bitmap.Config.ARGB_8888, true);
                                    this.detectObjects(this.originalImage);
                                });
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
        ).start();
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