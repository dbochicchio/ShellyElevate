package me.rapierxbox.shellyelevatev2.mqtt;

import static me.rapierxbox.shellyelevatev2.Constants.*;
import static me.rapierxbox.shellyelevatev2.ShellyElevateApplication.*;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import me.rapierxbox.shellyelevatev2.DeviceModel;

public class MQTTServer {

    private MqttClient mMqttClient;
    private final MemoryPersistence mMemoryPersistence;
    private final ShellyElevateMQTTCallback mShellyElevateMQTTCallback;
    private final MqttConnectionOptions mMqttConnectionsOptions;
    private final ScheduledExecutorService scheduler;
    private String clientId;
    private boolean validForConnection;
    private volatile boolean connecting = false;

    public MQTTServer() {
        mMemoryPersistence = new MemoryPersistence();
        mShellyElevateMQTTCallback = new ShellyElevateMQTTCallback();
        mMqttConnectionsOptions = new MqttConnectionOptions();
        scheduler = Executors.newScheduledThreadPool(1);

        setupClientId();
        registerSettingsReceiver();
        schedulePeriodicTempHum();

        checkCredsAndConnect();
    }

    private void setupClientId() {
        clientId = mSharedPreferences.getString(SP_MQTT_CLIENTID, "shellywalldisplay");
        if (clientId.equals("shellyelevate") || clientId.equals("shellywalldisplay") || clientId.length() <= 2) {
            clientId = "shellyelevate-" + UUID.randomUUID().toString().replaceAll("-", "").substring(2, 6);
            mSharedPreferences.edit().putString(SP_MQTT_CLIENTID, clientId).apply();
        }
    }

