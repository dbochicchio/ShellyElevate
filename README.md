# ShellyElevate
> [!IMPORTANT]
> Make sure to update your Home Assistant config.yaml file to comply with the new API. You can find the new yaml in the wiki.

> [!CAUTION]
> All content in this repository is provided "as is" and may render your device unusable. Always exercise caution when working with your device. No warranty or guarantee is provided.

Shelly Elevate is an app designed for the Shelly Wall Display, codenamed Stargate, that adds full Home Assistant functionality to the device. The Wiki also provides a detailed tutorial on hacking your device, installing a launcher, configuring Shelly Elevate, and integrating it with Home Assistant.<br>

https://github.com/user-attachments/assets/adf46edd-9bf1-45da-b553-bf7781d17fbd

### Features
* full screen Home Assistant control
* automatic Home Assistant IP detection
* autostart
* swipe to switch
* full access to sensors and the relay over a api
* playing sound files over the api
* hidden settings
* automatic brightness
* multiple screen savers with settable delay
* changing settings over the api
* lite mode
* support for all displays
* viewing any url

And of course you can disable each feature completely.

## Supported Devices

ShellyElevate supports the entire Shelly Wall Display family with automatic hardware detection:

### V1 Generation
- **Shelly Wall Display** (SAWD-0A1XX10EU1) - Original model, no proximity sensor
- **Shelly Wall Display 2** (SAWD-1A1XX10EU1) - Enhanced with proximity sensor
- **Shelly Wall Display X2** (SAWD-2A1XX10EU1) - With proximity sensor

### V2 Generation
- **Shelly Wall Display XL** (SAWD-3A1XE10EU2) - Large display with proximity sensor and 4 hardware buttons
- **Shelly Wall Display U1** (SAWD-4A1XE10US0) - US model with proximity sensor *(coming soon)*
- **Shelly Wall Display X2i** (SAWD-5A1XX10EU0) - With proximity sensor *(coming soon)*
- **Shelly Wall Display XLi** (SAWD-6A1XX10EU0) - Large display with proximity sensor and 4 hardware buttons *(coming soon)*

Each device model has its own calibrated temperature/humidity offsets, relay configuration, button mapping, and sensor availability that are handled automatically by the app.

## Sensors & Hardware Access

ShellyElevate provides access to various sensors depending on your device model:

### Environmental Sensors
- **Temperature** - Read ambient temperature from SHT3x sensor
- **Humidity** - Monitor relative humidity levels
- **Light (Lux)** - Ambient light sensor for automatic brightness adjustment
- **Proximity** - Detect user presence to wake the display

### Controllable Hardware
- **Relays** - Toggle connected relays (0-2 depending on device model)
- **Screen Brightness** - Manual or automatic adjustment based on ambient light
- **Screen Power** - Wake/sleep display on demand

All sensors publish state changes automatically when MQTT is enabled and are accessible via the HTTP API.

## HTTP API

ShellyElevate includes a built-in HTTP server (port 8080) that can be enabled in settings. The API provides complete control over the device:

### Endpoints

#### `/settings` - Configuration Management
- `GET /settings` - Retrieve all current settings
- `POST /settings` - Update settings (JSON payload with key-value pairs)

#### `/device` - Device Control
- `GET /device/status` - Get relay states, sensor readings, and device info
- `POST /device/relay/<id>/on` - Turn relay on
- `POST /device/relay/<id>/off` - Turn relay off
- `POST /device/relay/<id>/toggle` - Toggle relay state
- `GET /device/temp` - Get temperature reading
- `GET /device/humidity` - Get humidity reading
- `GET /device/lux` - Get light sensor value
- `GET /device/proximity` - Get proximity sensor state
- `POST /device/wake` - Wake the display
- `POST /device/sleep` - Put display to sleep
- `POST /device/reboot` - Reboot the device

#### `/media` - Audio Playback
- `POST /media/play` - Play audio file (requires file URL)
- `POST /media/pause` - Pause playback
- `POST /media/resume` - Resume playback
- `POST /media/stop` - Stop playback
- `POST /media/volume` - Set volume (0.0-1.0)

#### `/webview` - WebView Control
- `POST /webview/refresh` - Reload the WebView
- `POST /webview/inject` - Inject JavaScript code (requires `code` parameter)

All endpoints return JSON responses with status and data fields.

## MQTT Integration

ShellyElevate includes native MQTT support with Home Assistant auto-discovery. Configure your MQTT broker in settings to enable this feature.

### Published Topics
The app publishes to the following topic structure: `shellyelevate/<device_id>/<sensor>`

#### State Topics
- `switch/state` - Relay states (JSON array)
- `sensor/temperature/state` - Temperature in °C
- `sensor/humidity/state` - Relative humidity percentage
- `sensor/lux/state` - Light level in lux
- `sensor/proximity/state` - Proximity detection (on/off)
- `sensor/brightness/state` - Current screen brightness (0-255)

#### Event Topics
- `button/<id>` - Hardware button press events
- `swipe` - Swipe gesture events
- `availability` - Online/offline status

### Command Topics
Subscribe to these topics to control the device:

- `switch/<id>/set` - Control relays (ON/OFF)
- `screen/command` - Wake/sleep commands
- `device/command` - Reboot device
- `webview/command` - Refresh or inject JavaScript

### Home Assistant Auto-Discovery
When MQTT is enabled, ShellyElevate automatically publishes Home Assistant discovery configs for all sensors and controls, making integration seamless.

## WebView JavaScript Interface

An extended JavaScript interface can be enabled to allow the loaded webpage to interact with device hardware:

```javascript
// Access relays
Android.getRelayState(relayId);
Android.setRelayState(relayId, state);

// Read sensors
Android.getTemperature();
Android.getHumidity();
Android.getLux();

// Control screen
Android.setBrightness(level);
Android.startScreenSaver();
Android.stopScreenSaver();
```

This feature is disabled by default and can be enabled in settings for advanced use cases.

## Screen Savers

Multiple screen saver modes are available with configurable idle timeout:
- **Clock** - Simple clock display
- **Photo Frame** - Display images
- **Blank** - Turn screen off
- **Custom** - Developer-defined savers

Screen savers can be triggered by idle timeout or proximity sensor, and support wake-on-interaction.

## Kiosk Mode

ShellyElevate runs in full kiosk mode with:
- Foreground service watchdog to restart if MainActivity crashes
- Boot receiver to auto-start on device boot
- Crash handler with logging and auto-recovery
- Gesture-based access to hidden settings (10 taps on left + right overlay)
- Hardware button remapping for custom actions

## Contributing

If you'd like to contribute or have a feature request, please do so by creating a pull request or opening an issue.

### Don't know where to start?
Hack your display using the [guide](https://github.com/RapierXbox/ShellyElevate/wiki/Jailbreak) or check out the [releases](https://github.com/RapierXbox/ShellyElevate/releases).
If you want to add the modified display to Home Assistant, check out [this](https://github.com/RapierXbox/ShellyElevate/wiki/Integration-into-Home-Assistant).
