package com.example.ew2026demo.simulation;

import android.os.Handler;
import android.os.Looper;

import com.example.ew2026demo.protocol.Direction;
import com.example.ew2026demo.util.GeoUtil;

import java.util.List;
import java.util.Random;

/**
 * Simulation engine that moves a virtual car along the route.
 * Tick interval: 200ms (5 fps).
 * Speed fluctuates between 30-70 km/h automatically:
 *   - Near turns (< 200m): 30-45 km/h
 *   - Straight segments: 50-70 km/h
 */
public class SimulationEngine {
    private static final int TICK_INTERVAL_MS = 200;
    private static final double TURN_SLOW_DISTANCE = 200.0; // meters

    private final List<RouteWaypoint> route;
    private final double[] segmentDistances;
    private final double totalRouteDistance;
    private final Random random = new Random();

    private Handler handler;
    private Runnable tickRunnable;
    private SimulationListener listener;

    // State
    private boolean isRunning = false;
    private boolean isPaused = false;
    private boolean isLooping = true; // Default to true for easy Demo
    private double currentSpeed = 50.0;   // actual fluctuating speed
    private double targetSpeed = 50.0;    // speed we're smoothing towards
    private double speedMultiplier = 2.0; // simulation speed multiplier (1x ~ 10x)
    private int currentSegment = 0;
    private double progressInSegment = 0;
    private double totalTraveled = 0;
    private int tickCount = 0;

    public SimulationEngine(List<RouteWaypoint> route) {
        this.route = route;
        this.handler = new Handler(Looper.getMainLooper());

        segmentDistances = new double[route.size() - 1];
        double total = 0;
        for (int i = 0; i < route.size() - 1; i++) {
            segmentDistances[i] = GeoUtil.distance(
                    route.get(i).lat, route.get(i).lon,
                    route.get(i + 1).lat, route.get(i + 1).lon);
            total += segmentDistances[i];
        }
        totalRouteDistance = total;
    }

    public void setListener(SimulationListener listener) {
        this.listener = listener;
    }

    public void setLooping(boolean looping) {
        this.isLooping = looping;
    }

    public boolean isLooping() {
        return isLooping;
    }

    /** No-op kept for SeekBar compatibility; speed is now auto-managed. */
    public void setSpeed(double kmh) {
        // ignored — speed is auto-fluctuating
    }

    public double getSpeed() {
        return currentSpeed;
    }

    public void setSpeedMultiplier(double multiplier) {
        this.speedMultiplier = Math.max(1.0, Math.min(10.0, multiplier));
    }

    public double getSpeedMultiplier() {
        return speedMultiplier;
    }

