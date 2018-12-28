/*
According to apache license

This is fork of christocracy cordova-plugin-background-geolocation plugin
https://github.com/christocracy/cordova-plugin-background-geolocation

This is a new class
*/

package com.mediabeef.bgloc;

import android.content.Context;
import com.mediabeef.bgloc.data.DAOFactory;
import com.mediabeef.bgloc.LocationProvider;
import com.mediabeef.bgloc.DistanceFilterLocationProvider;
import com.mediabeef.bgloc.ActivityRecognitionLocationProvider;
import java.lang.IllegalArgumentException;

/**
 * LocationProviderFactory
 */
public class LocationProviderFactory {

    private LocationService context;

    public LocationProviderFactory(LocationService context) {
        this.context = context;
    };

    public LocationProvider getInstance (Integer locationProvider) {
        LocationProvider provider;
        switch (locationProvider) {
            case Config.ANDROID_DISTANCE_FILTER_PROVIDER:
                provider = new DistanceFilterLocationProvider(context);
                break;
            case Config.ANDROID_ACTIVITY_PROVIDER:
                provider = new ActivityRecognitionLocationProvider(context);
                break;
            default:
                throw new IllegalArgumentException("Provider not found");
        }

        provider.onCreate();
        return provider;
    }
}
