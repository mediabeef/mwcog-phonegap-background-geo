package com.mediabeef.bgloc;

import android.location.Location;
import android.os.Build;
import android.util.Log;

import com.mediabeef.bgloc.data.BackgroundLocation;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URL;
import java.net.HttpURLConnection;
import java.io.OutputStreamWriter;

public class HttpPostService {

    public static int postJSON(String url, Object json, Map headers) throws IOException {
        String jsonString = json.toString();
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setDoOutput(true);
        conn.setFixedLengthStreamingMode(jsonString.length());
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        Iterator<Map.Entry<String, String>> it = headers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> pair = it.next();
            conn.setRequestProperty(pair.getKey(), pair.getValue());
        }

        OutputStreamWriter os = null;
        try {
            os = new OutputStreamWriter(conn.getOutputStream());
            os.write(json.toString());

        } finally {
            if (os != null) {
                os.flush();
                os.close();
            }
        }

        return conn.getResponseCode();
    }

    /**
     * Do a (get) post to production API, e.g.
     * http://mwcog2.mediabeef.com/mwcog/verifiedtripservicecontrol?action=heartbeatVerifiedTrip&current_lat=32.74776941&current_lng=-117.06786961&tripId=742915Home190410133508&end_trip=1
     * @param url
     * @param location
     * @param headers
     * @param config
     * @return
     * @throws IOException
     */
    public static int getJSON(String url, BackgroundLocation location, Map headers, Config config) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Content-Type", "application/json");
        Iterator<Map.Entry<String, String>> it = headers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> pair = it.next();
            conn.setRequestProperty(pair.getKey(), pair.getValue());
        }
        //here we do the same for the json object
        conn.setRequestProperty("action", "heartbeatVerifiedTrip");
        conn.setRequestProperty("current_lat", String.valueOf(location.getLatitude()));
        conn.setRequestProperty("current_lng", String.valueOf(location.getLongitude()));
        conn.setRequestProperty("tripId", config.getTrip_id());
        conn.setRequestProperty("end_trip", String.valueOf(location.is_end_of_trip ? 1 : 0));
        int response_code = conn.getResponseCode();
        conn.disconnect();
        return response_code;
    }


    public static int postFile(String url, File file, Map headers, UploadingCallback callback) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();

        conn.setDoInput(false);
        conn.setDoOutput(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            conn.setFixedLengthStreamingMode(file.length());
        } else {
            conn.setChunkedStreamingMode(0);
        }
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        Iterator<Map.Entry<String, String>> it = headers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> pair = it.next();
            conn.setRequestProperty(pair.getKey(), pair.getValue());
        }

        long progress = 0;
        int bytesRead = -1;
        byte[] buffer = new byte[1024];

        BufferedInputStream is = null;
        BufferedOutputStream os = null;
        try {
            is = new BufferedInputStream(new FileInputStream(file));
            os = new BufferedOutputStream(conn.getOutputStream());
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
                os.flush();
                progress += bytesRead;
                int percentage = (int) ((progress * 100L) / file.length());
                if (callback != null) {
                    callback.uploadListener(percentage);
                }
            }
        } finally {
            if (os != null) {
                os.flush();
                os.close();
            }
            if (is != null) {
                is.close();
            }
        }

        return conn.getResponseCode();
    }
}
