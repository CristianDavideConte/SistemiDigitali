package com.example.sistemidigitali.model;

import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;

import androidx.constraintlayout.widget.ConstraintSet;

import com.example.sistemidigitali.customEvents.EndOfGestureEvent;
import com.example.sistemidigitali.customEvents.OverlayVisibilityChangeEvent;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class CustomGestureDetector {

    private boolean gestureIsZoom;
    private boolean listenToTouchEvents;

    public CustomGestureDetector() {
        this.gestureIsZoom = false;
        this.listenToTouchEvents = true;
    }

    public void update(MotionEvent event) {
        if(!this.listenToTouchEvents) return;

        int action = event.getAction();
        boolean endOfGesture = action == MotionEvent.ACTION_UP;
        boolean gestureIsTap = action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE || endOfGesture;
        if(!gestureIsTap) this.gestureIsZoom = true;

        if(endOfGesture) {
            EventBus.getDefault().postSticky(new EndOfGestureEvent());
            if(!this.gestureIsZoom) EventBus.getDefault().postSticky(event);
            this.gestureIsZoom = false;
        }
    }

    @SuppressLint("WrongConstant")
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onOverlayVisibilityChange(OverlayVisibilityChangeEvent event) {
        this.listenToTouchEvents = event.getVisibility() == View.GONE;
    }

    public boolean isGestureIsZoom() {
        return gestureIsZoom;
    }

    public boolean shouldListenToTouchEvents() {
        return listenToTouchEvents;
    }
}
