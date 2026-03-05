package com.example.ew2026demo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.ew2026demo.ble.BLEManager;
import com.example.ew2026demo.ble.HeartbeatTask;
import com.example.ew2026demo.map.MapManager;
import com.example.ew2026demo.protocol.Direction;
import com.example.ew2026demo.protocol.HysProtocol;
import com.example.ew2026demo.simulation.RouteData;
import com.example.ew2026demo.simulation.RouteWaypoint;
import com.example.ew2026demo.simulation.SimulationEngine;

import org.osmdroid.views.MapView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity
        implements BLEManager.BLEConnectionCallback, SimulationEngine.SimulationListener {

    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;

    // BLE
    private BLEManager bleManager;
    private HeartbeatTask heartbeatTask;

    // Map
    private MapManager mapManager;

    // Simulation
    private SimulationEngine simulationEngine;
    private List<RouteWaypoint> route;

    // UI — BLE toolbar
    private ImageView bleStatusIcon;
    private TextView bleStatusText;
    private Button btnConnect, btnDisconnect;

    // UI — Navigation panel
    private ImageView navArrow;
    private TextView navDirectionText, navDistanceText, navTotalDistText;
    private TextView navSpeedMultiplier;
    private Button btnSpeedDown, btnSpeedUp;

    // UI — Control bar
    private Button btnStart, btnPause, btnReset;
    private SeekBar speedSeekBar;
    private TextView speedLabel;

    // UI — Log
    private TextView logTextView;
    private ScrollView logScrollView;

    // Browse dialog
    private AlertDialog browseDialog;
    private ArrayAdapter<String> browseAdapter;
    private final List<String> browseDisplayList = new ArrayList<>();
    private final List<BluetoothDevice> browseDeviceList = new ArrayList<>();
    private boolean browseUpdatePending = false;
    private static final long BROWSE_UPDATE_INTERVAL_MS = 300;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Pre-initialize osmdroid configuration BEFORE setContentView
        org.osmdroid.config.IConfigurationProvider config = org.osmdroid.config.Configuration.getInstance();
        config.setUserAgentValue("EW2026Demo");

        // Use internal files directory for stability on Android 10+
        File basePath = new File(getFilesDir(), "osmdroid");
        config.setOsmdroidBasePath(basePath);
        config.setOsmdroidTileCache(new File(basePath, "tiles"));

        setContentView(R.layout.activity_main);

        initViews();
        initBLE();
        initMap();
        initSimulation();
        setupListeners();

        addLog("EW2026 Demo started");
        addLog(String.format(Locale.US, "Route: %.0f m, %d waypoints",
                RouteData.getTotalDistance(), route.size()));

        if (!checkPermissions()) {
            requestPermissions();
        }
    }

    private void initViews() {
        // BLE toolbar
        bleStatusIcon = findViewById(R.id.bleStatusIcon);
        bleStatusText = findViewById(R.id.bleStatusText);
        btnConnect = findViewById(R.id.btnConnect);
        btnDisconnect = findViewById(R.id.btnDisconnect);

        // Navigation panel
        navArrow = findViewById(R.id.navArrow);
        navDirectionText = findViewById(R.id.navDirectionText);
        navDistanceText = findViewById(R.id.navDistanceText);
        navTotalDistText = findViewById(R.id.navTotalDistText);
        navSpeedMultiplier = findViewById(R.id.navSpeedMultiplier);
        btnSpeedDown = findViewById(R.id.btnSpeedDown);
        btnSpeedUp = findViewById(R.id.btnSpeedUp);

        // Control bar
        btnStart = findViewById(R.id.btnStart);
        btnPause = findViewById(R.id.btnPause);
        btnReset = findViewById(R.id.btnReset);
        speedSeekBar = findViewById(R.id.speedSeekBar);
        speedLabel = findViewById(R.id.speedLabel);

        // Log
        logTextView = findViewById(R.id.logTextView);
        logScrollView = findViewById(R.id.logScrollView);
    }

    private void initBLE() {
        bleManager = new BLEManager(this);
        bleManager.setConnectionCallback(this);
        heartbeatTask = new HeartbeatTask(bleManager);
    }

    private void initMap() {
        MapView mapView = findViewById(R.id.mapView);
        // Set a background color to see if the view is present
        mapView.setBackgroundColor(0xFFEEEEEE); 
        
        // Android 12/13 fix: Force software rendering for the MapView
        mapView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        mapManager = new MapManager(this, mapView);
        // Pass log to MapManager
        mapManager.setLogCallback(this::addLog);
        mapManager.initMap();

        route = RouteData.getRoute();
        mapManager.drawRoute(route);

        // Ensure center and zoom are set after layout
        mapView.post(() -> {
            mapManager.setZoom(17.0);
            if (!route.isEmpty()) {
                mapManager.updateCarPosition(route.get(0).lat, route.get(0).lon, 0);
            } else {
                mapView.getController().setCenter(new org.osmdroid.util.GeoPoint(49.435, 11.094));
            }
            addLog("Map view layout completed");
        });
    }

    private void initSimulation() {
        route = RouteData.getRoute();
        simulationEngine = new SimulationEngine(route);
        simulationEngine.setListener(this);
    }

    private void setupListeners() {
        // BLE Connect
        btnConnect.setOnClickListener(v -> {
            if (checkPermissions()) {
                startBrowseAndShowDialog();
            } else {
                requestPermissions();
            }
        });

        btnDisconnect.setOnClickListener(v -> {
            heartbeatTask.stop();
            bleManager.disconnect();
            addLog("Disconnected");
        });

        // Simulation controls
        btnStart.setOnClickListener(v -> {
            if (simulationEngine.isPaused()) {
                simulationEngine.resume();
                addLog("Simulation resumed");
            } else if (!simulationEngine.isRunning()) {
                simulationEngine.start();
                addLog("Simulation started");
            }
            updateControlButtons();
        });

        btnPause.setOnClickListener(v -> {
            if (simulationEngine.isRunning() && !simulationEngine.isPaused()) {
                simulationEngine.pause();
                addLog("Simulation paused");
            }
            updateControlButtons();
        });

        btnReset.setOnClickListener(v -> {
            simulationEngine.reset();
            // Reset map to start position, orientation and zoom
            mapManager.resetOrientation();
            mapManager.setZoom(17.0);
            if (!route.isEmpty()) {
                RouteWaypoint start = route.get(0);
                mapManager.updateCarPosition(start.lat, start.lon, 0);
            }
            // Reset nav panel
            navDirectionText.setText("Ready to Start");
            navDistanceText.setText("-- m");
            navTotalDistText.setText("Total: -- km");
            addLog("Simulation reset");
            updateControlButtons();
        });

        // Speed is auto-managed; hide SeekBar controls
        speedSeekBar.setVisibility(View.GONE);
        speedLabel.setVisibility(View.GONE);

        // Speed multiplier controls (1x ~ 10x, integer steps)
        btnSpeedDown.setOnClickListener(v -> {
            double mult = simulationEngine.getSpeedMultiplier() - 1.0;
            simulationEngine.setSpeedMultiplier(mult);
            updateSpeedMultiplierLabel();
        });

        btnSpeedUp.setOnClickListener(v -> {
            double mult = simulationEngine.getSpeedMultiplier() + 1.0;
            simulationEngine.setSpeedMultiplier(mult);
            updateSpeedMultiplierLabel();
        });
    }

    private void updateSpeedMultiplierLabel() {
        double mult = simulationEngine.getSpeedMultiplier();
        if (mult == (int) mult) {
            navSpeedMultiplier.setText(String.format(Locale.US, "%dx", (int) mult));
        } else {
            navSpeedMultiplier.setText(String.format(Locale.US, "%.1fx", mult));
        }
    }

    private void updateControlButtons() {
        boolean running = simulationEngine.isRunning();
        boolean paused = simulationEngine.isPaused();

        btnStart.setEnabled(!running || paused);
        btnPause.setEnabled(running && !paused);
        btnReset.setEnabled(true);

        if (paused) {
            btnStart.setText("Resume");
        } else {
            btnStart.setText("Start");
        }
    }

    // ---- SimulationEngine.SimulationListener ----

    @Override
    public void onPositionUpdate(double lat, double lon, double bearing, double speed,
                                 double distToTurn, double totalRemaining,
                                 Direction direction, String label) {
        // Update map
        mapManager.updateCarPosition(lat, lon, bearing);

        // Update navigation panel
        navDirectionText.setText(direction.getEnglishText());
        navDistanceText.setText(HysProtocol.formatDistanceEN(distToTurn));
        navTotalDistText.setText("Total: " + HysProtocol.formatDistanceEN(totalRemaining));
        updateNavArrow(direction);

        // Build and send HYS protocol packet
        String packet = HysProtocol.buildNaviInfo(direction, distToTurn, totalRemaining, speed);

        if (bleManager.isConnected()) {
            bleManager.sendDataToCharacteristic(packet);
        }

        addLog("NAVI> " + packet);
    }

    @Override
    public void onArrived(double lat, double lon) {
        mapManager.updateCarPosition(lat, lon, 0);
        navDirectionText.setText("Arrived!");
        navDistanceText.setText("Nürnberg Hbf");
        navTotalDistText.setText("Total: 0 m");
        addLog("=== ARRIVED at Nürnberg Hbf ===");
        updateControlButtons();
    }

    private void updateNavArrow(Direction direction) {
        int drawableId;
        switch (direction) {
            case LEFT:
                drawableId = R.drawable.ic_arrow_left;
                break;
            case RIGHT:
                drawableId = R.drawable.ic_arrow_right;
                break;
            case LEFT_FWD:
                drawableId = R.drawable.ic_arrow_left_fwd;
                break;
            case RIGHT_FWD:
                drawableId = R.drawable.ic_arrow_right_fwd;
                break;
            default:
                drawableId = R.drawable.ic_arrow_straight;
                break;
        }
        navArrow.setImageResource(drawableId);
    }

    // ---- BLE Browse & Connect ----

    private boolean isLocationEnabled() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        return lm != null && (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
    }

    @SuppressLint("MissingPermission")
    private void startBrowseAndShowDialog() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && !isLocationEnabled()) {
            new AlertDialog.Builder(this)
                    .setTitle("Location Required")
                    .setMessage("BLE scanning requires Location Services to be enabled on this Android version.")
                    .setPositiveButton("Open Settings", (d, w) ->
                            startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        browseDisplayList.clear();
        browseDeviceList.clear();

        browseAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, browseDisplayList);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Scanning BLE devices...");
        builder.setAdapter(browseAdapter, (dialog, which) -> {
            bleManager.stopBrowseScan();
            BluetoothDevice selectedDevice = browseDeviceList.get(which);
            addLog("Selected: " + selectedDevice.getAddress());
            bleManager.connectToDevice(selectedDevice);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            bleManager.stopBrowseScan();
            addLog("Browse scan cancelled");
        });
        builder.setOnCancelListener(dialog -> {
            bleManager.stopBrowseScan();
        });

        browseDialog = builder.create();
        browseDialog.show();

        bleManager.startBrowseScan();
        addLog("Browse scanning...");
    }

    // ---- BLE Callbacks ----

    @SuppressLint("MissingPermission")
    @Override
    public void onDeviceFound(BluetoothDevice device, int rssi) {
        runOnUiThread(() -> {
            String address = device.getAddress();
            String name = device.getName();
            if (name == null || name.isEmpty()) name = "Unknown";
            String displayText = name + "\n" + address + " (" + rssi + " dBm)";

            int existingIndex = -1;
            for (int i = 0; i < browseDeviceList.size(); i++) {
                if (browseDeviceList.get(i).getAddress().equals(address)) {
                    existingIndex = i;
                    break;
                }
            }

            if (existingIndex >= 0) {
                browseDisplayList.set(existingIndex, displayText);
                browseDeviceList.set(existingIndex, device);
            } else {
                browseDisplayList.add(displayText);
                browseDeviceList.add(device);
            }

            scheduleBrowseListUpdate();
        });
    }

    private void scheduleBrowseListUpdate() {
        if (browseUpdatePending) return;
        browseUpdatePending = true;
        new Handler(getMainLooper()).postDelayed(() -> {
            browseUpdatePending = false;
            if (browseAdapter != null) {
                browseAdapter.notifyDataSetChanged();
            }
        }, BROWSE_UPDATE_INTERVAL_MS);
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onDeviceConnected(BluetoothDevice device) {
        runOnUiThread(() -> {
            String name = device.getName() != null ? device.getName() : device.getAddress();
            bleStatusIcon.setImageResource(R.drawable.ic_ble_connected);
            bleStatusText.setText("BLE: " + name);
            btnConnect.setVisibility(View.GONE);
            btnDisconnect.setVisibility(View.VISIBLE);
            addLog("Connected: " + name);

            // Start heartbeat
            heartbeatTask.start();
            addLog("Heartbeat started (500ms)");
        });
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onDeviceDisconnected(BluetoothDevice device) {
        runOnUiThread(() -> {
            bleStatusIcon.setImageResource(R.drawable.ic_ble_disconnected);
            bleStatusText.setText("BLE: Disconnected");
            btnConnect.setVisibility(View.VISIBLE);
            btnDisconnect.setVisibility(View.GONE);
            heartbeatTask.stop();
            addLog("Disconnected");
        });
    }

    @Override
    public void onServicesDiscovered(List<BluetoothGattService> services) {
        addLog("Services discovered: " + services.size());
    }

    @Override
    public void onCharacteristicChanged(BluetoothGattCharacteristic characteristic) {
        String value = new String(characteristic.getValue());
        addLog("RX: " + value);
    }

    // ---- Permissions ----

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, PERMISSION_REQUEST_CODE);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                addLog("Permissions granted");
            } else {
                addLog("Permissions denied");
                Toast.makeText(this, "BLE permissions required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ---- Logging ----

    private void addLog(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        runOnUiThread(() -> {
            logTextView.append("[" + timestamp + "] " + message + "\n");
            logScrollView.post(() -> logScrollView.fullScroll(ScrollView.FOCUS_DOWN));
            Log.d(TAG, message);
        });
    }

    // ---- Lifecycle ----

    @Override
    protected void onResume() {
        super.onResume();
        MapView mapView = findViewById(R.id.mapView);
        if (mapView != null) mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        MapView mapView = findViewById(R.id.mapView);
        if (mapView != null) mapView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (simulationEngine != null) simulationEngine.reset();
        if (heartbeatTask != null) heartbeatTask.stop();
        if (bleManager != null) bleManager.disconnect();
    }
}
