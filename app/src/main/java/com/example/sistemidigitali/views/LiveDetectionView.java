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
import com.example.sistemidigitali.customEvents.ClearSelectedDetectionEvent;
import com.example.sistemidigitali.customEvents.UpdateDetectionsRectsEvent;
import com.example.sistemidigitali.enums.MaskTypeEnum;
import com.example.sistemidigitali.enums.WearingModeEnum;
import com.example.sistemidigitali.model.CustomVibrator;
import com.example.sistemidigitali.model.DetectionLine;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.vision.detector.Detection;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LiveDetectionView extends View {
    private float MIN_ACCURACY_FOR_DETECTION_DISPLAY = 0.3F; //By default, 30% of minimum precision is needed to show a detection

    private float ROUNDING_RECTS_RADIUS = 70;
    private float STROKE_WIDTH = 10;
    private float SELECTED_STROKE_WIDTH = 25;


    private boolean allowUpdate;

    private static List<Detection> selectedDetections;
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

        if(LiveDetectionView.selectedDetections == null) LiveDetectionView.selectedDetections = new ArrayList<>();
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

    public void adjustSelectedStrokeWidth(float width) {
        final float standardResBig   = 3494F;       //x1
        final float standardResSmall = 1000F;       //x2
        final float standardStrokeWidthBig   = 25F; //y1
        final float standardStrokeWidthSmall = 8F;  //y2

        STROKE_WIDTH = Math.max(1, (width - standardResBig) / (standardResSmall - standardResBig) * (standardStrokeWidthSmall - standardStrokeWidthBig) + standardStrokeWidthBig);
        ROUNDING_RECTS_RADIUS = STROKE_WIDTH * 5;
        SELECTED_STROKE_WIDTH = STROKE_WIDTH * 2F;
    }

    public List<Detection> getSelectedDetections() {
        return LiveDetectionView.selectedDetections;
    }
    public void setMinimumAccuracyForDetectionsDisplay(float minimumAccuracyForDetectionsDisplay) {
        this.MIN_ACCURACY_FOR_DETECTION_DISPLAY = minimumAccuracyForDetectionsDisplay;
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.ASYNC)
    public void onClearSelectedDetection(ClearSelectedDetectionEvent event) {
        LiveDetectionView.selectedDetections.clear();
        EventBus.getDefault().removeStickyEvent(event);
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
            if(detection.getCategories().get(0).getScore() < MIN_ACCURACY_FOR_DETECTION_DISPLAY) continue;

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
            for(Detection selectedDetection : LiveDetectionView.selectedDetections) {
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
            final float initialStrokeWidth = line.getStartLineSize() * 0.5F;
            final float finalStrokeWidth = line.getEndLineSize() * 0.5F;

            final int startFixSign, endFixSign;
            if(line.getStartX() > line.getEndX()) {
                startFixSign = -1;
                endFixSign = +1;
            } else {
                startFixSign = +1;
                endFixSign = -1;
            }
            this.detectionLinesPath.reset();
            this.detectionLinesPath.moveTo(line.getStartX() + SELECTED_STROKE_WIDTH * startFixSign, line.getStartY() - initialStrokeWidth);
            this.detectionLinesPath.lineTo(line.getStartX() + SELECTED_STROKE_WIDTH * startFixSign, line.getStartY() + initialStrokeWidth);
            this.detectionLinesPath.lineTo(line.getEndX() + SELECTED_STROKE_WIDTH * endFixSign, line.getEndY() + finalStrokeWidth);
            this.detectionLinesPath.lineTo(line.getEndX() + SELECTED_STROKE_WIDTH * endFixSign, line.getEndY() - finalStrokeWidth);
            this.detectionLinesPath.lineTo(line.getStartX() + SELECTED_STROKE_WIDTH * startFixSign, line.getStartY() - initialStrokeWidth);

            this.boxPaint.setColor(line.getLineColor());
            canvas.drawPath(this.detectionLinesPath, this.boxPaint);
        }
    }

    public int onHold(MotionEvent motionEvent) {
        final Optional<Detection> detectionOptional = this.getDetectionAtPoint(motionEvent.getX(), motionEvent.getY());
        if(detectionOptional.isPresent()) {
            final Detection detection = detectionOptional.get();
            if(LiveDetectionView.selectedDetections.remove(detection)) {
                this.detectionsLines.clear();
                this.customVibrator.vibrateLight();
            } else {
                if(LiveDetectionView.selectedDetections.size() < 2) {
                    LiveDetectionView.selectedDetections.add(detection);
                    this.customVibrator.vibrateHeavy();
                }
                else return LiveDetectionView.selectedDetections.size();
            }
            this.invalidate();
        }
        return LiveDetectionView.selectedDetections.size();
    }

    @SuppressLint("DefaultLocale")
    @Subscribe(threadMode = ThreadMode.ASYNC)
    public boolean onTap(MotionEvent motionEvent) {
        if(this.allowUpdate) {
            final float touchToleranceY = 2.3F * SELECTED_STROKE_WIDTH;
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
            if(detection.getCategories().get(0).getScore() < MIN_ACCURACY_FOR_DETECTION_DISPLAY) continue;
            if (detection.getBoundingBox().contains(x, y)) {
                return Optional.of(detection);
            }
        }
        return Optional.empty();
    }
}