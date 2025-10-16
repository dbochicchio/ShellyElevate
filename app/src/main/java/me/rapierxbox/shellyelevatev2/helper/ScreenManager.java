package me.rapierxbox.shellyelevatev2.helper;

import static android.content.Context.MODE_PRIVATE;
import static me.rapierxbox.shellyelevatev2.Constants.INTENT_LIGHT_KEY;
import static me.rapierxbox.shellyelevatev2.Constants.INTENT_LIGHT_UPDATED;
import static me.rapierxbox.shellyelevatev2.Constants.INTENT_SCREEN_SAVER_STARTED;
import static me.rapierxbox.shellyelevatev2.Constants.INTENT_SCREEN_SAVER_STOPPED;
import static me.rapierxbox.shellyelevatev2.Constants.INTENT_TURN_SCREEN_OFF;
import static me.rapierxbox.shellyelevatev2.Constants.INTENT_TURN_SCREEN_ON;
import static me.rapierxbox.shellyelevatev2.Constants.SHARED_PREFERENCES_NAME;
import static me.rapierxbox.shellyelevatev2.Constants.SP_AUTOMATIC_BRIGHTNESS;
import static me.rapierxbox.shellyelevatev2.Constants.SP_BRIGHTNESS;
import static me.rapierxbox.shellyelevatev2.Constants.SP_MIN_BRIGHTNESS;
import static me.rapierxbox.shellyelevatev2.Constants.SP_SCREEN_SAVER_MIN_BRIGHTNESS;
import static me.rapierxbox.shellyelevatev2.ShellyElevateApplication.mDeviceHelper;

