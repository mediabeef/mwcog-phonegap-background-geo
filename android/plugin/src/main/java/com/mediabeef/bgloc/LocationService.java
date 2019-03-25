/*
According to apache license

This is fork of christocracy cordova-plugin-background-geolocation plugin
https://github.com/christocracy/cordova-plugin-background-geolocation

This is a new class
*/

package com.mediabeef.bgloc;

import android.accounts.Account;
import android.app.*;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.SQLException;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.*;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import com.mediabeef.bgloc.data.BackgroundLocation;
import com.mediabeef.bgloc.data.ConfigurationDAO;
import com.mediabeef.bgloc.data.DAOFactory;
import com.mediabeef.bgloc.data.LocationDAO;
import com.mediabeef.bgloc.sync.AccountHelper;
import com.mediabeef.bgloc.sync.AuthenticatorService;
import com.mediabeef.bgloc.sync.SyncService;
import com.mediabeef.cordova.BackgroundGeolocationPlugin;
import com.mediabeef.logging.LoggerManager;
import com.mediabeef.mwcog.MainActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class LocationService extends Service {

    /**
     * Keeps track of all current registered clients.
     */
    ArrayList<Messenger> mClients = new ArrayList<Messenger>();

    /**
     * Command sent by the service to
     * any registered clients with error.
     */
    public static final int MSG_ERROR = 1;

    /**
     * Command to the service to register a client, receiving callbacks
     * from the service.  The Message's replyTo field must be a Messenger of
     * the client where callbacks should be sent.
     */
    public static final int MSG_REGISTER_CLIENT = 2;

    /**
     * Command to the service to unregister a client, ot stop receiving callbacks
     * from the service.  The Message's replyTo field must be a Messenger of
     * the client as previously given with MSG_REGISTER_CLIENT.
     */
    public static final int MSG_UNREGISTER_CLIENT = 3;

    /**
     * Command sent by the service to
     * any registered clients with the new position.
     */
    public static final int MSG_LOCATION_UPDATE = 4;

    /**
     * Command sent by the service to
     * any registered clients whenever the devices enters "stationary-mode"
     */
    public static final int MSG_ON_STATIONARY = 5;


    /**
     * Command sent by the service to
     * any registered clients whenever the clients want to change provider operation mode
     */
    public static final int MSG_SWITCH_MODE = 6;

    /**
     * Command sent by the service to
     * any registered clients with the new position.
     */
    public static final int MSG_END_TRIP_REACHED = 7;

    /**
     * background operation mode of location provider
     */
    public static final int BACKGROUND_MODE = 0;

    /**
     * foreground operation mode of location provider
     */
    public static final int FOREGROUND_MODE = 1;

    private static final int ONE_MINUTE = 1000 * 60;
    private static final int FIVE_MINUTES = 1000 * 60 * 5;
        private static final float TIMEOUT_SELF_KILL_HOURS = 4;
//    private static final float TIMEOUT_SELF_KILL_HOURS = (float)2 / 60;//ttodob debug = 2 minutes

    private LocationDAO dao;
    private Config config;
    private LocationProvider provider;
    private Account syncAccount;
    private Boolean hasConnectivity = true;
    protected Notification mNoti;
    protected NotificationManager mNotificationManager;
    private BackgroundGeolocationPlugin mPlugin;

    private org.slf4j.Logger log;

    private volatile HandlerThread handlerThread;
    private ServiceHandler serviceHandler;
    int notifyID = 1;
    final String CHANNEL_ID = "mwcog_background_geolocation";
    final CharSequence channel_name = "commuter_connections_background";
    final String channel_description = "Commuter Connections background geolocation notifications";
    NotificationChannel mChannel = null;
    protected Date time_to_kill_self = null;

    private class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
        }
    }

    /**
     * Handler of incoming messages from clients.
     */
    private class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_REGISTER_CLIENT:
                    mClients.add(msg.replyTo);
                    break;
                case MSG_UNREGISTER_CLIENT:
                    mClients.remove(msg.replyTo);
                    break;
                case MSG_SWITCH_MODE:
                    switchMode(msg.arg1);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger messenger = new Messenger(new IncomingHandler());

    /**
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    @Override
    public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        log = LoggerManager.getLogger(LocationService.class);
        log.info("Creating LocationService");
        this.time_to_kill_self = new Date();
        this.time_to_kill_self.setTime((long) (this.time_to_kill_self.getTime() + LocationService.TIMEOUT_SELF_KILL_HOURS * 60 * 60 * 1000));

        // An Android handler thread internally operates on a looper.
        handlerThread = new HandlerThread("LocationService.HandlerThread");
        handlerThread.start();
        // An Android service handler is a handler running on a specific background thread.
        serviceHandler = new ServiceHandler(handlerThread.getLooper());

        dao = (DAOFactory.createLocationDAO(this));
        syncAccount = AccountHelper.CreateSyncAccount(this,
                AuthenticatorService.getAccount(
                        getStringResource(Config.ACCOUNT_NAME_RESOURCE),
                        getStringResource(Config.ACCOUNT_TYPE_RESOURCE)));

        registerReceiver(connectivityChangeReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Override
    public void onDestroy() {
        log.info("Destroying LocationService");
        this.time_to_kill_self = null;
        provider.onDestroy();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            handlerThread.quitSafely();
        } else {
            handlerThread.quit(); //sorry
        }

        unregisterReceiver(connectivityChangeReceiver);
        super.onDestroy();

        // sending broadcast to make sure that LocationService is running porperly,
        // service will be restarted if not running already
        log.info("Sending broadcast RestartLocationService");
        Intent broadcastIntent = new Intent("com.mediabeef.bgloc.RestartLocationService");
        sendBroadcast(broadcastIntent);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        log.debug("Task has been removed");

        if (config == null) {
            ConfigurationDAO dao = DAOFactory.createConfigurationDAO(this);
            try {
                config = dao.retrieveConfiguration();
            } catch (JSONException e) {
                log.error("Config exception: {}", e.getMessage());
                config = new Config(); //using default config
            }
        }

        if (config.getStopOnTerminate()) {
            log.info("Stopping self");
            stopSelf();
        } else {
            log.info("Continue running in background");
            /* Handle custom android OS */
            if (isOSCustomAndroid()) {
                log.info("Restarting LocationService explicitly using AlarmManager");
                Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
                restartServiceIntent.setPackage(getPackageName());
                restartServiceIntent.putExtra("config", config);

                PendingIntent restartServicePendingIntent = PendingIntent.getService(getApplicationContext(), 1, restartServiceIntent, PendingIntent.FLAG_ONE_SHOT);
                AlarmManager alarmService = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
                alarmService.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000, restartServicePendingIntent);
            }
        }

        // sending broadcast to make sure that LocationService is running porperly,
        // service will be restarted if not running already
        log.info("Sending broadcast RestartLocationService");
        Intent broadcastIntent = new Intent("com.mediabeef.bgloc.RestartLocationService");
        sendBroadcast(broadcastIntent);

        super.onTaskRemoved(rootIntent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        log.info("Received start startId: {} intent: {}", startId, intent);

        if (provider != null) {
            provider.onDestroy();
        }

        if (intent == null) {
            //service has been probably restarted so we need to load config from db
            ConfigurationDAO dao = DAOFactory.createConfigurationDAO(this);
            try {
                config = dao.retrieveConfiguration();
            } catch (JSONException e) {
                log.error("Config exception: {}", e.getMessage());
                config = new Config(); //using default config
            }
        } else {
            if (intent.hasExtra("config")) {
                config = intent.getParcelableExtra("config");
            } else {
                config = new Config(); //using default config
            }
        }

        log.debug("Will start service with: {}", config.toString());

        LocationProviderFactory spf = new LocationProviderFactory(this);
        provider = spf.getInstance(config.getLocationProvider());

        if (config.getStartForeground()) {
            // Build a Notification required for running service in foreground.
            mNotificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
            Context mContext = getApplicationContext();

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                String CHANNEL_ID = this.CHANNEL_ID;
                int importance = NotificationManager.IMPORTANCE_HIGH;
                NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, this.channel_name, importance);
                mChannel.setDescription(this.channel_description);
                mChannel.setShowBadge(true);
                if (mNotificationManager != null) {
                    mNotificationManager.createNotificationChannel(mChannel);
                } else {
                    log.error("null noti manager");
                }
            }

            NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_menu_mylocation);
