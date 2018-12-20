package com.saralweb.bgloc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.app.ActivityManager;

/**
 * Helpers class
 */
public class Helpers {
    private static final String TAG = Helpers.class.getName();

    // cunstructor
    public Helpers() {
    }

    public boolean isServiceRunning(Context context, Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                Log.d(TAG, "isServiceRunning: true");
                return true;
            }
        }
        Log.d(TAG, "isServiceRunning: false");
        return false;
    }
}
