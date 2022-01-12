package com.example.sistemidigitali.model;

import static com.example.sistemidigitali.debugUtility.Debug.println;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.vision.detector.Detection;

import java.util.ArrayList;
import java.util.List;

public class LiveDetectionView extends View {
    private float MAX_FONT_SIZE = 70F;
    private float CANVAS_CENTER_DEFAULT_VALUE = -1.0F;

    private List<Detection> detections;
    private float rectsWidth;
    private float rectsHeight;
    private float canvasCenter;
    private boolean flipNeeded;

    private Paint boxPaint;
    private Paint textPaint;

    public LiveDetectionView(Context context) {
        super(context);
        init();
    }

    public LiveDetectionView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LiveDetectionView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public void setDetections(List<Detection> detections, float rectsWidth, float rectsHeight, boolean flipNeeded) {
        this.detections = detections;
        this.rectsWidth = rectsWidth;
        this.rectsHeight = rectsHeight;
        this.flipNeeded = flipNeeded;
    }

    public void init() {
        this.detections = new ArrayList<>();
        this.canvasCenter = CANVAS_CENTER_DEFAULT_VALUE;
        this.flipNeeded = false;
        this.boxPaint = new Paint();
        this.textPaint = new Paint();

        this.boxPaint.setStyle(Paint.Style.STROKE);
        this.textPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        this.boxPaint.setStrokeWidth(10);
        this.textPaint.setStrokeWidth(2F);
        this.textPaint.setTextSize(70);
        this.boxPaint.setColor(Color.RED);
        this.textPaint.setColor(Color.GREEN);
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scaleX = canvas.getWidth() / this.rectsWidth;
        float scaleY = canvas.getHeight() / this.rectsHeight;
        if(this.canvasCenter == CANVAS_CENTER_DEFAULT_VALUE) this.canvasCenter = canvas.getWidth() / 2;

        println(canvas.getWidth() + " " + canvas.getHeight());
        this.detections.parallelStream().forEach((obj) -> {
            RectF boundingBox = obj.getBoundingBox();

            //Scale the bounding rectangles and flip on y-axis if necessary
            Matrix matrix = new Matrix();
            matrix.preScale(scaleX, scaleY);
            matrix.mapRect(boundingBox);
            if(this.flipNeeded) {
                float left = 2 * this.canvasCenter - boundingBox.right;
                boundingBox.set(left, boundingBox.top, left + boundingBox.width(), boundingBox.bottom);
            }

            canvas.drawRect(boundingBox, boxPaint);
            Category category = obj.getCategories().get(0);
            String accuracy = String.format("%.2f", category.getScore() * 100);
            String label = category.getLabel();

            this.textPaint.setTextSize(MAX_FONT_SIZE);
            canvas.drawText(
                     accuracy + "% " + label, boundingBox.left,
                    boundingBox.top, this.textPaint
            );
        });
    }
}
