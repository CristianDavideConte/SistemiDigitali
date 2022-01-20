package com.example.sistemidigitali.views;

import static com.example.sistemidigitali.debugUtility.Debug.println;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.example.sistemidigitali.customEvents.AllowUpdatePolicyChangeEvent;
import com.example.sistemidigitali.customEvents.UpdateDetectionsRectsEvent;
import com.example.sistemidigitali.enums.MaskTypeEnum;
import com.example.sistemidigitali.enums.WearingModeEnum;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.vision.detector.Detection;

import java.util.ArrayList;
import java.util.List;

public class LiveDetectionView extends View {
    private float ROUNDING_RECTS_RADIUS = 70;
    private float MAX_FONT_SIZE = 50F;
    private float CANVAS_CENTER_DEFAULT_VALUE = 0.0F;

    private boolean allowUpdate;

    private List<Detection> detections;
    private float canvasCenter;
    private boolean flipNeeded;
    private Matrix transformMatrix;

    private Paint boxPaint;
    private Paint textPaint;

    private long lastInvocationTime;
    private float currentFps;

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

    public boolean isEnabled() {
        return !this.detections.isEmpty();
    }

    public void setAllowUpdate(boolean allowUpdate) {
        this.allowUpdate = allowUpdate;
    }

    public void init() {
        this.allowUpdate = true;

        this.detections = new ArrayList<>();
        this.canvasCenter = CANVAS_CENTER_DEFAULT_VALUE;
        this.flipNeeded = false;
        this.transformMatrix = new Matrix();
        this.boxPaint = new Paint();
        this.textPaint = new Paint();

        this.boxPaint.setStyle(Paint.Style.STROKE);
        this.textPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        this.boxPaint.setStrokeWidth(10);
        this.textPaint.setStrokeWidth(2F);
        this.textPaint.setTextSize(MAX_FONT_SIZE);
        this.boxPaint.setColor(Color.RED);
        this.textPaint.setColor(Color.GREEN);

        this.lastInvocationTime = System.currentTimeMillis();
        this.currentFps = 0;
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void onUpdateDetectionsRects(UpdateDetectionsRectsEvent event) {
        this.detections = event.getDetections();
        this.flipNeeded = event.isFlipNeeded();
        this.transformMatrix = event.getTransformMatrix();
        this.invalidate();
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.ASYNC)
    public void onAllowUpdatePolicyChange(AllowUpdatePolicyChangeEvent event) {
        this.allowUpdate = event.isAllowUpdatePolicyChange();
        this.invalidate();
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(!this.allowUpdate) return;
        this.canvasCenter = this.getWidth() / 2.0F;

        this.detections.parallelStream().forEach((detection) -> {
            String labelParts [] = detection.getCategories().get(0).getLabel().split("_");
            WearingModeEnum wearingModeEnum = WearingModeEnum.valueOf(labelParts[0]);

            RectF boundingBox = detection.getBoundingBox();

            //Flip on y-axis if necessary
            if(this.flipNeeded) {
                Matrix matrix = new Matrix();
                matrix.preTranslate(2 * this.canvasCenter - (boundingBox.right + boundingBox.left), 0);
                matrix.mapRect(boundingBox);
            }

            //Do extra translation/scaling if specified
            if(this.transformMatrix != null) this.transformMatrix.mapRect(boundingBox);

            this.boxPaint.setColor(wearingModeEnum.getBackgroundColor());
            canvas.drawRoundRect(boundingBox, ROUNDING_RECTS_RADIUS, ROUNDING_RECTS_RADIUS, boxPaint);
        });

        long currentTime = System.currentTimeMillis();
        if(currentTime - this.lastInvocationTime < 1000) {
            this.currentFps++;
        } else {
            println(this.currentFps);
            this.currentFps = 0;
            this.lastInvocationTime = currentTime;
        }
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    public boolean onTap(MotionEvent motionEvent) {
        if(!this.allowUpdate) return false;
        for(Detection detection : this.detections) {
            if(detection.getBoundingBox().contains(motionEvent.getX(), motionEvent.getY())) {
                Category category = detection.getCategories().get(0);
                String labelParts [] = category.getLabel().split("_");
                WearingModeEnum wearingModeEnum = WearingModeEnum.valueOf(labelParts[0]);

                String wearingMode = wearingModeEnum.getFullName();
                String maskType = wearingModeEnum != WearingModeEnum.MRNW ? MaskTypeEnum.valueOf(labelParts[1]).getFullName() : "";
                String accuracy = String.format("%.2f", category.getScore() * 100) + "%";

                Intent intent = new Intent(this.getContext(), PopUpActivity.class);
                intent.putExtra(PopUpActivity.POP_UP_TEXT_1, wearingMode);
                intent.putExtra(PopUpActivity.POP_UP_TEXT_2, maskType);
                intent.putExtra(PopUpActivity.POP_UP_TEXT_3, accuracy);
                intent.putExtra(PopUpActivity.POP_UP_BACKGROUND_COLOR, String.valueOf(wearingModeEnum.getBackgroundColor()));
                intent.putExtra(PopUpActivity.POP_UP_TEXT_COLOR, String.valueOf(wearingModeEnum.getTextColor()));
                this.getContext().startActivity(intent);
                return true;
            }
        }
        return false;
    }
}
