package com.flipkart.drift.worker.activities;

import com.flipkart.drift.worker.model.callback.CallbackPayload;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface(namePrefix = "callbackActivity")
public interface CallbackActivity {

    @ActivityMethod
    void sendCallback(String callbackUrl, CallbackPayload payload);
}
