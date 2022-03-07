package com.example.sistemidigitali.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.example.sistemidigitali.customEvents.AllowUpdatePolicyChangeEvent;
import com.example.sistemidigitali.customEvents.UpdateDetectionsRectsEvent;
import com.example.sistemidigitali.enums.MaskTypeEnum;
import com.example.sistemidigitali.enums.WearingModeEnum;
import com.example.sistemidigitali.model.CustomVibrator;
import com.example.sistemidigitali.model.DetectionLine;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.vision.detector.Detection;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LiveDetectionView extends View {
    private static final float ROUNDING_RECTS_RADIUS = 70;
    public static final float STROKE_WIDTH = 10;
    public static final float SELECTED_STROKE_WIDTH = 25;

    private boolean allowUpdate;

    private List<Detection> selectedDetections;
    private List<Detection> detections;
    private List<DetectionLine> detectionsLines;
    private boolean flipNeeded;
    private Matrix flipperMatrix;
    private Matrix transformMatrix;

    private Path detectionLinesPath;
    private Paint boxPaint;

    private CustomVibrator customVibrator;

    public LiveDetectionView(Context context) {
        super(context);
        init(context);
    }

    public LiveDetectionView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public LiveDetectionView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    public void init(Context context) {
        this.allowUpdate = true;

        this.selectedDetections = new ArrayList<>();
        this.detections = new ArrayList<>();
        this.detectionsLines = new ArrayList<>();

        this.flipNeeded = false;
        this.flipperMatrix = new Matrix();
        this.transformMatrix = new Matrix();

        this.detectionLinesPath = new Path();
        this.boxPaint = new Paint();

        this.boxPaint.setStyle(Paint.Style.STROKE);
        this.boxPaint.setStrokeWidth(STROKE_WIDTH);
        this.boxPaint.setColor(Color.RED);

        this.customVibrator = new CustomVibrator(context);
    }

    public List<Detection> getSelectedDetections() {
        return selectedDetections;
    }
    public void setSelectedDetections(List<Detection> selectedDetections) {
        this.selectedDetections = selectedDetections;
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

        //Draws all the detections' rectangles
        this.boxPaint.setStyle(Paint.Style.STROKE);
        for(Detection detection : this.detections) {
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

            boolean isSelected = false;
            for(Detection selectedDetection : this.selectedDetections) {
                if(selectedDetection.equals(detection)) {
                    this.boxPaint.setStrokeWidth(SELECTED_STROKE_WIDTH);
                    isSelected = true;
                }
            }
            if(!isSelected) this.boxPaint.setStrokeWidth(STROKE_WIDTH);

            this.boxPaint.setColor(wearingModeEnum.getBackgroundColor());
            canvas.drawRoundRect(boundingBox, ROUNDING_RECTS_RADIUS, ROUNDING_RECTS_RADIUS, boxPaint);
        }

        //Draws the connecting lines of the detections' rectangles
        this.boxPaint.setStyle(Paint.Style.FILL);
        for(DetectionLine line : this.detectionsLines) {
            final float initialStrokeWidth = STROKE_WIDTH / 2 * line.getStartLineWidthMultiplier();
            final float finalStrokeWidth = STROKE_WIDTH / 2 * line.getEndLineWidthMultiplier();
            final int startFixSign, endFixSign;
            if(line.getStartX() > line.getEndX()) {
                startFixSign = -1;
                endFixSign = +1;
            } else {
                startFixSign = +1;
                endFixSign = -1;
            }

            this.detectionLinesPath.reset();
            this.detectionLinesPath.moveTo(line.getStartX() + STROKE_WIDTH * startFixSign, line.getStartY() - initialStrokeWidth);
            this.detectionLinesPath.lineTo(line.getStartX() + STROKE_WIDTH * startFixSign, line.getStartY() + initialStrokeWidth);
            this.detectionLinesPath.lineTo(line.getEndX() + STROKE_WIDTH * endFixSign, line.getEndY() + finalStrokeWidth);
            this.detectionLinesPath.lineTo(line.getEndX() + STROKE_WIDTH * endFixSign, line.getEndY() - finalStrokeWidth);
            this.detectionLinesPath.lineTo(line.getStartX() + STROKE_WIDTH * startFixSign, line.getStartY() - initialStrokeWidth);

            this.boxPaint.setColor(line.getLineColor());
            canvas.drawPath(this.detectionLinesPath, this.boxPaint);
        }
    }

    public int onHold(MotionEvent motionEvent) {
        final Optional<Detection> detectionOptional = this.getDetectionAtPoint(motionEvent.getX(), motionEvent.getY());
        if(detectionOptional.isPresent()) {
            final Detection detection = detectionOptional.get();
            if(this.selectedDetections.remove(detection)) {
                this.detectionsLines.clear();
                this.customVibrator.vibrateLight();
            } else {
                if(this.selectedDetections.size() < 2) {
                    this.selectedDetections.add(detection);
                    this.customVibrator.vibrateHeavy();
                }
                else return this.selectedDetections.size();
            }
            this.invalidate();
        }
        return this.selectedDetections.size();
    }

    @SuppressLint("DefaultLocale")
    @Subscribe(threadMode = ThreadMode.ASYNC)
    public boolean onTap(MotionEvent motionEvent) {
        if(this.allowUpdate) {
            final float touchToleranceY = 5 * STROKE_WIDTH;
            for(DetectionLine line : this.detectionsLines) {
                //The Y that the detection line should have at the motion event's X
                final float lineYatTouchX = (line.getEndY() - line.getStartY()) * (motionEvent.getX() - line.getStartX()) / (line.getEndX() - line.getStartX()) + line.getStartY();

                if(lineYatTouchX - touchToleranceY <= motionEvent.getY() &&
                        lineYatTouchX + touchToleranceY >= motionEvent.getY() &&
                        Math.min(line.getStartX(), line.getEndX()) <= motionEvent.getX() &&
                        Math.max(line.getStartX(), line.getEndX()) >= motionEvent.getX())
                {
                    Intent intent = new Intent(this.getContext(), PopUpActivity.class);
                    intent.putExtra(PopUpActivity.POP_UP_TEXT_1, "Distance");
                    intent.putExtra(PopUpActivity.POP_UP_TEXT_2, "");
                    intent.putExtra(PopUpActivity.POP_UP_TEXT_3, line.getInfo());

                    intent.putExtra(PopUpActivity.POP_UP_TEXT_COLOR, String.valueOf(line.getTextColor()));
                    intent.putExtra(PopUpActivity.POP_UP_BACKGROUND_COLOR, String.valueOf(line.getLineColor()));

                    this.customVibrator.vibrateMedium();
                    this.getContext().startActivity(intent);
                    return true;
                }
            }

            final Optional<Detection> detectionOptional = this.getDetectionAtPoint(motionEvent.getX(), motionEvent.getY());
            if(detectionOptional.isPresent()) {
                final Detection detection = detectionOptional.get();
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

                    this.customVibrator.vibrateMedium();
                    this.getContext().startActivity(intent);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if there's is a detection's rect at the given coordinates.
     * Returns an Optional containing the detection if the condition above is true, Optional.empty is returned otherwise.
     * @param x The x coordinate of the point that should be checked.
     * @param y The y coordinate of the point that should be checked.
     * @return Optional containing the detection if there's any at the specified coordinates, Optional.empty otherwise.
     */
    private Optional<Detection> getDetectionAtPoint(float x, float y) {
        for(Detection detection : this.detections) {
            if (detection.getBoundingBox().contains(x, y)) {
                return Optional.of(detection);
            }
        }
        return Optional.empty();
    }
}