//                    .setContentTitle("title")
//                    .setContentText("message");

//        Intent resultIntent = new Intent(ctx, MainActivity.class);
//        TaskStackBuilder stackBuilder = TaskStackBuilder.create(ctx);
//        stackBuilder.addParentStack(MainActivity.class);
//        stackBuilder.addNextIntent(resultIntent);
//        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
//        builder.setContentIntent(resultPendingIntent);
            mNoti = builder.build();

            log.info("Executing startForeground with startId: {}", startId);
            startForeground(startId, mNoti);
        }

        provider.startRecording();
        super.onStartCommand(intent, flags, startId);

        //We want this service to continue running until it is explicitly stopped
        return Service.START_STICKY;
    }

    protected int getAppResource(String name, String type) {
        return getApplication().getResources().getIdentifier(name, type, getApplication().getPackageName());
    }

    protected Integer getDrawableResource(String resourceName) {
        return getAppResource(resourceName, "drawable");
    }

    protected String getStringResource(String name) {
        return getApplication().getString(getAppResource(name, "string"));
    }

    private Integer parseNotificationIconColor(String color) {
        int iconColor = 0;
        if (color != null) {
            try {
                iconColor = Color.parseColor(color);
            } catch (IllegalArgumentException e) {
                log.error("Couldn't parse color from android options");
            }
        }
        return iconColor;
    }

    public void startRecording() {
        provider.startRecording();
    }

    public void stopRecording() {
        provider.stopRecording();
    }

    public boolean isOSCustomAndroid() {
        log.info("MANUFACTURER: {} BRAND: {}", Build.MANUFACTURER, Build.BRAND);
        // TODO: get list of brands from config (which comes with custom android os and has autostart and power saving mode)
        List<String> brandsWithCustomOS = new ArrayList<String>(Arrays.asList(new String[]{"vivo", "oppo", "lava"}));

        for (String bElem : brandsWithCustomOS) {
            if (bElem.equalsIgnoreCase(Build.BRAND) || bElem.equalsIgnoreCase(Build.MANUFACTURER)) {
                log.info("isOSCustomAndroid: true");
                return true;
            }
        }
        log.info("isOSCustomAndroid: false");
        return false;
    }

    /**
     * Handle location from location location provider
     * Brian3t similar to flushQueue()
     * <p>
     * All locations updates are recorded in local db at all times.
     * Also location is also send to all messenger clients.
     * <p>
     * If option.url is defined, each location is also immediately posted.
     * If post is successful, the location is deleted from local db.
     * All failed to post locations are coalesced and send in some time later in one single batch.
     * Batch sync takes place only when number of failed to post locations reaches syncTreshold.
     * <p>
     * If only option.syncUrl is defined, locations are send only in single batch,
     * when number of locations reaches syncTreshold.
     *
     * @param location The received location
     */
    public void handleLocation(BackgroundLocation location) {
        log.debug("New location {}", location.toString());
        String time_to_kill_self_str = "";
        Date cur_time = (new Date());
        if (this.time_to_kill_self != null) {
            time_to_kill_self_str = this.time_to_kill_self.toString();
        }
        if (config.isDebugging()) {
            log.debug("Start time {} ", time_to_kill_self_str);
            log.debug("Current time {} ", cur_time.toString());
        }
        if (cur_time.after(time_to_kill_self)) {//ttodob debugging
            StaticHelper.is_timeout_reached = true;
            log.info("_________LM stops itself because of timeout:");
            log.info("Start time {} ", time_to_kill_self_str);
            log.info("Current time {} ", cur_time.toString());

            // Build a Notification required for running service in foreground.
            mNotificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
            Context mContext = getApplicationContext();
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                String CHANNEL_ID = this.CHANNEL_ID;
                int importance = NotificationManager.IMPORTANCE_HIGH;
                NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, this.channel_name, importance);
                mChannel.setDescription(this.channel_description);
                mChannel.setShowBadge(true);
                if (mNotificationManager != null) {
                    mNotificationManager.createNotificationChannel(mChannel);
                } else {
                    log.error("null noti manager");
                }
            }

            NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                    .setContentTitle("Commuter Connections Flextrip")
                    .setContentText("Timeout reached. Your trip is not being logged anymore");
            Intent intent = new Intent(this, MainActivity.class);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.putExtra("launcher", "mwcbg");
            intent.putExtra("reason", "location_service_timeout_reached");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            //done preparing intent. now wrap it
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            builder.setContentIntent(pendingIntent);

            mNoti = builder.build();

            mNotificationManager.notify(this.notifyID, mNoti);
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    stopSelf();//delayed Stop, otherwise the notification will go away
                }
            }, 6000);

            if (android.os.Build.VERSION.SDK_INT == 23){//this is for Blackberry 6.0.1
                log.info("________this is for Blackberry 6.0.1");
                NotificationCompat.Builder builder_v23 = new NotificationCompat.Builder(mContext)
                        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                        .setContentTitle("ommuter Connections Flextrip")
                        .setContentText("Timeout reached. Your trip is not being logged anymore")
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true);
                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mContext);
                notificationManager.notify(1345, builder_v23.build());
            }
        }

        location.setBatchStartMillis(System.currentTimeMillis() + ONE_MINUTE); // prevent sync of not yet posted location
        persistLocation(location);

        if (config.hasUrl() || config.hasSyncUrl()) {
            Long locationsCount = dao.locationsForSyncCount(System.currentTimeMillis());
            log.debug("Location to sync: {} threshold: {}", locationsCount, config.getSyncThreshold());
            if (locationsCount >= config.getSyncThreshold()) {
                log.debug("Attempt to sync locations: {} threshold: {}", locationsCount, config.getSyncThreshold());
                SyncService.sync(syncAccount, getStringResource(Config.CONTENT_AUTHORITY_RESOURCE));
            }
        }

        if (hasConnectivity && config.hasUrl()) {
            location.setConfig(config);
            postLocationAsync(location);
        }

        Bundle bundle = new Bundle();
        bundle.putParcelable("location", location);
        Message msg;

        //brian3t now check if location is close to end_lat end_lng. If it does, stop BP
        //commenting out is_endof trip
        if (location.is_end_of_trip) {
            StaticHelper.is_end_of_trip_static = true;
            log.info("_________LM stops itself due to end_of_trip reached. Location:");
            log.info("latitude: {}", location.getLatitude());
            log.info("config: {}", config.getEnd_lat());

            // Build a Notification required for running service in foreground.

            mNotificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
            Context mContext = getApplicationContext();

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                String CHANNEL_ID = this.CHANNEL_ID;
                int importance = NotificationManager.IMPORTANCE_HIGH;
                NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, this.channel_name, importance);
                mChannel.setDescription(this.channel_description);
                mChannel.setShowBadge(true);
                if (mNotificationManager != null) {
                    mNotificationManager.createNotificationChannel(mChannel);
                } else {
                    log.error("null noti manager");
                }
            }

            NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                    .setContentTitle("Commuter Connections Flextrip")
                    .setContentText("Congratulations! Your trip has been verified!");
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            intent.putExtra("launcher", "mwcbg");
            intent.putExtra("reason", "location_service_is_end_of_trip_true");

            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
            builder.setContentIntent(pendingIntent);

            mNoti = builder.build();

            mNotificationManager.notify(this.notifyID, mNoti);
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    stopSelf();//delayed Stop, otherwise the notification will go away
                }
            }, 6000);

            if (android.os.Build.VERSION.SDK_INT == 23){//this is for Blackberry 6.0.1
                log.info("________this is for Blackberry 6.0.1");
                NotificationCompat.Builder builder_v23 = new NotificationCompat.Builder(mContext)
                        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                        .setContentTitle("Commuter Connections Flextrip")
                        .setContentText("Congratulations! Your trip has been verified!")
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true);
                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mContext);
                notificationManager.notify(1346, builder_v23.build());
            }
        } else {
            msg = Message.obtain(null, MSG_LOCATION_UPDATE);
            msg.setData(bundle);
            sendClientMessage(msg);
        }
    }

    public void handleStationary(BackgroundLocation location) {
        log.debug("New stationary {}", location.toString());

        Bundle bundle = new Bundle();
        bundle.putParcelable("location", location);
        Message msg = Message.obtain(null, MSG_ON_STATIONARY);
        msg.setData(bundle);

        sendClientMessage(msg);
    }

    public void switchMode(int mode) {
        // TODO: implement
    }

    public void sendClientMessage(Message msg) {
        for (int i = mClients.size() - 1; i >= 0; i--) {
            try {
                mClients.get(i).send(msg);
            } catch (RemoteException e) {
                // The client is dead.  Remove it from the list;
                // we are going through the list from back to front
                // so this is safe to do inside the loop.
                mClients.remove(i);
            }
        }
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        return super.registerReceiver(receiver, filter, null, serviceHandler);
    }

    @Override
    public void unregisterReceiver(BroadcastReceiver receiver) {
        super.unregisterReceiver(receiver);
    }

    public void handleError(JSONObject error) {
        Bundle bundle = new Bundle();
        bundle.putString("error", error.toString());
        Message msg = Message.obtain(null, MSG_ERROR);
        msg.setData(bundle);

        sendClientMessage(msg);
    }

    // method will mutate location
    public Long persistLocation(BackgroundLocation location) {
        Long locationId = -1L;
        try {
            locationId = dao.persistLocationWithLimit(location, config.getMaxLocations());
            location.setLocationId(locationId);
            log.debug("Persisted location: {}", location.toString());
        } catch (SQLException e) {
            log.error("Failed to persist location: {} error: {}", location.toString(), e.getMessage());
        }

        return locationId;
    }

    public void postLocation(BackgroundLocation location) {
        PostLocationTask task = new LocationService.PostLocationTask();
        task.doInBackground(location);
    }

    public void postLocationAsync(BackgroundLocation location) {
        PostLocationTask task = new LocationService.PostLocationTask();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, location);
        } else {
            task.execute(location);
        }
    }

    public Config getConfig() {
        return this.config;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    /**
     * PostLocationTask
     * brian3t this is similar to PostAsJSON()
     */
    private class PostLocationTask extends AsyncTask<BackgroundLocation, Integer, Boolean> {

        @Override
        protected Boolean doInBackground(BackgroundLocation... locations) {
            log.debug("Executing PostLocationTask#doInBackground");
            JSONArray jsonLocations = new JSONArray();
            for (BackgroundLocation location : locations) {
                try {
                    JSONObject jsonLocation = location.toJSONObject();
                    //here append commuter_id to post back
                    jsonLocation.put("commuter_id", config.getCommuter_id());
                    jsonLocations.put(jsonLocation);
                } catch (JSONException e) {
                    log.warn("Location to json failed: {}", location.toString());
                    return false;
                }
            }

            String url = config.getUrl();
            log.debug("Posting json to url: {} headers: {}", url, config.getHttpHeaders());
            int responseCode;

            try {
                responseCode = HttpPostService.postJSON(url, jsonLocations, config.getHttpHeaders());
            } catch (Exception e) {
                hasConnectivity = isNetworkAvailable();
                log.warn("Error while posting locations: {}", e.getMessage());
                return false;
            }

            if (responseCode != HttpURLConnection.HTTP_OK) {
                log.warn("Server error while posting locations responseCode: {}", responseCode);
                return false;
            }

            for (BackgroundLocation location : locations) {
                Long locationId = location.getLocationId();
                if (locationId != null) {
                    dao.deleteLocation(locationId);
                }
            }

            return true;
        }
    }

    /**
     * Broadcast receiver which detects connectivity change condition
     */
    private BroadcastReceiver connectivityChangeReceiver = new BroadcastReceiver() {
        private static final String LOG_TAG = "NetworkChangeReceiver";

        @Override
        public void onReceive(Context context, Intent intent) {
            hasConnectivity = isNetworkAvailable();
            log.info("Network condition changed hasConnectivity: {}", hasConnectivity);
        }
    };

    private boolean isNetworkAvailable() {
        ConnectivityManager cm =
                (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }
}
