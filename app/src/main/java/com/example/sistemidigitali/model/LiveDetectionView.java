package com.example.sistemidigitali.model;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import org.tensorflow.lite.task.vision.detector.Detection;

import java.util.ArrayList;
import java.util.List;

public class LiveDetectionView extends View {
    private float MAX_FONT_SIZE = 96F;

    private List<Detection> detections;
    private float rectsWidth;
    private float rectsHeight;

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

    public void setDetections(List<Detection> detections, float rectsWidth, float rectsHeight) {
        this.detections = detections;
        this.rectsWidth = rectsWidth;
        this.rectsHeight = rectsHeight;
    }

    public void init() {
        this.detections = new ArrayList<>();
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

        this.detections.parallelStream().forEach((obj) -> {
            RectF boundingBox = obj.getBoundingBox();

            //Scale the bounding rectangles if necessary
            Matrix matrix = new Matrix();
            matrix.preScale(scaleX, scaleY);
            matrix.mapRect(boundingBox);

            canvas.drawRect(boundingBox, boxPaint);
            //Calculates the right font size
            RectF tagSize = new RectF(0, 0, 0, 0);
            String text = obj.getCategories().get(0).getLabel();
            this.textPaint.setTextSize(MAX_FONT_SIZE);
            float fontSize = this.textPaint.getTextSize() * boundingBox.width() / tagSize.width();

            //Adjusts the font size so texts are inside the bounding box
            if (fontSize < this.textPaint.getTextSize()) this.textPaint.setTextSize(fontSize);

            float margin = (boundingBox.width() - tagSize.width()) / 2.0F;
            if (margin < 0F) margin = 0F;
            canvas.drawText(
                    text, boundingBox.left + margin,
                    boundingBox.top + tagSize.height() * 1F, this.textPaint
            );
        });
    }
}