    public void start() {
        if (isRunning && !isPaused) return;

        isRunning = true;
        isPaused = false;

        tickRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRunning && !isPaused) {
                    tick();
                    handler.postDelayed(this, TICK_INTERVAL_MS);
                }
            }
        };
        handler.post(tickRunnable);
    }

    public void pause() {
        isPaused = true;
    }

    public void resume() {
        if (isRunning && isPaused) {
            isPaused = false;
            handler.post(tickRunnable);
        }
    }

    public void reset() {
        isRunning = false;
        isPaused = false;
        if (tickRunnable != null) {
            handler.removeCallbacks(tickRunnable);
        }
        currentSegment = 0;
        progressInSegment = 0;
        totalTraveled = 0;
        tickCount = 0;
        currentSpeed = 50.0;
        targetSpeed = 50.0;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public boolean isPaused() {
        return isPaused;
    }

    public double getTotalRouteDistance() {
        return totalRouteDistance;
    }

    private void tick() {
        if (currentSegment >= route.size() - 1) {
            if (isLooping) {
                // Loop: restart from the beginning
                currentSegment = 0;
                progressInSegment = 0;
                totalTraveled = 0;
            } else {
                isRunning = false;
                handler.removeCallbacks(tickRunnable);
                if (listener != null) {
                    RouteWaypoint last = route.get(route.size() - 1);
                    listener.onArrived(last.lat, last.lon);
                }
                return;
            }
        }

        tickCount++;

        // --- Compute distance to next turn (needed for speed logic) ---
        double distToTurn = computeDistToNextTurn();

        // --- Auto speed fluctuation ---
        updateSpeed(distToTurn);

        // --- Advance position ---
        double metersPerTick = (currentSpeed * 1000.0 / 3600.0) * (TICK_INTERVAL_MS / 1000.0) * speedMultiplier;
        double remaining = metersPerTick;

        while (remaining > 0 && currentSegment < route.size() - 1) {
            double segLen = segmentDistances[currentSegment];
            double leftInSegment = segLen - progressInSegment;

            if (remaining >= leftInSegment) {
                remaining -= leftInSegment;
                totalTraveled += leftInSegment;
                currentSegment++;
                progressInSegment = 0;
            } else {
                progressInSegment += remaining;
                totalTraveled += remaining;
                remaining = 0;
            }
        }

        if (currentSegment >= route.size() - 1) {
            if (isLooping) {
                // Handle end-of-route exactly on this tick if looping
                currentSegment = 0;
                progressInSegment = 0;
                totalTraveled = 0;
            } else {
                isRunning = false;
                handler.removeCallbacks(tickRunnable);
                if (listener != null) {
                    RouteWaypoint last = route.get(route.size() - 1);
                    listener.onArrived(last.lat, last.lon);
                }
                return;
            }
        }

        // --- Interpolate position using Catmull-Rom spline for smooth curves ---
        double segLen = segmentDistances[currentSegment];
        double fraction = (segLen > 0) ? progressInSegment / segLen : 0;

        // Get 4 control points: P0, P1 (current), P2 (next), P3
        int idx0 = Math.max(0, currentSegment - 1);
        int idx1 = currentSegment;
        int idx2 = currentSegment + 1;
        int idx3 = Math.min(route.size() - 1, currentSegment + 2);

        RouteWaypoint p0 = route.get(idx0);
        RouteWaypoint p1 = route.get(idx1);
        RouteWaypoint p2 = route.get(idx2);
        RouteWaypoint p3 = route.get(idx3);

        double[] pos = GeoUtil.catmullRom(
                p0.lat, p0.lon, p1.lat, p1.lon,
                p2.lat, p2.lon, p3.lat, p3.lon, fraction);
        double bearing = GeoUtil.catmullRomBearing(
                p0.lat, p0.lon, p1.lat, p1.lon,
                p2.lat, p2.lon, p3.lat, p3.lon, fraction);
        double totalRemaining = totalRouteDistance - totalTraveled;

        // --- Re-compute turn info after position advance ---
        Direction nextDirection = Direction.STRAIGHT;
        String nextLabel = "";
        distToTurn = segLen - progressInSegment;

        for (int i = currentSegment + 1; i < route.size(); i++) {
            if (route.get(i).isTurnPoint()) {
                nextDirection = route.get(i).direction;
                nextLabel = route.get(i).label;
                break;
            }
            if (i < route.size() - 1) {
                distToTurn += segmentDistances[i];
            }
        }

        if (p2.isTurnPoint()) {
            nextDirection = p2.direction;
            nextLabel = p2.label;
            distToTurn = segLen - progressInSegment;
        }

        if (listener != null) {
            listener.onPositionUpdate(
                    pos[0], pos[1],
                    bearing, currentSpeed,
                    distToTurn, totalRemaining,
                    nextDirection, nextLabel);
        }
    }

    /**
     * Compute distance to the next non-STRAIGHT waypoint from current position.
     */
    private double computeDistToNextTurn() {
        if (currentSegment >= route.size() - 1) return Double.MAX_VALUE;

        double dist = segmentDistances[currentSegment] - progressInSegment;
        for (int i = currentSegment + 1; i < route.size(); i++) {
            if (route.get(i).isTurnPoint()) return dist;
            if (i < route.size() - 1) dist += segmentDistances[i];
        }
        return dist;
    }

    /**
     * Auto-fluctuate speed:
     *   Near turn (< 200m):  target 30-45 km/h
     *   Straight:            target 50-70 km/h
     * Every ~1s pick a new target; smoothly interpolate each tick.
     */
    private void updateSpeed(double distToTurn) {
        // Pick new target every ~1 second (5 ticks)
        if (tickCount % 5 == 0) {
            if (distToTurn < TURN_SLOW_DISTANCE) {
                // Near a turn: slow range
                targetSpeed = 30 + random.nextInt(16); // 30-45
            } else {
                // Straight road: fast range
                targetSpeed = 50 + random.nextInt(21); // 50-70
            }
        }

        // Smooth towards target (ease 20% per tick)
        currentSpeed += (targetSpeed - currentSpeed) * 0.2;

        // Clamp
        currentSpeed = Math.max(25, Math.min(75, currentSpeed));
    }

    public interface SimulationListener {
        void onPositionUpdate(double lat, double lon, double bearing, double speed,
                              double distToTurn, double totalRemaining,
                              Direction direction, String label);
        void onArrived(double lat, double lon);
    }
}