import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class ScreenManager extends BroadcastReceiver {

    private static final String TAG = "ScreenManager";
    private static final long HYSTERESIS_DELAY_MS = 3000L; // 3 seconds
    private static final long FADE_DURATION_MS = 1000L;

    public static final int MIN_BRIGHTNESS_DEFAULT = 48;
    public static final int DEFAULT_BRIGHTNESS = 255;

    // sensor
    private float lastMeasuredLux = 0.0f;

    // fade/state
    private long lastUpdateTime = 0L;
    private volatile int currentBrightness = -1; // -1 = uninitialized
    private volatile int targetBrightness = -1;
    private volatile boolean inScreenSaver = false;
    private volatile boolean screenOn = true; // explicit screen state separate from brightness

    // handler
    private final Handler fadeHandler = new Handler(Looper.getMainLooper());
    private final Runnable fadeRunnable = this::checkAndApplyBrightness;

    // prefs cached
    private final SharedPreferences prefs;
    private volatile boolean cachedAutomaticBrightness = true;
    private volatile int cachedFixedBrightness = DEFAULT_BRIGHTNESS;
    private volatile int cachedMinBrightness = MIN_BRIGHTNESS_DEFAULT;
    private volatile int cachedScreenSaverMinBrightness = MIN_BRIGHTNESS_DEFAULT;

    private final Context context;
    private final BrightnessAnimator brightnessAnimator = new BrightnessAnimator();

    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener =
            (sharedPreferences, key) -> {
                if (SP_AUTOMATIC_BRIGHTNESS.equals(key)) {
                    cachedAutomaticBrightness = sharedPreferences.getBoolean(SP_AUTOMATIC_BRIGHTNESS, true);
                } else if (SP_BRIGHTNESS.equals(key)) {
                    cachedFixedBrightness = clamp(sharedPreferences.getInt(SP_BRIGHTNESS, DEFAULT_BRIGHTNESS), 0, 255);
                } else if (SP_MIN_BRIGHTNESS.equals(key)) {
                    cachedMinBrightness = clamp(sharedPreferences.getInt(SP_MIN_BRIGHTNESS, MIN_BRIGHTNESS_DEFAULT), 0, 255);
                } else if (SP_SCREEN_SAVER_MIN_BRIGHTNESS.equals(key)) {
                    cachedScreenSaverMinBrightness = clamp(sharedPreferences.getInt(SP_SCREEN_SAVER_MIN_BRIGHTNESS, MIN_BRIGHTNESS_DEFAULT), 0, 255);
                }
            };

    public ScreenManager(Context ctx) {
        this.context = ctx.getApplicationContext();
        prefs = context.getSharedPreferences(SHARED_PREFERENCES_NAME, MODE_PRIVATE);
        loadPrefsToCache();

        prefs.registerOnSharedPreferenceChangeListener(prefsListener);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(INTENT_SCREEN_SAVER_STARTED);
        intentFilter.addAction(INTENT_SCREEN_SAVER_STOPPED);
        intentFilter.addAction(INTENT_TURN_SCREEN_ON);
        intentFilter.addAction(INTENT_TURN_SCREEN_OFF);
        intentFilter.addAction(INTENT_LIGHT_UPDATED);

        LocalBroadcastManager.getInstance(context).registerReceiver(this, intentFilter);
    }

    private void loadPrefsToCache() {
        cachedAutomaticBrightness = prefs.getBoolean(SP_AUTOMATIC_BRIGHTNESS, true);
        cachedFixedBrightness = clamp(prefs.getInt(SP_BRIGHTNESS, DEFAULT_BRIGHTNESS), 0, 255);
        cachedMinBrightness = clamp(prefs.getInt(SP_MIN_BRIGHTNESS, MIN_BRIGHTNESS_DEFAULT), 0, 255);
        cachedScreenSaverMinBrightness = clamp(prefs.getInt(SP_SCREEN_SAVER_MIN_BRIGHTNESS, MIN_BRIGHTNESS_DEFAULT), 0, 255);
    }

    public void onDestroy() {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(this);
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener);
        fadeHandler.removeCallbacksAndMessages(null);
        brightnessAnimator.cancel();
    }

    public void setScreenOn(boolean on) {
        // keep explicit boolean for state; do not conflate with brightness
        screenOn = on;
        if (!on) {
            // keep brightness state consistent with screen off
            currentBrightness = 0;
        }
        // keep using existing mDeviceHelper (per request to avoid DI change)
        mDeviceHelper.setScreenOn(on);
        // ensure device brightness is in sync
        if (!on) {
            mDeviceHelper.setScreenBrightness(0);
        }
    }

    private boolean automaticBrightness() {
        return cachedAutomaticBrightness;
    }

    private int fixedBrightness() {
        return cachedFixedBrightness;
    }

    @Override
    @MainThread
    public void onReceive(Context ctx, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        switch (action) {
            case INTENT_SCREEN_SAVER_STARTED:
                updateScreenSaverState(true);
                break;
            case INTENT_SCREEN_SAVER_STOPPED:
                updateScreenSaverState(false);
                break;
            case INTENT_TURN_SCREEN_ON:
                setScreenOn(true);
                break;
            case INTENT_TURN_SCREEN_OFF:
                setScreenOn(false);
                break;
            case INTENT_LIGHT_UPDATED:
                float lux = intent.getFloatExtra(INTENT_LIGHT_KEY, 0.0f);
                if (Float.isNaN(lux) || lux < 0f) lux = 0f; // sanitize
                lastMeasuredLux = lux;
                updateBrightness();
                break;
        }
    }

    private synchronized void updateBrightness() {
        // If screen is off or screensaver is active, force brightness to 0
        if (!screenOn || inScreenSaver) {
            targetBrightness = 0;
            // respect request: keep using mDeviceHelper directly
            mDeviceHelper.setScreenBrightness(0);
            Log.d(TAG, "Screen off or screensaver: setting brightness to 0");
            // cancel pending fades
            fadeHandler.removeCallbacks(fadeRunnable);
            return;
        }

        int desiredBrightness;
        if (automaticBrightness()) {
            desiredBrightness = getScreenBrightnessFromLux(lastMeasuredLux);
        } else {
            desiredBrightness = fixedBrightness();
        }

        desiredBrightness = clamp(desiredBrightness, 0, 255);

        if (desiredBrightness != targetBrightness) {
            targetBrightness = desiredBrightness;
            lastUpdateTime = System.currentTimeMillis();
            // cancel any pending fade to avoid race with outdated tasks
            fadeHandler.removeCallbacks(fadeRunnable);
            // post hysteresis delayed task on main looper
            fadeHandler.postDelayed(fadeRunnable, HYSTERESIS_DELAY_MS);
            Log.d(TAG, "Desired brightness: " + desiredBrightness + ", targetBrightness: " + targetBrightness + ", lastUpdateTime: " + lastUpdateTime + ", currentBrightness: " + currentBrightness);
        }
    }

    private synchronized void checkAndApplyBrightness() {
        // force in case of brightness 0 (ensure immediate apply to 0)
        boolean force = currentBrightness != 0 && targetBrightness == 0;
        long now = System.currentTimeMillis();

        if (now - lastUpdateTime >= HYSTERESIS_DELAY_MS || force) {
            if (currentBrightness == -1) {
                currentBrightness = targetBrightness;
                mDeviceHelper.setScreenBrightness(clamp(currentBrightness, 0, 255));
            } else if (currentBrightness != targetBrightness) {
                animateBrightnessTransition(currentBrightness, targetBrightness);
            } else {
                Log.d(TAG, "No brightness change needed.");
            }
        } else {
            // A possible in-flight update; re-schedule to ensure we eventually apply.
            fadeHandler.removeCallbacks(fadeRunnable);
            long delay = Math.max(0, HYSTERESIS_DELAY_MS - (now - lastUpdateTime));
            fadeHandler.postDelayed(fadeRunnable, delay);
        }
    }

    private void animateBrightnessTransition(int from, int to) {
        // ensure bounds
        from = clamp(from, 0, 255);
        to = clamp(to, 0, 255);

        // Use the existing animator class if available; fallback to ValueAnimator if needed.
        try {
            brightnessAnimator.animate(from, to, value -> {
                mDeviceHelper.setScreenBrightness(clamp(value, 0, 255));
                currentBrightness = value;
            });
        } catch (Throwable t) {
            // fallback safe path: immediate set (shouldn't happen often)
            Log.d(TAG, "BrightnessAnimator failed, applying immediate value. " + t.getMessage());
            currentBrightness = to;
            mDeviceHelper.setScreenBrightness(to);
        }
    }

    private int getScreenBrightnessFromLux(float lux) {
        // sanitize input
        if (Float.isNaN(lux) || lux < 0f) lux = 0f;

        int minBrightness = inScreenSaver ? cachedScreenSaverMinBrightness : cachedMinBrightness;
        minBrightness = clamp(minBrightness, 0, 255);

        if (lux >= 500f) return 255;
        if (lux <= 30f) return minBrightness;

        double slope = (255.0 - minBrightness) / (500.0 - 30.0);
        double computed = minBrightness + slope * (lux - 30.0);
        return clamp((int) Math.round(computed), 0, 255);
    }

    private synchronized void updateScreenSaverState(boolean newState) {
        this.inScreenSaver = newState;
        updateBrightness();
    }

    private static int clamp(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    /**
     * Minimal BrightnessAnimator wrapper expected by the class.
     */
    static class BrightnessAnimator {
        private ValueAnimator animator;

        void animate(int from, int to, IntConsumer onUpdate) {
            cancel();
            animator = ValueAnimator.ofInt(from, to);
            animator.setDuration(Math.max(0, ScreenManager.FADE_DURATION_MS));
            animator.addUpdateListener(animation -> {
                int value = (Integer) animation.getAnimatedValue();
                onUpdate.accept(value);
            });
            animator.start();
        }

        void cancel() {
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
        }
    }

    @FunctionalInterface
    interface IntConsumer {
        void accept(int value);
    }
}
