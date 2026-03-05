package com.example.ew2026demo.ble;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

public class BLEManager {
    private static final String TAG = "BLEManager";

    private Context context;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt = null;
    private BLEConnectionCallback connectionCallback;
    private Handler handler = new Handler(Looper.getMainLooper());

    private String targetDeviceName = "LE Counter";
    private String targetDeviceAddress = "01:23:45:67:89:AB";
    private UUID targetCharacteristicUuid = UUID.fromString("0000ff11-0000-1000-8000-00805f9b34fb");
    private String dataToSend;

    private boolean isConnected = false;
    private boolean isDiscoveringServices = false;
    private boolean isScanning = false;
    private BluetoothLeScanner bleScanner;

    private boolean isBrowseScanning = false;
    private final LinkedHashMap<String, ScanResult> discoveredDevicesMap = new LinkedHashMap<>();

    public BLEManager(Context context) {
        this.context = context;
        initializeBluetooth();
    }

    @SuppressLint("MissingPermission")
    private void initializeBluetooth() {
        BluetoothManager bluetoothManager =
                (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
            if (bluetoothAdapter != null) {
                bleScanner = bluetoothAdapter.getBluetoothLeScanner();
            }
        }
    }

    public void setTargetDevice(String deviceName, UUID characteristicUuid) {
        this.targetDeviceName = deviceName;
        this.targetCharacteristicUuid = characteristicUuid;
    }

    public void setTargetDeviceAddress(String address) {
        this.targetDeviceAddress = address;
    }

    public void setDataToSend(String data) {
        this.dataToSend = data;
    }

    @SuppressLint("MissingPermission")
    public void startDeviceScan() {
        if (bleScanner == null || isScanning) {
            Log.w(TAG, "Scanner unavailable or already scanning");
            return;
        }

        isScanning = true;
        Log.i(TAG, "Start scanning for BLE device: " + targetDeviceName);

        bleScanner.startScan(scanCallback);
        handler.postDelayed(this::stopDeviceScan, 30000);
    }

    @SuppressLint("MissingPermission")
    public void connectToDevice(BluetoothDevice device) {
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth adapter not initialized");
            return;
        }

