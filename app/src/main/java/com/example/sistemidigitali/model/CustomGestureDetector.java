package com.example.sistemidigitali.model;

import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;

import com.example.sistemidigitali.customEvents.EndOfGestureEvent;
import com.example.sistemidigitali.customEvents.GestureIsMoveEvent;
import com.example.sistemidigitali.customEvents.GestureIsZoomEvent;
import com.example.sistemidigitali.customEvents.OverlayVisibilityChangeEvent;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class CustomGestureDetector {
    private final static int MAX_MOVE_POINTS_FOR_MOVE_TRIGGER = 4;
    private final static long MAX_DOUBLE_TAP_DURATION_FOR_ZOOM_TRIGGER = 450; //in ms

    private boolean gestureIsZoom;
    private boolean gestureIsMove;
    private boolean listenToTouchEvents;

    private int gestureMovePointsCounter;
    private long lastTapTime;

    public CustomGestureDetector() {
        this.gestureIsMove = false;
        this.gestureIsZoom = false;
        this.listenToTouchEvents = true;
        this.gestureMovePointsCounter = 0;
        this.lastTapTime = System.currentTimeMillis();
    }

    public void update(MotionEvent event) {
        if(!this.listenToTouchEvents) return;

        final int action = event.getAction();
        final long currentDoubleTapDuration = System.currentTimeMillis() - this.lastTapTime;

        final boolean endOfGesture  = action == MotionEvent.ACTION_UP;
        final boolean gestureIsMove = action == MotionEvent.ACTION_MOVE || action == MotionEvent.EDGE_LEFT || action == MotionEvent.EDGE_RIGHT;
        final boolean gestureIsTap  = !this.gestureIsMove && endOfGesture && event.getPointerCount() < 2;
        final boolean gestureIsZoom = event.getPointerCount() > 1 || gestureIsTap && currentDoubleTapDuration < MAX_DOUBLE_TAP_DURATION_FOR_ZOOM_TRIGGER;

        if(gestureIsTap) {
            this.lastTapTime = System.currentTimeMillis();
        }

        if(gestureIsMove) {
            this.gestureMovePointsCounter++;
            if(this.gestureMovePointsCounter == MAX_MOVE_POINTS_FOR_MOVE_TRIGGER) {
                this.gestureIsMove = true;
                EventBus.getDefault().post(new GestureIsMoveEvent());
            }
        }

        if(gestureIsZoom) {
            this.gestureIsZoom = true;
            EventBus.getDefault().post(new GestureIsZoomEvent());
        }

        if(endOfGesture) {
            EventBus.getDefault().postSticky(new EndOfGestureEvent());
            if(!this.gestureIsZoom) EventBus.getDefault().post(event);
            this.gestureIsMove = false;
            this.gestureIsZoom = false;
            this.gestureMovePointsCounter = 0;
        }
    }

    @SuppressLint("WrongConstant")
    @Subscribe(sticky = true, threadMode = ThreadMode.ASYNC)
    public void onOverlayVisibilityChange(OverlayVisibilityChangeEvent event) {
        this.listenToTouchEvents = event.getVisibility() == View.GONE;
    }


    public boolean shouldListenToTouchEvents() {
        return listenToTouchEvents;
    }
}
