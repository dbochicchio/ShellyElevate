package me.rapierxbox.shellyelevatev2;

import static fi.iki.elonen.NanoHTTPD.*;
import static me.rapierxbox.shellyelevatev2.Constants.SHARED_PREFERENCES_NAME;
import static me.rapierxbox.shellyelevatev2.Constants.SP_DEVICE;
import static me.rapierxbox.shellyelevatev2.Constants.SP_HTTP_SERVER_ENABLED;
import static me.rapierxbox.shellyelevatev2.Constants.SP_SWITCH_ON_SWIPE;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import fi.iki.elonen.NanoHTTPD;
import me.rapierxbox.shellyelevatev2.helper.DeviceHelper;
import me.rapierxbox.shellyelevatev2.helper.DeviceSensorManager;
import me.rapierxbox.shellyelevatev2.helper.MediaHelper;
import me.rapierxbox.shellyelevatev2.helper.ScreenManager;
import me.rapierxbox.shellyelevatev2.helper.SwipeHelper;
import me.rapierxbox.shellyelevatev2.mqtt.MQTTServer;
import me.rapierxbox.shellyelevatev2.screensavers.ScreenSaverManager;

public class ShellyElevateApplication extends Application {
    public static HttpServer mHttpServer;

    public static DeviceHelper mDeviceHelper;
    public static DeviceSensorManager mDeviceSensorManager;
    public static SwipeHelper mSwipeHelper;
    public static ShellyElevateJavascriptInterface mShellyElevateJavascriptInterface;
    public static MQTTServer mMQTTServer;
    public static MediaHelper mMediaHelper;
    public static ScreenSaverManager mScreenSaverManager;
    public static ScreenManager mScreenManager;

    public static Context mApplicationContext;
    public static SharedPreferences mSharedPreferences;

    private static long applicationStartTime;
    private ScheduledExecutorService httpWatchdog;
    private int retryDelaySeconds = 5;

    @Override
    public void onCreate() {
        super.onCreate();
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(this));

        applicationStartTime = System.currentTimeMillis();

        mApplicationContext = getApplicationContext();
        mSharedPreferences = getSharedPreferences(SHARED_PREFERENCES_NAME, MODE_PRIVATE);

        var deviceModel = DeviceModel.getReportedDevice();
        Log.i("ShellyElevateApplication", "Device: " + deviceModel.modelName);

        mDeviceHelper = new DeviceHelper();
        mScreenSaverManager = new ScreenSaverManager(this);
        mScreenManager = new ScreenManager(this);

        // Sensors Init
        mDeviceSensorManager = new DeviceSensorManager(this);

        if (mSharedPreferences.getBoolean(SP_SWITCH_ON_SWIPE, true))
            mSwipeHelper = new SwipeHelper();

        mShellyElevateJavascriptInterface = new ShellyElevateJavascriptInterface();

        mMediaHelper = new MediaHelper();
        mHttpServer = new HttpServer();

        mMQTTServer = new MQTTServer();

        // HTTP Server
        mHttpServer = new HttpServer();
        httpWatchdog = Executors.newSingleThreadScheduledExecutor();
        if (mSharedPreferences.getBoolean(SP_HTTP_SERVER_ENABLED, true)) {
            tryStartHttpServer();

            httpWatchdog.scheduleWithFixedDelay(() -> {
                if (mHttpServer == null || !mHttpServer.isAlive()) {
                    Log.w("ShellyElevateV2", "HTTP server not alive. Restarting...");
                    tryStartHttpServer();
                }
            }, 10, 10, TimeUnit.SECONDS);
        }

        mShellyElevateJavascriptInterface = new ShellyElevateJavascriptInterface();

        // restore screen status
        mScreenManager.setScreenOn(true);
        mScreenSaverManager.stopScreenSaver();

        Log.i("ShellyElevateV2", "Application started");
    }

    private void tryStartHttpServer() {
        try {
            if (mHttpServer == null) {
                mHttpServer = new HttpServer();
            }

            mHttpServer.start(SOCKET_READ_TIMEOUT, false);
            Log.i("ShellyElevateV2", "HTTP server started on port 8080");

            // Reset exponential backoff delay
            retryDelaySeconds = 5;
        } catch (IOException e) {
            Log.e("ShellyElevateV2", "Failed to start HTTP server. Retrying in " + retryDelaySeconds + "s...", e);

            int delay = retryDelaySeconds;
            retryDelaySeconds = Math.min(retryDelaySeconds * 2, 60); // Cap at 60s

            httpWatchdog.schedule(this::tryStartHttpServer, delay, TimeUnit.SECONDS);
        }
    }

    public static long getApplicationStartTime() {
        return applicationStartTime;
    }

    @Override
    public void onTerminate() {
        mHttpServer.onDestroy();
        mDeviceSensorManager.onDestroy();

        mScreenSaverManager.stopScreenSaver();
        mScreenSaverManager.onDestroy();
        mScreenManager.setScreenOn(true);
        mScreenManager.onDestroy();

        mMQTTServer.onDestroy();
        mMediaHelper.onDestroy();

        Log.i("ShellyElevateV2", "BYEEEEEEEEEEEEEEEEEEEE :)");

        super.onTerminate();
    }
}
