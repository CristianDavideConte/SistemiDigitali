package com.example.sistemidigitali.views;

import static com.example.sistemidigitali.debugUtility.Debug.println;

import android.annotation.SuppressLint;
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
import com.example.sistemidigitali.model.DetectionLine;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.vision.detector.Detection;

import java.util.ArrayList;
import java.util.List;

public class LiveDetectionView extends View {
    private static final float ROUNDING_RECTS_RADIUS = 70;
    private static final float STROKE_WIDTH = 10;

    private boolean allowUpdate;

    private List<Detection> detections;
    private List<DetectionLine> detectionsLines;
    private boolean flipNeeded;
    private Matrix flipperMatrix;
    private Matrix transformMatrix;

    private Paint boxPaint;

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

    public void init() {
        this.allowUpdate = true;

        this.detections = new ArrayList<>();
        this.detectionsLines = new ArrayList<>();
        this.flipNeeded = false;
        this.flipperMatrix = new Matrix();
        this.transformMatrix = new Matrix();
        this.boxPaint = new Paint();

        this.boxPaint.setStyle(Paint.Style.STROKE);
        this.boxPaint.setStrokeWidth(STROKE_WIDTH);
        this.boxPaint.setColor(Color.RED);
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void onUpdateDetectionsRects(UpdateDetectionsRectsEvent event) {
        if(this.getContext() != event.getContext()) return;
        this.detections = event.getDetections();
        this.detectionsLines = event.getDetectionLines();
        this.flipNeeded = event.isFlipNeeded();
        this.transformMatrix = event.getTransformMatrix();
        this.flipperMatrix.reset();
        this.invalidate();
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onAllowUpdatePolicyChange(AllowUpdatePolicyChangeEvent event) {
        if(this.getContext() != event.getContext()) return;
        this.allowUpdate = event.isAllowUpdatePolicyChange();
        this.invalidate();
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(!this.allowUpdate) return;

        this.detections.parallelStream().forEach((detection) -> {
            WearingModeEnum wearingModeEnum;
            try{
                String[] labelParts = detection.getCategories().get(0).getLabel().split("_");
                wearingModeEnum = WearingModeEnum.valueOf(labelParts[0]);
            } catch (IllegalArgumentException e) { //Test mode
                wearingModeEnum = WearingModeEnum.TEST;
            }

            RectF boundingBox = detection.getBoundingBox();

            //Flip on y-axis if necessary
            if(this.flipNeeded) {
                this.flipperMatrix.preTranslate(this.getWidth() - (boundingBox.right + boundingBox.left), 0);
                this.flipperMatrix.mapRect(boundingBox);
            }

            //Do extra translation/scaling if specified
            if(this.transformMatrix != null) this.transformMatrix.mapRect(boundingBox);

            this.boxPaint.setColor(wearingModeEnum.getBackgroundColor());
            canvas.drawRoundRect(boundingBox, ROUNDING_RECTS_RADIUS, ROUNDING_RECTS_RADIUS, boxPaint);
        });


        this.detectionsLines.parallelStream().forEach((line) -> {
            this.boxPaint.setColor(line.getLineColorType().getColor());
            canvas.drawLine(line.getStartX(), line.getStartY(), line.getEndX(), line.getEndY(), this.boxPaint);
        });
    }

    @SuppressLint("DefaultLocale")
    @Subscribe(threadMode = ThreadMode.ASYNC)
    public boolean onTap(MotionEvent motionEvent) {
        if(this.allowUpdate) {
            for(Detection detection : this.detections) {
                if(detection.getBoundingBox().contains(motionEvent.getX(), motionEvent.getY())) {
                    Category category = detection.getCategories().get(0);
                    String[] labelParts = category.getLabel().split("_");
                    String accuracy = String.format("%.2f", category.getScore() * 100) + "%";

                    WearingModeEnum wearingModeEnum;
                    String maskType;
                    try{
                        wearingModeEnum = WearingModeEnum.valueOf(labelParts[0]);
                        maskType = wearingModeEnum != WearingModeEnum.MRNW ? MaskTypeEnum.valueOf(labelParts[1]).getFullName() : "";
                    } catch (IllegalArgumentException e) { //Test mode
                        wearingModeEnum = WearingModeEnum.TEST;
                        maskType = WearingModeEnum.TEST.getFullName();
                    }
                    String wearingMode = wearingModeEnum.getFullName();

                    Intent intent = new Intent(this.getContext(), PopUpActivity.class);
                    intent.putExtra(PopUpActivity.POP_UP_TEXT_1, wearingMode);
                    intent.putExtra(PopUpActivity.POP_UP_TEXT_2, maskType);
                    intent.putExtra(PopUpActivity.POP_UP_TEXT_3, accuracy);

                    intent.putExtra(PopUpActivity.POP_UP_TEXT_COLOR, String.valueOf(wearingModeEnum.getTextColor()));
                    intent.putExtra(PopUpActivity.POP_UP_BACKGROUND_COLOR, String.valueOf(wearingModeEnum.getBackgroundColor()));

                    this.getContext().startActivity(intent);
                    return true;
                }
            }

            final float touchTollerance = 10 * STROKE_WIDTH;
            for(DetectionLine line : this.detectionsLines) {
                //The Y that the detection line should have at the motion event's X
                final float lineYatTouchX = (line.getEndY() - line.getStartY()) * (motionEvent.getX() - line.getStartX()) / (line.getEndX() - line.getStartX()) + line.getStartY();

                if(lineYatTouchX + touchTollerance >= motionEvent.getY() && lineYatTouchX - touchTollerance <= motionEvent.getY()) {
                    Intent intent = new Intent(this.getContext(), PopUpActivity.class);
                    intent.putExtra(PopUpActivity.POP_UP_TEXT_1, "Distance");
                    intent.putExtra(PopUpActivity.POP_UP_TEXT_2, "");
                    intent.putExtra(PopUpActivity.POP_UP_TEXT_3, line.getInfo());

                    intent.putExtra(PopUpActivity.POP_UP_TEXT_COLOR, String.valueOf(line.getTextColorType().getColor()));
                    intent.putExtra(PopUpActivity.POP_UP_BACKGROUND_COLOR, String.valueOf(line.getLineColorType().getColor()));

                    this.getContext().startActivity(intent);
                    return true;
                }
            }
        }
        return false;
    }
}