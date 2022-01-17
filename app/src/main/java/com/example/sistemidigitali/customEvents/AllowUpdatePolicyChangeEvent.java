package com.example.sistemidigitali.customEvents;

public class AllowUpdatePolicyChangeEvent {
    private boolean allowUpdatePolicyChange;

    public AllowUpdatePolicyChangeEvent(boolean allowUpdatePolicyChange) {
        this.allowUpdatePolicyChange = allowUpdatePolicyChange;
    }

    public boolean isAllowUpdatePolicyChange() {
        return allowUpdatePolicyChange;
    }
}
