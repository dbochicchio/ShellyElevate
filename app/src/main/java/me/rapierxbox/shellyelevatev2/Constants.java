package me.rapierxbox.shellyelevatev2;

import java.util.HashMap;

public class Constants {
    public static final String SHARED_PREFERENCES_NAME = "ShellyElevateV2";

    public static final String SP_SETTINGS_EVER_SHOWN = "settingEverShown";
    public static final String SP_WEBVIEW_URL = "webviewUrl";
    public static final String SP_IGNORE_SSL_ERRORS = "ignoreSslErrors";
    public static final String SP_HTTP_SERVER_ENABLED = "httpServer";
    public static final String SP_SWITCH_ON_SWIPE = "switchOnSwipe";
    public static final String SP_AUTOMATIC_BRIGHTNESS = "automaticBrightness";
    public static final String SP_MIN_BRIGHTNESS = "minBrightness";
    public static final String SP_BRIGHTNESS = "brightness";
    public static final String SP_SCREEN_SAVER_ENABLED = "screenSaver";
    public static final String SP_SCREEN_SAVER_DELAY = "screenSaverDelay";
    public static final String SP_SCREEN_SAVER_ID = "screenSaverId";
    public static final String SP_WAKE_ON_PROXIMITY = "wakeOnProximity";
    public static final String SP_LITE_MODE = "liteMode";
    public static final String SP_EXTENDED_JAVASCRIPT_INTERFACE = "extendedJavascriptInterface";
    public static final String SP_DEVICE = "device";

    public static final String SP_MQTT_ENABLED = "mqttEnabled";
    public static final String SP_MQTT_BROKER = "mqttBroker";
    public static final String SP_MQTT_PORT = "mqttPort";
    public static final String SP_MQTT_USERNAME = "mqttUsername";
    public static final String SP_MQTT_PASSWORD = "mqttPassword";
    public static final String SP_MQTT_DEVICE_ID = "mqttDeviceId";

    public static final String SP_DEPRECATED_HA_IP = "homeAssistantIp";

    public static final String ACTION_USER_INTERACTION = "shellyelevate.ACTION_USER_INTERACTION";

    public static final String INTENT_SETTINGS_CHANGED = "me.rapierxbox.shellyelevatev2.SETTINGS_CHANGED";
    public static final String INTENT_WEBVIEW_INJECT_JAVASCRIPT = "me.rapierxbox.shellyelevatev2.WEBVIEW_INJECT_JAVASCRIPT";
    public static final String INTENT_END_SCREENSAVER = "me.rapierxbox.shellyelevatev2.END_SCREENSAVER";

    public static final String MQTT_TOPIC_CONFIG_DEVICE = "homeassistant/device/%s/config";
    public static final String MQTT_TOPIC_STATUS = "shellyelevatev2/%s/status";

    public static final String MQTT_TOPIC_TEMP_SENSOR = "shellyelevatev2/%s/temp";
    public static final String MQTT_TOPIC_HUM_SENSOR = "shellyelevatev2/%s/hum";
    public static final String MQTT_TOPIC_LUX_SENSOR = "shellyelevatev2/%s/lux";
    public static final String MQTT_TOPIC_PROXIMITY_SENSOR = "shellyelevatev2/%s/proximity";
    public static final String MQTT_TOPIC_RELAY_STATE = "shellyelevatev2/%s/relay_state";
    public static final String MQTT_TOPIC_RELAY_COMMAND = "shellyelevatev2/%s/relay_command";
    public static final String MQTT_TOPIC_SLEEP_BUTTON = "shellyelevatev2/%s/sleep";
    public static final String MQTT_TOPIC_WAKE_BUTTON = "shellyelevatev2/%s/wake";
    public static final String MQTT_TOPIC_REFRESH_WEBVIEW_BUTTON = "shellyelevatev2/%s/refresh_webview";
    public static final String MQTT_TOPIC_REBOOT_BUTTON = "shellyelevatev2/%s/reboot";
    public static final String MQTT_TOPIC_SWIPE_EVENT = "shellyelevatev2/%s/swipe_event";
    public static final String MQTT_TOPIC_SLEEPING_BINARY_SENSOR = "shellyelevatev2/%s/sleeping";

    public static final String MQTT_TOPIC_HOME_ASSISTANT_STATUS = "homeassistant/status";

    public static final String DEVICE_STARGATE = "SAWD-0A1XX10EU1"; // old one
    public static final String DEVICE_ATLANTIS = "SAWD-1A1XX10EU1"; // new one
    public static final String DEVICE_PEGASUS = "SAWD-2A1XX10EU1"; // wide display

    public static final HashMap<String, Boolean> hasProximitySensor = new HashMap<>() {
        {
            put(DEVICE_STARGATE, false);
            put(DEVICE_ATLANTIS, false);
            put(DEVICE_PEGASUS, true);
        }
    };
    public static final HashMap<String, Double> temperatureOffset = new HashMap<>() {
        {
            put(DEVICE_STARGATE, -2.7d);
            put(DEVICE_ATLANTIS, -1.1d);
            put(DEVICE_PEGASUS, -2.6d);
        }
    };

    public static final HashMap<String, Double> humidityOffset = new HashMap<>() {
        {
            put(DEVICE_STARGATE, 7.0d);
            put(DEVICE_ATLANTIS, 3.0d);
            put(DEVICE_PEGASUS, 8.0d);
        }
    };
}
