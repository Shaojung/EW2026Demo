package com.example.ew2026demo.ble;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.ew2026demo.protocol.HysProtocol;

public class HeartbeatTask {
    private static final String TAG = "HeartbeatTask";
    private static final int HEARTBEAT_INTERVAL = 500; // 500ms

    private final BLEManager bleManager;
    private Handler handler;
    private Runnable heartbeatRunnable;
    private boolean isRunning = false;

    public HeartbeatTask(BLEManager bleManager) {
        this.bleManager = bleManager;
        this.handler = new Handler(Looper.getMainLooper());
    }

    public void start() {
        if (isRunning) return;
        isRunning = true;

        heartbeatRunnable = new Runnable() {
            @Override
            public void run() {
                sendHeartbeat();
                handler.postDelayed(this, HEARTBEAT_INTERVAL);
            }
        };
        handler.post(heartbeatRunnable);
        Log.i(TAG, "Heartbeat started");
    }

    public void stop() {
        if (!isRunning) return;
        isRunning = false;

        if (heartbeatRunnable != null) {
            handler.removeCallbacks(heartbeatRunnable);
        }
        Log.i(TAG, "Heartbeat stopped");
    }

    public boolean isRunning() {
        return isRunning;
    }

    private void sendHeartbeat() {
        if (bleManager != null && bleManager.isConnected()) {
            bleManager.sendDataToCharacteristic(HysProtocol.buildHeartbeat());
        }
    }
}
