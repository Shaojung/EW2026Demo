package com.example.ew2026demo.map;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import com.example.ew2026demo.R;
import com.example.ew2026demo.simulation.RouteWaypoint;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class MapManager {
    private static final String TAG = "MapManager";

    private final Context context;
    private MapView mapView;
    private Marker carMarker;
    private Marker startMarker;
    private Marker endMarker;
    private Polyline routeLine;
    private boolean isFollowing = true;

    public MapManager(Context context, MapView mapView) {
        this.context = context;
        this.mapView = mapView;
    }

    private LogCallback logCallback;

    public interface LogCallback {
        void onLog(String message);
    }

    public void setLogCallback(LogCallback callback) {
        this.logCallback = callback;
    }

    private void addLog(String msg) {
        Log.i(TAG, msg);
        if (logCallback != null) logCallback.onLog(msg);
    }

    public void initMap() {
        // Configuration is now handled in MainActivity before setContentView
        File tileCachePath = Configuration.getInstance().getOsmdroidTileCache();

        // Extract offline tiles into osmdroid cache so they work with default MAPNIK source
        extractOfflineTilesToCache(tileCachePath);

        // Always use MAPNIK — works online (emulator) + offline (cached tiles at expo)
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.setBuiltInZoomControls(false);

        // Removed direct setZoom/setCenter here; handled in MainActivity.post()
    }

    /**
     * Extract tiles from assets/tiles/nuremberg.zip into osmdroid's tile cache.
     * osmdroid MAPNIK cache format: {tileCachePath}/Mapnik/{z}/{x}/{y}.png.tile
     * Our ZIP format:              {z}/{x}/{y}.png
     */
    private void extractOfflineTilesToCache(File tileCachePath) {
        // Marker file to avoid re-extracting every launch
        File marker = new File(tileCachePath, ".nuremberg_extracted");
        if (marker.exists()) {
            addLog("Tiles: Already cached");
            return;
        }

        addLog("Tiles: Copying asset...");
        File zipInCache = copyAssetToCache("tiles/nuremberg.zip");
        if (zipInCache == null || !zipInCache.exists()) {
            addLog("Tiles: Error - ZIP not found");
            return;
        }

        try {
            addLog("Tiles: Extracting...");
            ZipFile zip = new ZipFile(zipInCache);
            Enumeration<? extends ZipEntry> entries = zip.entries();
            int count = 0;

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;

                String name = entry.getName(); // e.g. "15/17390/11189.png"
                if (!name.endsWith(".png")) continue;

                // Convert to osmdroid cache path: Mapnik/{z}/{x}/{y}.png.tile
                String tilePath = "Mapnik/" + name + ".tile";
                File outFile = new File(tileCachePath, tilePath);
                
                if (!outFile.exists()) {
                    outFile.getParentFile().mkdirs();

                    InputStream is = zip.getInputStream(entry);
                    FileOutputStream fos = new FileOutputStream(outFile);
                    byte[] buf = new byte[4096];
                    int len;
                    while ((len = is.read(buf)) != -1) {
                        fos.write(buf, 0, len);
                    }
                    fos.close();
                    is.close();
                }
                count++;
            }
            zip.close();

            // Write marker
            marker.createNewFile();
            addLog("Tiles: Extracted " + count + " files");

        } catch (Exception e) {
            addLog("Tiles: Extract error - " + e.getMessage());
            Log.e(TAG, "Failed to extract offline tiles", e);
        }
    }

    public void drawRoute(List<RouteWaypoint> waypoints) {
        if (waypoints == null || waypoints.size() < 2) return;

        List<GeoPoint> points = new ArrayList<>();
        for (RouteWaypoint wp : waypoints) {
            points.add(new GeoPoint(wp.lat, wp.lon));
        }

        routeLine = new Polyline();
        routeLine.setPoints(points);
        routeLine.getOutlinePaint().setColor(Color.parseColor("#2196F3"));
        routeLine.getOutlinePaint().setStrokeWidth(10f);
        mapView.getOverlayManager().add(routeLine);

        // Start marker (green)
        RouteWaypoint first = waypoints.get(0);
        startMarker = new Marker(mapView);
        startMarker.setPosition(new GeoPoint(first.lat, first.lon));
        startMarker.setTitle("Nürnberg Hbf");
        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        Drawable startIcon = ContextCompat.getDrawable(context, R.drawable.ic_marker_start);
        if (startIcon != null) startMarker.setIcon(startIcon);
        mapView.getOverlayManager().add(startMarker);

        // End marker (red)
        RouteWaypoint last = waypoints.get(waypoints.size() - 1);
        endMarker = new Marker(mapView);
        endMarker.setPosition(new GeoPoint(last.lat, last.lon));
        endMarker.setTitle("Messe Nürnberg");
        endMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        Drawable endIcon = ContextCompat.getDrawable(context, R.drawable.ic_marker_end);
        if (endIcon != null) endMarker.setIcon(endIcon);
        mapView.getOverlayManager().add(endMarker);

        // Car marker
        carMarker = new Marker(mapView);
        carMarker.setPosition(new GeoPoint(first.lat, first.lon));
        carMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        carMarker.setFlat(false);
        Drawable carIcon = ContextCompat.getDrawable(context, R.drawable.ic_car);
        if (carIcon != null) carMarker.setIcon(carIcon);
        mapView.getOverlayManager().add(carMarker);

        mapView.invalidate();
    }

    public void updateCarPosition(double lat, double lon, double bearing) {
        if (carMarker == null) return;

        GeoPoint pos = new GeoPoint(lat, lon);
        carMarker.setPosition(pos);
        carMarker.setRotation(0f);

        // Rotate the entire map so the heading direction is always up
        mapView.setMapOrientation(-(float) bearing);

        if (isFollowing) {
            mapView.getController().animateTo(pos);
        }

        mapView.invalidate();
    }

    public void centerOn(double lat, double lon) {
        mapView.getController().animateTo(new GeoPoint(lat, lon));
    }

    public void setFollowing(boolean following) {
        this.isFollowing = following;
    }

    public void setZoom(double zoom) {
        mapView.getController().setZoom(zoom);
    }

    public void resetOrientation() {
        mapView.setMapOrientation(0f);
        mapView.invalidate();
    }

    private File copyAssetToCache(String assetPath) {
        try {
            File cacheFile = new File(context.getCacheDir(), assetPath);
            if (cacheFile.exists()) return cacheFile;

            cacheFile.getParentFile().mkdirs();
            InputStream is = context.getAssets().open(assetPath);
            FileOutputStream fos = new FileOutputStream(cacheFile);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
            }
            fos.close();
            is.close();
            Log.i(TAG, "Copied asset to cache: " + cacheFile.getAbsolutePath());
            return cacheFile;
        } catch (Exception e) {
            Log.e(TAG, "Failed to copy asset: " + assetPath, e);
            return null;
        }
    }
}
