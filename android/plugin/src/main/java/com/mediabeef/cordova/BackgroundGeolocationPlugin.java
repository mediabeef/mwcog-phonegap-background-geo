/*
According to apache license

This is fork of christocracy cordova-plugin-background-geolocation plugin
https://github.com/christocracy/cordova-plugin-background-geolocation

Differences to original version:

1. new methods isLocationEnabled, mMessageReciever, handleMessage
*/

package com.mediabeef.cordova;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.text.TextUtils;
import android.widget.Toast;
import com.mediabeef.bgloc.*;
import com.mediabeef.bgloc.data.BackgroundLocation;
import com.mediabeef.bgloc.data.ConfigurationDAO;
import com.mediabeef.bgloc.data.DAOFactory;
import com.mediabeef.bgloc.data.LocationDAO;
import com.mediabeef.logging.DBLogReader;
import com.mediabeef.logging.LogEntry;
import com.mediabeef.logging.LogReader;
import com.mediabeef.logging.LoggerManager;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.app.AlertDialog;

public class BackgroundGeolocationPlugin extends CordovaPlugin {

    public static final String ACTION_START = "start";
    public static final String ACTION_STOP = "stop";
    public static final String ACTION_CONFIGURE = "configure";
    public static final String ACTION_SWITCH_MODE = "switchMode";
    public static final String ACTION_LOCATION_ENABLED_CHECK = "isLocationEnabled";
    public static final String ACTION_SHOW_LOCATION_SETTINGS = "showLocationSettings";
    public static final String ACTION_SHOW_APP_SETTINGS = "showAppSettings";
    public static final String ACTION_SET_BG_AND_11 = "setBgAnd11";
    public static final String ACTION_GET_BG_AND_11 = "getBgAnd11";
    public static final String ACTION_ADD_MODE_CHANGED_LISTENER = "watchLocationMode";
    public static final String ACTION_REMOVE_MODE_CHANGED_LISTENER = "stopWatchingLocationMode";
    public static final String ACTION_ADD_STATIONARY_LISTENER = "addStationaryRegionListener";
    public static final String ACTION_GET_STATIONARY = "getStationaryLocation";
    public static final String ACTION_GET_ALL_LOCATIONS = "getLocations";
    public static final String ACTION_GET_IS_END_OF_TRIP = "getIsEndOfTrip";
    public static final String ACTION_GET_IS_SERVICE_RUNNING = "getIsServiceRunning";
    public static final String ACTION_GET_IS_SERVICE_RECORDING = "getIsServiceRecording";
    public static final String ACTION_RESET_IS_END_OF_TRIP = "resetIsEndOfTrip";
    public static final String ACTION_GET_VALID_LOCATIONS = "getValidLocations";
    public static final String ACTION_DELETE_LOCATION = "deleteLocation";
    public static final String ACTION_DELETE_ALL_LOCATIONS = "deleteAllLocations";
    public static final String ACTION_GET_CONFIG = "getConfig";
    public static final String ACTION_GET_LOG_ENTRIES = "getLogEntries";

