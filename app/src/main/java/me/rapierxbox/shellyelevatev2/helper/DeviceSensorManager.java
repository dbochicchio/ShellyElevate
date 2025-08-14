package me.rapierxbox.shellyelevatev2.helper;

import static me.rapierxbox.shellyelevatev2.ShellyElevateApplication.mDeviceHelper;
import static me.rapierxbox.shellyelevatev2.ShellyElevateApplication.mMQTTServer;
import static me.rapierxbox.shellyelevatev2.ShellyElevateApplication.mScreenSaverManager;
import static me.rapierxbox.shellyelevatev2.ShellyElevateApplication.mSharedPreferences;
import static me.rapierxbox.shellyelevatev2.Constants.*;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.util.Log;


public class DeviceSensorManager implements SensorEventListener {
    private float lastMeasuredLux = 0.0f;
    public float getLastMeasuredLux() {
        return lastMeasuredLux;
    }

    private float lastMeasuredDistance = 0.0f;
    public float getLastMeasuredDistance() { return lastMeasuredDistance; }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            lastMeasuredLux = event.values[0];

            if (mSharedPreferences.getBoolean(SP_AUTOMATIC_BRIGHTNESS, true)) {
                mDeviceHelper.setScreenBrightness(getScreenBrightnessFromLux(lastMeasuredLux));
            }
            if (mMQTTServer.shouldSend()) {
                mMQTTServer.publishLux(lastMeasuredLux);
            }
        }

        if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
            lastMeasuredDistance = event.values[0];

            Log.d("DeviceSensorManager", "Received new proximity sensor value: " + event.values[0]);
            if (mSharedPreferences.getBoolean(SP_WAKE_ON_PROXIMITY, false)
                    && lastMeasuredDistance <= 7.5f) {
                mScreenSaverManager.stopScreenSaver();
            }
            if (mMQTTServer.shouldSend()) {
                mMQTTServer.publishProximity(lastMeasuredDistance);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Ignore
    }

    public static int getScreenBrightnessFromLux(float lux) {
        int minBrightness = mSharedPreferences.getInt(SP_MIN_BRIGHTNESS, 48);
        if (lux >= 500) return 255;
        if (lux <= 30) return minBrightness;

        double slope = (255.0 - minBrightness) / (500.0 - 30.0);
        return (int) (minBrightness + slope * (lux - 30));
    }
}