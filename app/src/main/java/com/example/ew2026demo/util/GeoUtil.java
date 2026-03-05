package com.example.ew2026demo.util;

public class GeoUtil {

    private static final double EARTH_RADIUS = 6371000; // meters

    /**
     * Haversine distance between two lat/lon points in meters.
     */
    public static double distance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS * c;
    }

    /**
     * Bearing from point 1 to point 2 in degrees (0=North, 90=East).
     */
    public static double bearing(double lat1, double lon1, double lat2, double lon2) {
        double dLon = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dLon) * Math.cos(Math.toRadians(lat2));
        double x = Math.cos(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2))
                - Math.sin(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(dLon);
        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360) % 360;
    }

    /**
     * Linear interpolation between two points.
     * @param fraction 0.0 = point1, 1.0 = point2
     * @return double[2] = {lat, lon}
     */
    public static double[] interpolate(double lat1, double lon1, double lat2, double lon2, double fraction) {
        double lat = lat1 + (lat2 - lat1) * fraction;
        double lon = lon1 + (lon2 - lon1) * fraction;
        return new double[]{lat, lon};
    }

    /**
     * Catmull-Rom spline interpolation between P1 and P2, using P0 and P3 as control points.
     * Produces a smooth curve that passes through all waypoints.
     *
     * @param t parameter 0.0 = P1, 1.0 = P2
     * @return double[2] = {lat, lon}
     */
    public static double[] catmullRom(
            double lat0, double lon0,
            double lat1, double lon1,
            double lat2, double lon2,
            double lat3, double lon3,
            double t) {
        double t2 = t * t;
        double t3 = t2 * t;

        double lat = 0.5 * ((2 * lat1)
                + (-lat0 + lat2) * t
                + (2 * lat0 - 5 * lat1 + 4 * lat2 - lat3) * t2
                + (-lat0 + 3 * lat1 - 3 * lat2 + lat3) * t3);

        double lon = 0.5 * ((2 * lon1)
                + (-lon0 + lon2) * t
                + (2 * lon0 - 5 * lon1 + 4 * lon2 - lon3) * t2
                + (-lon0 + 3 * lon1 - 3 * lon2 + lon3) * t3);

        return new double[]{lat, lon};
    }

    /**
     * Compute smooth bearing from Catmull-Rom spline tangent direction.
     * Uses a small delta to approximate the tangent at parameter t.
     */
    public static double catmullRomBearing(
            double lat0, double lon0,
            double lat1, double lon1,
            double lat2, double lon2,
            double lat3, double lon3,
            double t) {
        double delta = 0.005;
        double tA = Math.max(0, t - delta);
        double tB = Math.min(1, t + delta);

        double[] posA = catmullRom(lat0, lon0, lat1, lon1, lat2, lon2, lat3, lon3, tA);
        double[] posB = catmullRom(lat0, lon0, lat1, lon1, lat2, lon2, lat3, lon3, tB);

        return bearing(posA[0], posA[1], posB[0], posB[1]);
    }

    /**
     * Shortest angular difference between two bearings in degrees.
     * Result is in [-180, 180].
     */
    public static double bearingDiff(double from, double to) {
        double diff = to - from;
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;
        return diff;
    }

    /**
     * Smoothly interpolate between two bearings.
     */
    public static double lerpBearing(double from, double to, double fraction) {
        double diff = bearingDiff(from, to);
        return (from + diff * fraction + 360) % 360;
    }
}
