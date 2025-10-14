package me.rapierxbox.shellyelevatev2;

import android.os.Build;

import androidx.annotation.NonNull;

import java.util.Arrays;

public enum DeviceModel {
    //V1
    STARGATE("Stargate", "Shelly Wall Display", "SAWD-0A1XX10EU1", false, -2.7d, 7.0d, 0, 1), // Old One
    ATLANTIS("Atlantis", "Shelly Wall Display 2", "SAWD-1A1XX10EU1", true, -1.1d, 3.0d, 0, 1), // New One
    PEGASUS("Pegasus",  "Shelly Wall Display X2", "SAWD-2A1XX10EU1", true, -2.6d, 8.0d, 0, 1),

    //V2
    BLAKE("Blake", "Shelly Wall Display XL","SAWD-3A1XE10EU2", true, -1.2d, 10.0d, 4, 1),
    MAVERICK("Maverick", "Shelly Wall Display U1", "SAWD-4A1XE10US0", true, 0d, 0.0d, 0, 1), // TODO: not yet avaiable
    JENNA("Jenna", "Shelly Wall Display X2i", "SAWD-5A1XX10EU0", true, 0d, 0.0d, 0, 1), // TODO: not yet avaiable
    CALLY("Jenna","Shelly Wall Display XLi", "SAWD-6A1XX10EU0", true, 0d, 0.0d, 4, 1), // TODO: not yet avaiable
    ;

    private final String model;
    public final String modelName;
    public final String friendlyName;
    public final boolean hasProximitySensor;
    public final double temperatureOffset;
    public final double humidityOffset;
    public final int buttons;
    public final int inputs;

    DeviceModel(String model, String friendlyName, String modelName, boolean hasProximitySensor, double temperatureOffset, double humidityOffset, int buttons, int inputs) {
        this.model = model;
        this.modelName = modelName;
        this.friendlyName = friendlyName;
	    this.hasProximitySensor = hasProximitySensor;
        this.temperatureOffset = temperatureOffset;
        this.humidityOffset = humidityOffset;
        this.buttons = buttons;
        this.inputs = inputs;
    }

    public static DeviceModel getReportedDevice(){
        return Arrays.stream(DeviceModel.values()).filter(deviceType -> deviceType.model.equals(Build.MODEL)).findFirst().orElse(DeviceModel.STARGATE);
    }

    @NonNull
    @Override
    public String toString() {
        //We are using this with the adapter in SettingsActivity
        return modelName;
    }
}
