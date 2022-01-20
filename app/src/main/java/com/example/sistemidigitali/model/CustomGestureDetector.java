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
    private int MAX_MOVE_POINTS_FOR_EVENT_TRIGGER = 5;

    private boolean gestureIsZoom;
    private boolean listenToTouchEvents;
    private int gestureMovePointsCounter;

    public CustomGestureDetector() {
        this.gestureIsZoom = false;
        this.listenToTouchEvents = true;
        this.gestureMovePointsCounter = 0;
    }

    public void update(MotionEvent event) {
        if(!this.listenToTouchEvents) return;

        int action = event.getAction();
        boolean endOfGesture  = action == MotionEvent.ACTION_UP;
        boolean gestureIsMove = action == MotionEvent.ACTION_MOVE;
        boolean gestureIsTap  = action == MotionEvent.ACTION_DOWN || gestureIsMove || endOfGesture;

        if(gestureIsMove) {
            this.gestureMovePointsCounter++;
            if(this.gestureMovePointsCounter == MAX_MOVE_POINTS_FOR_EVENT_TRIGGER) {
                EventBus.getDefault().postSticky(new GestureIsMoveEvent());
            }
        }

        if(!gestureIsTap) {
            this.gestureIsZoom = true;
            EventBus.getDefault().postSticky(new GestureIsZoomEvent());
        }

        if(endOfGesture) {
            EventBus.getDefault().postSticky(new EndOfGestureEvent());
            if(!this.gestureIsZoom) EventBus.getDefault().postSticky(event);
            this.gestureIsZoom = false;
            this.gestureMovePointsCounter = 0;
        }
    }

    @SuppressLint("WrongConstant")
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onOverlayVisibilityChange(OverlayVisibilityChangeEvent event) {
        this.listenToTouchEvents = event.getVisibility() == View.GONE;
    }


    public boolean shouldListenToTouchEvents() {
        return listenToTouchEvents;
    }
}
