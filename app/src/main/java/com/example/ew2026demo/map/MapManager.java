package com.example.ew2026demo.map;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.MapTile;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import com.example.ew2026demo.R;
import com.example.ew2026demo.simulation.RouteWaypoint;

import java.util.ArrayList;
import java.util.List;

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
        // Use a custom tile source whose getTileRelativeFilenameString() returns
        // "{z}/{x}/{y}.png" (no "Mapnik/" prefix) so that osmdroid's built-in
        // MapTileFileArchiveProvider can look up entries inside nuremberg.zip,
        // which stores tiles as {z}/{x}/{y}.png.
        // Online URLs remain unchanged (https://tile.openstreetmap.org/{z}/{x}/{y}.png).
        mapView.setTileSource(new XYTileSource(
                "Mapnik", 0, 19, 256, ".png",
                new String[]{
                        "https://a.tile.openstreetmap.org/",
                        "https://b.tile.openstreetmap.org/",
                        "https://c.tile.openstreetmap.org/"
                }
        ) {
            @Override
            public String getTileRelativeFilenameString(MapTile tile) {
                return tile.getZoomLevel() + "/" + tile.getX() + "/"
                        + tile.getY() + ".png";
            }
        });
        mapView.setMultiTouchControls(true);
        mapView.setBuiltInZoomControls(false);
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

}