    private void registerSettingsReceiver() {
        BroadcastReceiver settingsChangedBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                checkCredsAndConnect();
            }
        };
        LocalBroadcastManager.getInstance(mApplicationContext)
                .registerReceiver(settingsChangedBroadcastReceiver, new IntentFilter(INTENT_SETTINGS_CHANGED));
    }

    private void schedulePeriodicTempHum() {
        scheduler.scheduleWithFixedDelay(this::publishTempAndHum, 0, 5, TimeUnit.SECONDS);
    }

    public void checkCredsAndConnect() {
        if (!isEnabled()) return;

        validForConnection =
                !mSharedPreferences.getString(SP_MQTT_PASSWORD, "").isEmpty() &&
                        !mSharedPreferences.getString(SP_MQTT_USERNAME, "").isEmpty() &&
                        !mSharedPreferences.getString(SP_MQTT_BROKER, "").isEmpty();

        connect();
    }

    public void connect() {
        if (!validForConnection || connecting || (mMqttClient != null && mMqttClient.isConnected())) return;

        connecting = true;
        Log.d("MQTT", "Connecting...");
        scheduler.execute(this::doConnect);
    }

    private void doConnect() {
        if (mMqttClient != null && mMqttClient.isConnected()) return;

        try {
            mMqttConnectionsOptions.setUserName(mSharedPreferences.getString(SP_MQTT_USERNAME, ""));
            mMqttConnectionsOptions.setPassword(mSharedPreferences.getString(SP_MQTT_PASSWORD, "").getBytes());
            mMqttConnectionsOptions.setAutomaticReconnect(true);
            mMqttConnectionsOptions.setConnectionTimeout(5);
            mMqttConnectionsOptions.setCleanStart(true);

            mMqttClient = new MqttClient(
                mSharedPreferences.getString(SP_MQTT_BROKER, "") + ":" + mSharedPreferences.getInt(SP_MQTT_PORT, 1883),
                clientId, mMemoryPersistence
            );

            // Set callback only once
            mMqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    Log.i("MQTT", "Connected to " + serverURI + ", reconnect: " + reconnect);
                    connecting = false;
                    safeOnConnected();
                }

                @Override
                public void disconnected(MqttDisconnectResponse disconnectResponse) {
                    Log.w("MQTT", "Disconnected: " + disconnectResponse.getReasonString());
                    connecting = false;
                    // automatically handled by reconnect
                }

                @Override
                public void mqttErrorOccurred(MqttException exception) {
                    Log.e("MQTT", "MQTT error occurred", exception);
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    mShellyElevateMQTTCallback.messageArrived(topic, message);
                }

                @Override
                public void deliveryComplete(IMqttToken token) {}

                @Override
                public void authPacketArrived(int reasonCode, MqttProperties properties) {}
            });

            // LWT
            MqttMessage lwtMessage = new MqttMessage("offline".getBytes());
            lwtMessage.setQos(1);
            lwtMessage.setRetained(true);
            mMqttConnectionsOptions.setWill(parseTopic(MQTT_TOPIC_STATUS), lwtMessage);

            mMqttClient.connect(mMqttConnectionsOptions);
        } catch (MqttException e) {
            Log.e("MQTT", "Connect failed, scheduling retry in 60s: ", e);
            connecting = false;
            scheduler.schedule(this::connect, 60, TimeUnit.SECONDS);
        }
    }

    private void safeOnConnected() {
        scheduler.schedule(() -> {
            if (mMqttClient != null && mMqttClient.isConnected()) {
                try {
                    // Subscriptions
                    mMqttClient.subscribe("shellyelevatev2/#", 1);
                    mMqttClient.subscribe(MQTT_TOPIC_HOME_ASSISTANT_STATUS, 1);

                    publishStatus();
                } catch (Exception e) {
                    Log.e("MQTT", "onConnected error", e);
                }
            }
        }, 150, TimeUnit.MILLISECONDS);
    }

    public void publishStatus() {
        if (mMqttClient == null || !mMqttClient.isConnected()) return;

        scheduler.execute(() -> {
            try {
                // Publish hello info
                publishHello();

                // Publish config
                publishConfig();

                // Publish online status last
                publishInternal(parseTopic(MQTT_TOPIC_STATUS), "online", 1, true);

                // Stagger sensor publishes
                scheduler.schedule(this::publishTempAndHum, 50, TimeUnit.MILLISECONDS);
                for (int num = 0; num < DeviceModel.getReportedDevice().inputs; num++) {
                    int finalNum = num;
                    scheduler.schedule(() -> publishRelay(finalNum, mDeviceHelper.getRelay(finalNum)), 100, TimeUnit.MILLISECONDS);
                }
                scheduler.schedule(() -> publishLux(mDeviceSensorManager.getLastMeasuredLux()), 150, TimeUnit.MILLISECONDS);
                scheduler.schedule(() -> publishScreenBrightness(mDeviceHelper.getScreenBrightness()), 200, TimeUnit.MILLISECONDS);

                if (DeviceModel.getReportedDevice().hasProximitySensor) {
                    scheduler.schedule(() -> publishProximity(mDeviceSensorManager.getLastMeasuredDistance()), 250, TimeUnit.MILLISECONDS);
                }

                scheduler.schedule(() -> publishSleeping(mScreenSaverManager.isScreenSaverRunning()), 300, TimeUnit.MILLISECONDS);

            } catch (Exception e) {
                Log.e("MQTT", "publishStatus failed", e);
            }
        });
    }

    public void disconnect() {
        Log.d("MQTT", "Disconnecting");
        if (mMqttClient != null && mMqttClient.isConnected()) {
            try {
                deleteConfig();
                mMqttClient.publish(parseTopic(MQTT_TOPIC_STATUS), "offline".getBytes(), 1, true);
                mMqttClient.disconnect();
            } catch (MqttException e) {
                Log.e("MQTT", "Error disconnecting MQTT client", e);
            }
        }
    }

    public boolean isEnabled() {
        return mSharedPreferences.getBoolean(SP_MQTT_ENABLED, false);
    }

    public boolean shouldSend() {
        return isEnabled() && mMqttClient != null && mMqttClient.isConnected();
    }

    public void publishInternal(String topic, String payload, int qos, boolean retained) {
        if (!shouldSend()) {
            Log.w("MQTT", "publishInternal skipped — client not connected: " + topic);
            return;
        }
        try {
            MqttMessage message = new MqttMessage(payload.getBytes());
            message.setQos(qos);
            message.setRetained(retained);
            mMqttClient.publish(topic, message);
        } catch (MqttException e) {
            Log.e("MQTT", "Failed to publish to " + topic, e);
        }
    }

    public void publishTempAndHum() {
        float temp = (float) mDeviceHelper.getTemperature();
        float hum = (float) mDeviceHelper.getHumidity();
        publishTemp(temp);
        publishHum(hum);
    }

    public void publishTemp(float temp) {
        if (temp == -999) return;
        publishInternal(parseTopic(MQTT_TOPIC_TEMP_SENSOR), String.valueOf(temp), 1, false);
    }

    public void publishHum(float hum) {
        if (hum == -999) return;
        publishInternal(parseTopic(MQTT_TOPIC_HUM_SENSOR), String.valueOf(hum), 1, false);
    }

    public void publishLux(float lux) {
        publishInternal(parseTopic(MQTT_TOPIC_LUX_SENSOR), String.valueOf(lux), 1, false);
    }

    public void publishScreenBrightness(float val) {
        publishInternal(parseTopic(MQTT_TOPIC_SCREEN_BRIGHTNESS), String.valueOf(val), 1, false);
    }
    public void publishProximity(float distance) {
        publishInternal(parseTopic(MQTT_TOPIC_PROXIMITY_SENSOR), String.valueOf(distance), 1, false);
    }

    public void publishRelay(int num, boolean state) {
        var mqttSuffix = (num >0 ? ("_" + num): "");
        publishInternal(parseTopic(MQTT_TOPIC_RELAY_STATE) + mqttSuffix, state ? "ON" : "OFF", 1, false);
    }

    public void publishSwitch(int num, boolean state) {
        var mqttSuffix = (num >0 ? ("_" + num): "");
        publishInternal(parseTopic(MQTT_TOPIC_BUTTON_STATE) + mqttSuffix, state?"PRESS":"RELEASE", 1, false);
    }

    public void publishSleeping(boolean state) {
        publishInternal(parseTopic(MQTT_TOPIC_SLEEPING_BINARY_SENSOR), state ? "ON" : "OFF", 1, false);
    }

    public void publishButton(int number) {
        long epochMillis = System.currentTimeMillis();
        String payload = "{\"last_update\": " + epochMillis + "}";
        publishInternal(parseTopic(MQTT_TOPIC_BUTTON_STATE) + "/" + number, payload, 1, false);
    }

    public void publishSwipeEvent() {
        publishInternal(parseTopic(MQTT_TOPIC_SWIPE_EVENT), "{\"event_type\": \"swipe\"}", 1, false);
    }

    public void publishHello() {
        if (!shouldSend()) return;
        try {
            JSONObject json = new JSONObject();
            json.put("name", mApplicationContext.getPackageName());

            String version = "unknown";
            try {
                PackageInfo pInfo = mApplicationContext.getPackageManager()
                        .getPackageInfo(mApplicationContext.getPackageName(), 0);
                version = pInfo.versionName;
            } catch (PackageManager.NameNotFoundException ignored) {}

            json.put("version", version);
            var device = DeviceModel.getReportedDevice();
            json.put("modelName", device.name());
            json.put("proximity", device.hasProximitySensor ? "true" : "false");

            publishInternal(parseTopic(MQTT_TOPIC_HELLO), json.toString(), 1, false);
        } catch (JSONException e) {
            Log.e("MQTT", "Error publishing hello", e);
        }
    }

    private void publishConfig() throws JSONException, MqttException {
        JSONObject configPayload = new JSONObject();

        JSONObject device = new JSONObject();
        device.put("ids", clientId);
        device.put("name", "Shelly Wall Display");
        device.put("mf", "Shelly");
        configPayload.put("dev", device);

        JSONObject origin = new JSONObject();
        origin.put("name", "ShellyElevateV2");
        origin.put("url", "https://github.com/RapierXbox/ShellyElevate");
        configPayload.put("o", origin);

        JSONObject components = new JSONObject();

        JSONObject tempSensorPayload = new JSONObject();
        tempSensorPayload.put("p", "sensor");
        tempSensorPayload.put("name", "Temperature");
        tempSensorPayload.put("state_topic", parseTopic(MQTT_TOPIC_TEMP_SENSOR));
        tempSensorPayload.put("device_class", "temperature");
        tempSensorPayload.put("unit_of_measurement", "°C");
        tempSensorPayload.put("unique_id", clientId + "_temp");
        components.put(clientId + "_temp", tempSensorPayload);

        JSONObject humSensorPayload = new JSONObject();
        humSensorPayload.put("p", "sensor");
        humSensorPayload.put("name", "Humidity");
        humSensorPayload.put("state_topic", parseTopic(MQTT_TOPIC_HUM_SENSOR));
        humSensorPayload.put("device_class", "humidity");
        humSensorPayload.put("unit_of_measurement", "%");
        humSensorPayload.put("unique_id", clientId + "_hum");
        components.put(clientId + "_hum", humSensorPayload);

        JSONObject luxSensorPayload = new JSONObject();
        luxSensorPayload.put("p", "sensor");
        luxSensorPayload.put("name", "Light");
        luxSensorPayload.put("state_topic", parseTopic(MQTT_TOPIC_LUX_SENSOR));
        luxSensorPayload.put("device_class", "illuminance");
        luxSensorPayload.put("unit_of_measurement", "lx");
        luxSensorPayload.put("unique_id", clientId + "_lux");
        components.put(clientId + "_lux", luxSensorPayload);

        if (DeviceModel.getReportedDevice().hasProximitySensor) {
            JSONObject proximitySensorPayload = new JSONObject();
            proximitySensorPayload.put("p", "sensor");
            proximitySensorPayload.put("name", "Proximity");
            proximitySensorPayload.put("state_topic", parseTopic(MQTT_TOPIC_PROXIMITY_SENSOR));
            proximitySensorPayload.put("device_class", "distance");
            proximitySensorPayload.put("unit_of_measurement", "cm");
            proximitySensorPayload.put("unique_id", clientId + "_proximity");
            components.put(clientId + "_proximity", proximitySensorPayload);
        }

        // buttons
        var buttons = DeviceModel.getReportedDevice().buttons;
        if (buttons > 0) {
            for (int i = 0; i < buttons; i++) {
                JSONObject sensorPayload = new JSONObject();
                sensorPayload.put("p", "sensor");
                sensorPayload.put("name", "Button " + i + " Last Press");
                sensorPayload.put("state_topic", parseTopic(MQTT_TOPIC_BUTTON_STATE) + "/" + i);
                sensorPayload.put("unique_id", clientId + "_button_" + i + "_lastpress");
                sensorPayload.put("device_class", "timestamp");

                // value_template to convert Unix millis to ISO 8601
                sensorPayload.put(
                        "value_template",
                        "{{ (value_json.last_update / 1000) | timestamp_custom('%Y-%m-%dT%H:%M:%S%z', true) }}"
                );

                components.put(clientId + "_button_" + i + "_lastpress", sensorPayload);
            }
        }

        for (int num = 0; num < DeviceModel.getReportedDevice().inputs; num++) {
            String mqttSuffix = (num >0 ? ("_" + num): "");
            // relay
            JSONObject relaySwitchPayload = new JSONObject();
            relaySwitchPayload.put("p", "switch");
            relaySwitchPayload.put("name", ("Relay " + (num >0 ? (" " + num): "")).trim());
            relaySwitchPayload.put("state_topic", parseTopic(MQTT_TOPIC_RELAY_STATE) + mqttSuffix);
            relaySwitchPayload.put("command_topic", parseTopic(MQTT_TOPIC_RELAY_COMMAND) + mqttSuffix);
            relaySwitchPayload.put("device_class", "outlet");
            relaySwitchPayload.put("unique_id", clientId + "_relay" + (num >0 ? ("_" + num): ""));
            components.put(clientId + "_relay" + (num >0 ? ("_" + num): ""), relaySwitchPayload);

            JSONObject buttonPayload = new JSONObject();
            buttonPayload.put("p", "button");
            buttonPayload.put("name", ("Switch " + (num > 0 ? (" " + num) : "")).trim());
            buttonPayload.put("command_topic", parseTopic(MQTT_TOPIC_SWITCH_STATE) + mqttSuffix);
            buttonPayload.put("payload_press", "PRESS");
            buttonPayload.put("payload_release", "RELEASE");
            buttonPayload.put("value_template", "{{ value }}");
            buttonPayload.put("unique_id", clientId + "_switch" + (num > 0 ? ("_" + num) : ""));
            buttonPayload.put("device_class", "restart"); // optional: or "none"
            components.put(clientId + "_switch" + (num > 0 ? ("_" + num) : ""), buttonPayload);
        }

        JSONObject sleepButtonPayload = new JSONObject();
        sleepButtonPayload.put("p", "button");
        sleepButtonPayload.put("name", "Sleep");
        sleepButtonPayload.put("command_topic", parseTopic(MQTT_TOPIC_SLEEP_BUTTON));
        sleepButtonPayload.put("unique_id", clientId + "_sleep");
        components.put(clientId + "_sleep", sleepButtonPayload);

        JSONObject wakeButtonPayload = new JSONObject();
        wakeButtonPayload.put("p", "button");
        wakeButtonPayload.put("name", "Wake");
        wakeButtonPayload.put("command_topic", parseTopic(MQTT_TOPIC_WAKE_BUTTON));
        wakeButtonPayload.put("unique_id", clientId + "_wake");
        components.put(clientId + "_wake", wakeButtonPayload);

        JSONObject refreshWebviewButtonPayload = new JSONObject();
        refreshWebviewButtonPayload.put("p", "button");
        refreshWebviewButtonPayload.put("name", "Refresh Webview");
        refreshWebviewButtonPayload.put("command_topic", parseTopic(MQTT_TOPIC_REFRESH_WEBVIEW_BUTTON));
        refreshWebviewButtonPayload.put("device_class", "restart");
        refreshWebviewButtonPayload.put("unique_id", clientId + "_refresh_webview");
        components.put(clientId + "_refresh_webview", refreshWebviewButtonPayload);

        JSONObject rebootButtonPayload = new JSONObject();
        rebootButtonPayload.put("p", "button");
        rebootButtonPayload.put("name", "Reboot");
        rebootButtonPayload.put("command_topic", parseTopic(MQTT_TOPIC_REBOOT_BUTTON));
        rebootButtonPayload.put("device_class", "restart");
        rebootButtonPayload.put("unique_id", clientId + "_reboot");
        components.put(clientId + "_reboot", rebootButtonPayload);

        JSONObject swipeEventPayload = new JSONObject();
        swipeEventPayload.put("p", "event");
        swipeEventPayload.put("name", "Swipe Event");
        swipeEventPayload.put("state_topic", parseTopic(MQTT_TOPIC_SWIPE_EVENT));
        swipeEventPayload.put("device_class", "button");
        swipeEventPayload.put("event_types", new JSONArray().put("swipe"));
        swipeEventPayload.put("unique_id", clientId + "_swipe_event");
        components.put(clientId + "_swipe_event", swipeEventPayload);

        JSONObject sleepingBinarySensorPayload = new JSONObject();
        sleepingBinarySensorPayload.put("p", "binary_sensor");
        sleepingBinarySensorPayload.put("name", "Sleeping");
        sleepingBinarySensorPayload.put("state_topic", parseTopic(MQTT_TOPIC_SLEEPING_BINARY_SENSOR));
        sleepingBinarySensorPayload.put("unique_id", clientId + "_sleeping");
        components.put(clientId + "_sleeping", sleepingBinarySensorPayload);

        // TODO: brightness as both state and control

        configPayload.put("cmps", components);

        configPayload.put("state_topic", MQTT_TOPIC_STATUS);

        mMqttClient.publish(parseTopic(MQTT_TOPIC_CONFIG_DEVICE), configPayload.toString().getBytes(), 1, true);
    }

    private void deleteConfig() throws MqttException {
        mMqttClient.publish(parseTopic(MQTT_TOPIC_CONFIG_DEVICE), "".getBytes(), 1, false);
    }

    private String parseTopic(String topic) {
        return topic.replace("%s", clientId);
    }

    public String getClientId() {
        return clientId;
    }

    public void onDestroy() {
        disconnect();
        if (scheduler != null && !scheduler.isShutdown()) scheduler.shutdown();
    }
}