    public static final int START_REQ_CODE = 0;
    /**
     * Id to identify a camera permission request.
     */
    private static final int REQUEST_BG = 0;
    public static final int PERMISSION_DENIED_ERROR_CODE = 2;
    public static final String[] permissions;

    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions = new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION};
        } else {
            permissions = new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION};
        }
    }

    /** Messenger for communicating with the service. */
    private Messenger mService = null;
    /** Flag indicating whether we have called bind on the service. */
    private Boolean isBound = false;

    private Config config;
    private CallbackContext callbackContext;
    private ArrayList<CallbackContext> stationaryContexts = new ArrayList<CallbackContext>();
    private CallbackContext actionStartCallbackContext;
    private CallbackContext locationModeChangeCallbackContext;
    private ExecutorService executorService;
    private BackgroundLocation stationaryLocation;
    private Helpers helpers = new Helpers();
    public boolean isEndOfTrip = false;

    private org.slf4j.Logger log;

    /**
     * Handler of incoming messages from service.
     */
    class IncomingHandler extends Handler {
        Context mContext;
        final String CHANNEL_ID = "mwcog_background_geolocation";
        final CharSequence channel_name = "commuter_connections_background";
        final String channel_description = "Commuter Connections background geolocation notifications";
        NotificationChannel mChannel = null;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case LocationService.MSG_LOCATION_UPDATE:
                    try {
                        log.debug("Sending location to webview");
                        Bundle bundle = msg.getData();
                        bundle.setClassLoader(LocationService.class.getClassLoader());
                        BackgroundLocation backgroundLocation = ((BackgroundLocation) bundle.getParcelable("location"));
                        if (backgroundLocation != null) {
                            JSONObject location = backgroundLocation.toJSONObject();
                            PluginResult result = new PluginResult(PluginResult.Status.OK, location);
                            if (location.getBoolean("is_end_of_trip")) {
                                log.debug("____is  bg geo plugin end of trip");
                                log.info("Destroying plugin");
                                unregisterLocationModeChangeReceiver();
                                doUnbindService();

                                if (config != null && config.getStopOnTerminate()) {
                                    log.info("Stopping BackgroundService");
                                    stopBackgroundService();
                                }
                            }
                            if (StaticHelper.is_timeout_reached) {
                                log.debug("____ bg geo plugin timeout reached");
                                log.info("Destroying plugin");
                                unregisterLocationModeChangeReceiver();
                                doUnbindService();

                                if (config != null && config.getStopOnTerminate()) {
                                    log.info("Stopping BackgroundService");
                                    stopBackgroundService();
                                }
                            }
                            result.setKeepCallback(true);
                            callbackContext.sendPluginResult(result);
                        }
                    } catch (JSONException e) {
                        log.warn("Error converting message to json");
                        PluginResult result = new PluginResult(PluginResult.Status.JSON_EXCEPTION);
                        result.setKeepCallback(true);
                        callbackContext.sendPluginResult(result);
                    }
                    break;
                case LocationService.MSG_ON_STATIONARY:
                    if (!stationaryContexts.isEmpty()) {
                        PluginResult result;
                        try {
                            log.debug("Sending stationary location to webview");
                            Bundle bundle = msg.getData();
                            bundle.setClassLoader(LocationService.class.getClassLoader());
                            stationaryLocation = (BackgroundLocation) bundle.getParcelable("location");
                            JSONObject location = stationaryLocation.toJSONObject();
                            result = new PluginResult(PluginResult.Status.OK, location);
                            result.setKeepCallback(true);
                        } catch (JSONException e) {
                            log.warn("Error converting message to json");
                            result = new PluginResult(PluginResult.Status.JSON_EXCEPTION);
                            result.setKeepCallback(true);

                        }

                        for (CallbackContext ctx:stationaryContexts) {
                            ctx.sendPluginResult(result);
                        }
                    }
                    break;
                case LocationService.MSG_ERROR:
                    try {
                        log.debug("Sending error to webview");
                        Bundle bundle = msg.getData();
                        bundle.setClassLoader(LocationService.class.getClassLoader());
                        JSONObject error = new JSONObject(bundle.getString("error"));
                        PluginResult result = new PluginResult(PluginResult.Status.ERROR, error);
                        result.setKeepCallback(true);
                        callbackContext.sendPluginResult(result);
                    } catch (JSONException e) {
                        log.warn("Error converting message to json");
                        PluginResult result = new PluginResult(PluginResult.Status.JSON_EXCEPTION);
                        result.setKeepCallback(true);
                        callbackContext.sendPluginResult(result);
                    }
                    break;
                default:
                    super.handleMessage(msg);
            }
        }


        /**
         // Create the NotificationChannel, but only on API 26+ because
         // the NotificationChannel class is new and not in the support library
         *
         */
        private void createNotificationChannel() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                int importance = NotificationManager.IMPORTANCE_LOW;
                NotificationChannel channel = new NotificationChannel(this.CHANNEL_ID, this.channel_name, importance);
                channel.setDescription(this.channel_description);
                // Register the channel with the system; you can't change the importance
                // or other notification behaviors after this
                NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(NotificationManager.class);
                if (notificationManager != null)    {
                    notificationManager.createNotificationChannel(channel);
                    this.mChannel = channel;
                }
            }
        }
    }

    final Messenger mMessenger = new Messenger(new IncomingHandler());

    /**
     * Class for interacting with the main interface of the service.
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the object we can use to
            // interact with the service.  We are communicating with the
            // service using a Messenger, so here we get a client-side
            // representation of that from the raw IBinder object.
            mService = new Messenger(service);
            isBound = true;

            // We want to monitor the service for as long as we are
            // connected to it.
            try {
                Message msg = Message.obtain(null,
                        LocationService.MSG_REGISTER_CLIENT);
                msg.replyTo = mMessenger;
                mService.send(msg);
            } catch (RemoteException e) {
                // In this case the service has crashed before we could even
                // do anything with it; we can count on soon being
                // disconnected (and then reconnected if it can be restarted)
                // so there is no need to do anything here.
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            mService = null;
            isBound = false;
        }
    };

    private BroadcastReceiver locationModeChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            log.debug("Received MODE_CHANGED_ACTION action");
            if (locationModeChangeCallbackContext != null) {
                PluginResult result;
                try {
                    int isLocationEnabled = BackgroundGeolocationPlugin.isLocationEnabled(context) ? 1 : 0;
                    result = new PluginResult(PluginResult.Status.OK, isLocationEnabled);
                    result.setKeepCallback(true);
                } catch (SettingNotFoundException e) {
                    result = new PluginResult(PluginResult.Status.ERROR, "Location setting error occured");
                }
                locationModeChangeCallbackContext.sendPluginResult(result);
            }
        }
    };

    @Override
    protected void pluginInitialize() {
        super.pluginInitialize();

        log = LoggerManager.getLogger(BackgroundGeolocationPlugin.class);
        LoggerManager.enableDBLogging();
        log.info("initializing plugin");

        final ResourceResolver res = ResourceResolver.newInstance(getApplication());
        final String authority = res.getStringResource(Config.CONTENT_AUTHORITY_RESOURCE);

        executorService =  Executors.newSingleThreadExecutor();
    }

    public boolean execute(String action, final JSONArray data, final CallbackContext callbackContext) {
        Context context = getContext();

        if (ACTION_START.equals(action)) {
            executorService.execute(new Runnable() {
                public void run() {
                    if (config == null) {
                        log.warn("Attempt to start unconfigured service");
                        callbackContext.error("Plugin not configured. Please call configure method first.");
                        return;
                    }

                    if (!hasPermissions()) {
                        log.info("Requesting permissions from user");
                        actionStartCallbackContext = callbackContext;
                        PermissionHelper.requestPermissions(getSelf(), START_REQ_CODE, permissions);
                        callbackContext.success(1);
                        return;
                    }

                    startAndBindBackgroundService();
                    callbackContext.success();
                }
            });

            return true;
        } else if (ACTION_STOP.equals(action)) {
            executorService.execute(new Runnable() {
                public void run() {
                    doUnbindService();
                    stopBackgroundService();
                    callbackContext.success();
                }
            });

            return true;
        } else if (ACTION_SWITCH_MODE.equals(action)) {
            Message msg = Message.obtain(null, LocationService.MSG_SWITCH_MODE);
            try {
                msg.arg1 = data.getInt(0);
                mService.send(msg);
            } catch (Exception e) {
                log.error("Switch mode failed: {}", e.getMessage());
            }

            return true;
        } else if (ACTION_CONFIGURE.equals(action)) {
            this.callbackContext = callbackContext;
            executorService.execute(new Runnable() {
                public void run() {
                    try {
                        config = Config.fromJSONObject(data.getJSONObject(0));
                        persistConfiguration(config);
                        // callbackContext.success(); //we cannot do this
                    } catch (JSONException e) {
                        log.error("Configuration error: {}", e.getMessage());
                        callbackContext.error("Configuration error: " + e.getMessage());
                    } catch (NullPointerException e) {
                        log.error("Configuration error: {}", e.getMessage());
                        callbackContext.error("Configuration error: " + e.getMessage());
                    }
                }
            });

            return true;
        } else if (ACTION_LOCATION_ENABLED_CHECK.equals(action)) {
            log.debug("Location services enabled check");
            try {
                int isLocationEnabled = BackgroundGeolocationPlugin.isLocationEnabled(context) ? 1 : 0;
                callbackContext.success(isLocationEnabled);
            } catch (SettingNotFoundException e) {
                log.error("Location service checked failed: {}", e.getMessage());
                callbackContext.error("Location setting error occured");
            }

            return true;
        } else if (ACTION_SHOW_LOCATION_SETTINGS.equals(action)) {
            showLocationSettings();
            // TODO: call success/fail callback

            return true;
        } else if (ACTION_SHOW_APP_SETTINGS.equals(action)) {
            showAppSettings();
            // TODO: call success/fail callback

            return true;
        } else if (ACTION_SET_BG_AND_11.equals(action)) {
            try {
                String bg11and_result = setBgAnd11();
                if (bg11and_result != null) {
                    callbackContext.success(bg11and_result);
                } else {
                    callbackContext.success();
                }
            } catch (Exception e) {
                log.error("BG and 11 failed: {}", e.getMessage());
                callbackContext.error("BG And 11 failed");
            }

            return true;
        } else if (ACTION_GET_BG_AND_11.equals(action)) {
            try {
                Boolean bg11and_result = getBgAnd11();
                String s_bg11and_result = bg11and_result.toString();
                callbackContext.success(s_bg11and_result);
            } catch (Exception e) {
                log.error("get BG and 11 failed: {}", e.getMessage());
                callbackContext.error("get BG And 11 failed");
            }

            return true;
        } else if (ACTION_ADD_MODE_CHANGED_LISTENER.equals(action)) {
            registerLocationModeChangeReceiver(callbackContext);
            // TODO: call success/fail callback

            return true;
        } else if (ACTION_REMOVE_MODE_CHANGED_LISTENER.equals(action)) {
            unregisterLocationModeChangeReceiver();
            // TODO: call success/fail callback

            return true;
        } else if (ACTION_ADD_STATIONARY_LISTENER.equals(action)) {
            stationaryContexts.add(callbackContext);

            return true;
        } else if (ACTION_GET_STATIONARY.equals(action)) {
            try {
                if (stationaryLocation != null) {
                    callbackContext.success(stationaryLocation.toJSONObject());
                } else {
                    callbackContext.success();
                }
            } catch (JSONException e) {
                log.error("Getting stationary location failed: {}", e.getMessage());
                callbackContext.error("Getting stationary location failed");
            }

            return true;
        } else if (ACTION_GET_ALL_LOCATIONS.equals(action)) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    try {
                        callbackContext.success(getAllLocations());
                    } catch (JSONException e) {
                        log.error("Getting all locations failed: {}", e.getMessage());
                        callbackContext.error("Converting locations to JSON failed.");
                    }
                }
            });

            return true;
        } else if (ACTION_GET_IS_END_OF_TRIP.equals(action)) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    callbackContext.success(getIsEndOfTrip());
                }
            });

            return true;
        }  else if (ACTION_GET_IS_SERVICE_RUNNING.equals(action)) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    callbackContext.success(getIsServiceRunning());
                }
            });

            return true;
        }  else if (ACTION_GET_IS_SERVICE_RECORDING.equals(action)) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    callbackContext.success(getIsServiceRecording());
                }
            });

            return true;
        } else if (ACTION_RESET_IS_END_OF_TRIP.equals(action)) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    resetIsEndOfTrip();
                }
            });

            return true;
        } else if (ACTION_GET_VALID_LOCATIONS.equals(action)) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    try {
                        callbackContext.success(getValidLocations());
                    } catch (JSONException e) {
                        log.error("Getting valid locations failed: {}", e.getMessage());
                        callbackContext.error("Converting locations to JSON failed.");
                    }
                }
            });

            return true;
        } else if (ACTION_DELETE_LOCATION.equals(action)) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    try {
                        Long locationId = data.getLong(0);
                        deleteLocation(locationId);
                        callbackContext.success();
                    } catch (JSONException e) {
                        log.error("Delete location failed: {}", e.getMessage());
                        callbackContext.error("Deleting location failed: " + e.getMessage());
                    }
                }
            });

            return true;
        } else if (ACTION_DELETE_ALL_LOCATIONS.equals(action)) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    deleteAllLocations();
                    callbackContext.success();
                }
            });

            return true;
        } else if (ACTION_GET_CONFIG.equals(action)) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    try {
                        callbackContext.success(retrieveConfiguration());
                    } catch (JSONException e) {
                        log.error("Error getting config: {}", e.getMessage());
                        callbackContext.error("Error getting config: " + e.getMessage());
                    }
                }
            });

            return true;
        } else if (ACTION_GET_LOG_ENTRIES.equals(action)) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    try {
                        callbackContext.success(getLogs(data.getInt(0)));
                    } catch (Exception e) {
                        callbackContext.error("Getting logs failed: " + e.getMessage());
                    }
                }
            });

            return true;
        }

        return false;
    }

    /**
     * Called when the system is about to start resuming a previous activity.
     *
     * @param multitasking		Flag indicating if multitasking is turned on for app
     */
    public void onPause(boolean multitasking) {
        log.info("App will be paused multitasking={}", multitasking);
    }

    /**
     * Called when the activity will start interacting with the user.
     *
     * @param multitasking		Flag indicating if multitasking is turned on for app
     */
    public void onResume(boolean multitasking) {
        log.info("App will be resumed multitasking={}", multitasking);
    }

    /**
     * Called when the activity is becoming visible to the user.
     */
    public void onStart() {
        log.info("App is visible");
    }

    /**
     * Called when the activity is no longer visible to the user.
     */
    public void onStop() {
        log.info("App is no longer visible");
    }

    /**
     * The final call you receive before your activity is destroyed.
     * Checks to see if it should turn off
     */
    @Override
    public void onDestroy() {
        log.info("Destroying plugin");
        unregisterLocationModeChangeReceiver();
        doUnbindService();
        if (config != null && config.getStopOnTerminate()) {
            log.info("Stopping BackgroundService");
            stopBackgroundService();
        }
        super.onDestroy();

        // sending broadcast to make sure that LocationService is running porperly,
        // service will be restarted if not running already
        log.info("Sending broadcast RestartLocationService");
        Intent broadcastIntent = new Intent("com.mediabeef.bgloc.RestartLocationService");
        broadcastIntent.setClass(this.getContext(), this.getClass());
        broadcastIntent.setPackage("com.mediabeef.bgloc");

        getContext().sendBroadcast(broadcastIntent);

    }

    protected BackgroundGeolocationPlugin getSelf() {
        return this;
    }

    protected Activity getActivity() {
        return this.cordova.getActivity();
    }

    protected Application getApplication() {
        return getActivity().getApplication();
    }

    protected Context getContext() {
        return getActivity().getApplicationContext();
    }

    protected void runOnUiThread(Runnable action) {
        getActivity().runOnUiThread(action);
    }

    protected void startAndBindBackgroundService () {
        startBackgroundService();
        doBindService();
        StaticHelper.is_end_of_trip_static = false;
        StaticHelper.is_timeout_reached = false;
    }
    protected void startBackgroundService () {
        if (!helpers.isServiceRunning(getContext(), LocationService.class)) {
            log.info("Starting bg service");
            Activity activity = getActivity();
            Intent locationServiceIntent = new Intent(activity, LocationService.class);
            locationServiceIntent.putExtra("config", config);
            locationServiceIntent.addFlags(Intent.FLAG_FROM_BACKGROUND);
            // start service to keep service running even if no clients are bound to it
            activity.startService(locationServiceIntent);
            StaticHelper.is_recording = true;
        }
    }

    protected void stopBackgroundService() {
        if (helpers.isServiceRunning(getContext(), LocationService.class)) {
            log.info("Stopping bg service");
            Activity activity = getActivity();
            activity.stopService(new Intent(activity, LocationService.class));
            StaticHelper.is_recording = false;
        }
    }

    void doBindService () {
        // Establish a connection with the service.  We use an explicit
        // class name because there is no reason to be able to let other
        // applications replace our component.
        if (!isBound) {
            log.info("Binding to bg service");
            Activity activity = getActivity();
            Intent locationServiceIntent = new Intent(activity, LocationService.class);
            locationServiceIntent.putExtra("config", config);
            activity.bindService(locationServiceIntent, mConnection, Context.BIND_IMPORTANT);
        }
    }

    void doUnbindService () {
        if (isBound) {
            log.info("Unbinding from bg service");
            // If we have received the service, and hence registered with
            // it, then now is the time to unregister.
            if (mService != null) {
                try {
                    Message msg = Message.obtain(null,
                            LocationService.MSG_UNREGISTER_CLIENT);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                } catch (RemoteException e) {
                    // There is nothing special we need to do if the service
                    // has crashed.
                }

                // Detach our existing connection.
                Activity activity = getActivity();
                activity.unbindService(mConnection);
                isBound = false;
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private Intent registerLocationModeChangeReceiver (CallbackContext callbackContext) {
        if (locationModeChangeCallbackContext != null) {
            unregisterLocationModeChangeReceiver();
        }
        locationModeChangeCallbackContext = callbackContext;
        return getContext().registerReceiver(locationModeChangeReceiver, new IntentFilter(LocationManager.MODE_CHANGED_ACTION));
    }

    private void unregisterLocationModeChangeReceiver () {
        if (locationModeChangeCallbackContext == null) { return; }

        getContext().unregisterReceiver(locationModeChangeReceiver);
        locationModeChangeCallbackContext = null;
    }

    public void showLocationSettings() {
        Intent settingsIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        cordova.getActivity().startActivity(settingsIntent);
    }

    public void showAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setData(Uri.parse("package:" + getContext().getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        getContext().startActivity(intent);
    }

    /**
     * Check for bg location permission. If not allowed, show setting page for user to choose
     * @return String
     */
    public String setBgAnd11(){
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return "version less than 30 (R), nothing to do";
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED)
            return "permission granted";

        /* code to show native dialog
        AlertDialog alertDialog = new AlertDialog.Builder(getActivity()).create();
        alertDialog.setTitle("Background location permission");
        alertDialog.setMessage("CarpoolNow will need to access your location in the background in order to best find matches while you are using your phone in other applications");
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Accept",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        String[] myStringArray = new String[1];
                        myStringArray[0] = Manifest.permission.ACCESS_BACKGROUND_LOCATION;
                        androidx.core.app.ActivityCompat.requestPermissions(getActivity(),myStringArray, REQUEST_BG);
                    }
                });
        alertDialog.setButton(
                AlertDialog.BUTTON_NEGATIVE, "Refuse",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        alertDialog.show();*/

        String[] myStringArray = new String[1];
        myStringArray[0] = Manifest.permission.ACCESS_BACKGROUND_LOCATION;
        androidx.core.app.ActivityCompat.requestPermissions(getActivity(),myStringArray, REQUEST_BG);

        return "Resolved";
    }


    /**
     * Only getting bg location permission. If not allowed, show setting page for user to choose
     * @return boolean
     */
    public Boolean getBgAnd11(){
        if (Build.VERSION.SDK_INT < 30) return false;//return false so that mobile app can request it implicitly
        return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public JSONArray getIsServiceRunning(){
        JSONArray is_service_running = new JSONArray();
        is_service_running.put(helpers.isServiceRunning(getContext(), LocationService.class));
        return is_service_running;
    }

    public JSONArray getIsServiceRecording(){
        JSONArray is_service_recording = new JSONArray();
        boolean is_service_recording_bool = helpers.isServiceRecording(getContext(), LocationService.class);
        is_service_recording.put(is_service_recording_bool);
        return is_service_recording;
    }

    public static boolean isLocationEnabled(Context context) throws SettingNotFoundException {
        int locationMode = 0;
        String locationProviders;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            locationMode = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);
            return locationMode != Settings.Secure.LOCATION_MODE_OFF;

        } else {
            locationProviders = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
            return !TextUtils.isEmpty(locationProviders);
        }
    }

    public void resetIsEndOfTrip(){
        StaticHelper.is_end_of_trip_static = false;
    }

    public JSONArray getAllLocations() throws JSONException {
        JSONArray jsonLocationsArray = new JSONArray();
        LocationDAO dao = DAOFactory.createLocationDAO(getContext());
        Collection<BackgroundLocation> locations = dao.getAllLocations();
        for (BackgroundLocation location : locations) {
            jsonLocationsArray.put(location.toJSONObject());
        }
        return jsonLocationsArray;
    }

    public JSONObject getIsEndOfTrip(){
        JSONObject jsonIsEndOfTrip = new JSONObject();
        try{
            jsonIsEndOfTrip.put("is_end_of_trip", StaticHelper.is_end_of_trip_static);
        } catch (JSONException e){
            int a =1;
        }
        return jsonIsEndOfTrip;
    }

    public JSONArray getValidLocations() throws JSONException {
        JSONArray jsonLocationsArray = new JSONArray();
        LocationDAO dao = DAOFactory.createLocationDAO(getContext());
        Collection<BackgroundLocation> locations = dao.getValidLocations();
        for (BackgroundLocation location : locations) {
            jsonLocationsArray.put(location.toJSONObjectWithId());
        }
        return jsonLocationsArray;
    }

    public void deleteLocation(Long locationId) {
        LocationDAO dao = DAOFactory.createLocationDAO(getContext());
        dao.deleteLocation(locationId);
    }

    public void deleteAllLocations() {
        LocationDAO dao = DAOFactory.createLocationDAO(getContext());
        dao.deleteAllLocations();
    }

    public void persistConfiguration(Config config) throws NullPointerException {
        ConfigurationDAO dao = DAOFactory.createConfigurationDAO(getContext());

        dao.persistConfiguration(config);
    }

    public JSONObject retrieveConfiguration() throws JSONException {
        ConfigurationDAO dao = DAOFactory.createConfigurationDAO(getContext());
        Config config = dao.retrieveConfiguration();
        if (config != null) {
            return config.toJSONObject();
        }
        return null;
    }

    public JSONArray getLogs(Integer limit) throws Exception {
        JSONArray jsonLogsArray = new JSONArray();
        LogReader logReader = new DBLogReader();
        Collection<LogEntry> logEntries = logReader.getEntries(limit);
        for (LogEntry logEntry : logEntries) {
            jsonLogsArray.put(logEntry.toJSONObject());
        }
        return jsonLogsArray;
    }

    public boolean hasPermissions() {
        for(String p : permissions) {
            if(!PermissionHelper.hasPermission(this, p)) {
                return false;
            }
        }
        return true;
    }

    public void onRequestPermissionResult(int requestCode, String[] permissions,
                                          int[] grantResults) throws JSONException {
        for (int r : grantResults) {
            if (r == PackageManager.PERMISSION_DENIED) {
                log.info("Permission Denied!");
                actionStartCallbackContext.error(JSONErrorFactory.getJSONError(PERMISSION_DENIED_ERROR_CODE, "Permission denied by user"));
                actionStartCallbackContext = null;
                return;
            }
        }
        switch (requestCode) {
            case START_REQ_CODE:
                startAndBindBackgroundService();
                actionStartCallbackContext.success();
                actionStartCallbackContext = null;
                break;
        }
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(startMain);
    }
}
