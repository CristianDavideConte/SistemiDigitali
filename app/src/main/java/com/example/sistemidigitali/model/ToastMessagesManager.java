package com.example.sistemidigitali.model;

import android.content.Context;
import android.widget.Toast;

public class ToastMessagesManager {
    private final String DEFAULT_MESSAGE = "Unavailable";

    private Context context;
    private int duration;
    private Toast toast;

    public ToastMessagesManager(Context context, int duration) {
        this.context = context;
        this.duration = duration;
    }

    private Toast createToast(String message) {
        Toast _toast = Toast.makeText(this.context, message, this.duration);
        _toast.addCallback(new Toast.Callback() {
            @Override
            public void onToastHidden() {
                super.onToastHidden();
                toast = null;
            }
        });
        return _toast;
    }

    public void showToastIfNeeded(String message) {
        if(this.toast != null) return;
        this.toast = createToast(message);
        this.toast.show();
    }

    public void showToastIfNeeded() {
        this.showToastIfNeeded(DEFAULT_MESSAGE);
    }

    public void showToast(String message) {
        if(this.toast != null) this.toast.cancel();
        this.toast = createToast(message);
        this.toast.show();
    }

    public void showToast() {
        this.showToast(DEFAULT_MESSAGE);
    }

    public void hideToast() {
        if(this.toast != null) this.toast.cancel();
    }
}
