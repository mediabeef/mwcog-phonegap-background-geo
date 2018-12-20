package com.saralweb.bgloc;

import java.util.Timer;
import java.util.TimerTask;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.app.ActivityManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.widget.Toast;

import org.json.JSONException;

import com.saralweb.bgloc.Config;
import com.saralweb.bgloc.Helpers;
import com.saralweb.bgloc.LocationService;
import com.saralweb.bgloc.data.DAOFactory;
import com.saralweb.bgloc.data.ConfigurationDAO;

/**
 * RestartLocationServiceReceiver class
 */
public class RestartLocationServiceReceiver extends BroadcastReceiver {
    private static final String TAG = RestartLocationServiceReceiver.class.getName();

    protected ToneGenerator toneGenerator;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Received RestartLocationService");

        Helpers helpers = new Helpers();
        ConfigurationDAO dao = DAOFactory.createConfigurationDAO(context);
        Config config = null;

        try {
            config = dao.retrieveConfiguration();
        } catch (JSONException e) {
            Log.d(TAG, "Config exception: " + e.getMessage());
        }

        if (config == null) { return; }

        Log.d(TAG, "RestartLocationServiceReceiver, config: " + config.toString());

        if (!config.getStopOnTerminate()) {
            // start LocationService if not already running
            if (!helpers.isServiceRunning(context, LocationService.class)) {
                Log.d(TAG, "Starting LocationService");
                Intent locationServiceIntent = new Intent(context, LocationService.class);
                locationServiceIntent.addFlags(Intent.FLAG_FROM_BACKGROUND);
                locationServiceIntent.putExtra("config", config);

                context.startService(locationServiceIntent);

                // play debug sound CHIRP_CHIRP_CHIRP to notify that LocationService is running properly
                if (config.isDebugging()) {
                    toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
                    toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1000);
                    Toast.makeText(context, "LocationService started by RestartLocationServiceReceiver", Toast.LENGTH_LONG).show();

                    // we are done, release toneGenerator
                    Timer timer = new Timer();
                    timer.schedule( new TimerTask() {
                        public void run() { 
                            if (toneGenerator != null) {
                                toneGenerator.release();
                                toneGenerator = null;
                            }
                        }
                    }, 3000);
                }
            } else {
                Log.d(TAG, "LocationService is already running");
            }
        }
    }
}
