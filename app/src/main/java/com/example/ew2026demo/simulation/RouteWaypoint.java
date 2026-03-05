package com.example.ew2026demo.simulation;

import com.example.ew2026demo.protocol.Direction;

public class RouteWaypoint {
    public final double lat;
    public final double lon;
    public final Direction direction;
    public final String label;

    public RouteWaypoint(double lat, double lon, Direction direction, String label) {
        this.lat = lat;
        this.lon = lon;
        this.direction = direction;
        this.label = label;
    }

    /**
     * Shape point (STRAIGHT) — no turn instruction, just defines route geometry.
     */
    public static RouteWaypoint shape(double lat, double lon) {
        return new RouteWaypoint(lat, lon, Direction.STRAIGHT, "");
    }

    /**
     * Instruction point — triggers turn notification.
     */
    public static RouteWaypoint turn(double lat, double lon, Direction direction, String label) {
        return new RouteWaypoint(lat, lon, direction, label);
    }

    public boolean isTurnPoint() {
        return direction != Direction.STRAIGHT;
    }
}
