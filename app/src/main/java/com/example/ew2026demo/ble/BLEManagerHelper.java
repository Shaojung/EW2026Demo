package com.example.ew2026demo.ble;

import android.content.Context;

import java.util.UUID;

public class BLEManagerHelper {

    public static BLEManager createDefaultBLECounterManager(Context context) {
        BLEManager manager = new BLEManager(context);
        UUID characteristicUuid = UUID.fromString("0000ff11-0000-1000-8000-00805f9b34fb");
        manager.setTargetDevice("GATT Counter", characteristicUuid);
        return manager;
    }

    public static boolean sendData(BLEManager manager) {
        if (manager == null) {
            return false;
        }
        return manager.sendDataToCharacteristic();
    }
}
