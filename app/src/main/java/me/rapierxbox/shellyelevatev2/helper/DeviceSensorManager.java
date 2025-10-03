package me.rapierxbox.shellyelevatev2.helper;

import static me.rapierxbox.shellyelevatev2.Constants.INTENT_LIGHT_KEY;
import static me.rapierxbox.shellyelevatev2.Constants.INTENT_LIGHT_UPDATED;
import static me.rapierxbox.shellyelevatev2.Constants.INTENT_PROXIMITY_KEY;
import static me.rapierxbox.shellyelevatev2.Constants.INTENT_PROXIMITY_UPDATED;
import static me.rapierxbox.shellyelevatev2.Constants.SP_AUTOMATIC_BRIGHTNESS;
import static me.rapierxbox.shellyelevatev2.Constants.SP_MIN_BRIGHTNESS;
import static me.rapierxbox.shellyelevatev2.Constants.SP_WAKE_ON_PROXIMITY;
import static me.rapierxbox.shellyelevatev2.ShellyElevateApplication.mMQTTServer;
import static me.rapierxbox.shellyelevatev2.ShellyElevateApplication.mScreenSaverManager;
import static me.rapierxbox.shellyelevatev2.ShellyElevateApplication.mSharedPreferences;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import me.rapierxbox.shellyelevatev2.DeviceModel;
import me.rapierxbox.shellyelevatev2.ShellyElevateApplication;


public class DeviceSensorManager implements SensorEventListener {
    private static final String TAG = "DeviceSensorManager";
    private float lastMeasuredLux = 0.0f;

    private final Context context;

    public DeviceSensorManager(Context ctx) {
        context = ctx;

        DeviceModel device = DeviceModel.getDevice(mSharedPreferences);
        SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        // light sensor
        Sensor lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);

        // proximity sensor
        Log.d("ShellyElevateApplication", "Has proximity sensor: " + device.hasProximitySensor);
        if (device.hasProximitySensor) {
            Sensor proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
            if (proximitySensor != null) {
                Log.d("ShellyElevateApplication", "Default proximity sensor: " + proximitySensor);
                sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }
    }

    public float getLastMeasuredLux() {
        return lastMeasuredLux;
    }

    private float lastMeasuredDistance = 0.0f;
    public float getLastMeasuredDistance() { return lastMeasuredDistance; }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event == null) return;
        Intent intent;

        switch (event.sensor.getType()) {
            case Sensor.TYPE_LIGHT:
                lastMeasuredLux = event.values[0];

                if (mMQTTServer.shouldSend()) {
                    mMQTTServer.publishLux(lastMeasuredLux);
                }
                //Let everyone know we got a new light value
                intent = new Intent(INTENT_LIGHT_UPDATED);
                intent.putExtra(INTENT_LIGHT_KEY, lastMeasuredLux);
                LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                break;

            case Sensor.TYPE_PROXIMITY:
                lastMeasuredDistance = event.values[0];

                if (mMQTTServer.shouldSend()) {
                    mMQTTServer.publishProximity(lastMeasuredDistance);
                }
                //Let everyone know we got a new proximity value
                intent = new Intent(INTENT_PROXIMITY_UPDATED);
                intent.putExtra(INTENT_PROXIMITY_KEY, lastMeasuredDistance);
                LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Ignore
    }

    public void onDestroy() {
        ((SensorManager) context.getSystemService(Context.SENSOR_SERVICE)).unregisterListener(this);
    }
}