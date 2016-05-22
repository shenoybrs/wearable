package com.example.android.sunshine.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.WearableListenerService;

public class WeatherUpdateService extends WearableListenerService {
    private static final String TAG = "WeatherUpdateService";
    public static final String PREF_MAX_TEMP = "max_weather";
    public static final String PREF_MIN_TEMP = "min_weather";
    public static final String PREF_WEATHER_ID = "weather_id";


    public WeatherUpdateService() {
        Log.d(TAG, "UpdateService created");
    }

    private static final String WEATHER_DATA_PATH = "/weather";

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.d(TAG, "onDataChanged()");

        // Loop through the events and send a message
        // to the node that created the data item.
        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                Log.d(TAG, "Updating prefs");
                DataMap dataMap = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
                float max = dataMap.getFloat("max_temp");
                float min = dataMap.getFloat("min_temp");
                int weatherId = dataMap.getInt("weather_id");

                Intent intent = new Intent("custom-event-name");
                intent.putExtra(PREF_MAX_TEMP, max);
                intent.putExtra(PREF_MIN_TEMP, min);
                intent.putExtra(PREF_WEATHER_ID, weatherId);
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

            }
        }
    }
}
