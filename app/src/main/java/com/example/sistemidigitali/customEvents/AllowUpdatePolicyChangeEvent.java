package com.example.sistemidigitali.customEvents;

import android.content.Context;

public class AllowUpdatePolicyChangeEvent {
    private Context context;
    private boolean allowUpdatePolicyChange;

    public AllowUpdatePolicyChangeEvent(Context context, boolean allowUpdatePolicyChange) {
        this.context = context;
        this.allowUpdatePolicyChange = allowUpdatePolicyChange;
    }

    public Context getContext() {
        return context;
    }

    public boolean isAllowUpdatePolicyChange() {
        return allowUpdatePolicyChange;
    }
}