        bluetoothGatt = device.connectGatt(context, false, gattCallback);
        Log.i(TAG, "Connecting to device: " + device.getName());
    }

    @SuppressLint("MissingPermission")
    public void stopDeviceScan() {
        if (bleScanner != null && isScanning) {
            bleScanner.stopScan(scanCallback);
            isScanning = false;
            Log.i(TAG, "Stopped BLE scan");
        }
    }

    @SuppressLint("MissingPermission")
    public void startBrowseScan() {
        if (bleScanner == null || isBrowseScanning) {
            Log.w(TAG, "Scanner unavailable or already browse-scanning");
            return;
        }

        discoveredDevicesMap.clear();
        isBrowseScanning = true;
        Log.i(TAG, "Start browse scan");

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .build();
        bleScanner.startScan(null, settings, browseScanCallback);
        handler.postDelayed(this::stopBrowseScan, 30000);
    }

    @SuppressLint("MissingPermission")
    public void stopBrowseScan() {
        if (bleScanner != null && isBrowseScanning) {
            bleScanner.stopScan(browseScanCallback);
            isBrowseScanning = false;
            Log.i(TAG, "Stopped browse scan");
        }
    }

    public List<ScanResult> getDiscoveredDevices() {
        return new ArrayList<>(discoveredDevicesMap.values());
    }

    private final ScanCallback browseScanCallback = new ScanCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            String address = device.getAddress();
            discoveredDevicesMap.put(address, result);

            if (connectionCallback != null) {
                connectionCallback.onDeviceFound(device, result.getRssi());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.e(TAG, "Browse scan failed: " + errorCode);
            isBrowseScanning = false;
        }
    };

    @SuppressLint("MissingPermission")
    public boolean sendDataToCharacteristic() {
        if (bluetoothGatt == null || !isConnected) {
            Log.e(TAG, "Device not connected");
            return false;
        }

        if (targetCharacteristicUuid == null || dataToSend == null) {
            Log.e(TAG, "Target characteristic or data not set");
            return false;
        }

        for (BluetoothGattService service : bluetoothGatt.getServices()) {
            BluetoothGattCharacteristic characteristic =
                    service.getCharacteristic(targetCharacteristicUuid);
            if (characteristic != null) {
                // 強制使用 UTF-8 編碼發送，避免 C 端解析截斷
                characteristic.setValue(dataToSend.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                boolean success = bluetoothGatt.writeCharacteristic(characteristic);
                if (success) {
                    Log.i(TAG, "Data sent: " + dataToSend);
                    return true;
                } else {
                    Log.e(TAG, "Write characteristic failed");
                    return false;
                }
            }
        }

        Log.e(TAG, "Target characteristic not found: " + targetCharacteristicUuid);
        return false;
    }

    @SuppressLint("MissingPermission")
    public boolean sendDataToCharacteristic(String data) {
        this.dataToSend = data;
        return sendDataToCharacteristic();
    }

    @SuppressLint("MissingPermission")
    public void disconnect() {
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
        isConnected = false;
        Log.i(TAG, "Device disconnected");
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void setConnectionCallback(BLEConnectionCallback callback) {
        this.connectionCallback = callback;
    }

    private ScanCallback scanCallback = new ScanCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            String deviceAddress = device.getAddress();
            String deviceName = device.getName();

            if (deviceAddress != null && (deviceAddress.equals(targetDeviceAddress)
                    || (deviceName != null && deviceName.contains(targetDeviceName)))) {
                Log.i(TAG, "Found target device: " + deviceAddress + " (" + deviceName + ")");
                stopDeviceScan();
                connectToDevice(device);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.e(TAG, "Scan failed: " + errorCode);
            isScanning = false;
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Connection failed: status=" + status);
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(context, "Connection failed: status=" + status, Toast.LENGTH_LONG).show());
                gatt.close();
            }
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.i(TAG, "Device connected");
                isConnected = true;
                isDiscoveringServices = true;
                
                // 根據 BLE_ATT_MTU_Guide.md 請求較大的 MTU (128 bytes)
                // 預設 MTU 只有 23 bytes，不足以傳送完整的導航訊息
                gatt.requestMtu(128);

                if (connectionCallback != null) {
                    connectionCallback.onDeviceConnected(gatt.getDevice());
                }
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.i(TAG, "Device disconnected");
                isConnected = false;
                isDiscoveringServices = false;

                if (connectionCallback != null) {
                    connectionCallback.onDeviceDisconnected(gatt.getDevice());
                }
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "MTU negotiated: " + mtu + ", payload: " + (mtu - 3));
            } else {
                Log.w(TAG, "MTU negotiation failed, status: " + status);
            }
            // 協商結束後才開始發現服務
            gatt.discoverServices();
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            isDiscoveringServices = false;

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services discovered");
                if (connectionCallback != null) {
                    connectionCallback.onServicesDiscovered(gatt.getServices());
                }
            } else {
                Log.e(TAG, "Service discovery failed: " + status);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic,
                                          int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Characteristic write success");
            } else {
                Log.e(TAG, "Characteristic write failed: " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            if (connectionCallback != null) {
                connectionCallback.onCharacteristicChanged(characteristic);
            }
        }
    };

    public interface BLEConnectionCallback {
        void onDeviceConnected(BluetoothDevice device);
        void onDeviceDisconnected(BluetoothDevice device);
        void onServicesDiscovered(java.util.List<BluetoothGattService> services);
        void onCharacteristicChanged(BluetoothGattCharacteristic characteristic);
        default void onDeviceFound(BluetoothDevice device, int rssi) {}
    }
}
