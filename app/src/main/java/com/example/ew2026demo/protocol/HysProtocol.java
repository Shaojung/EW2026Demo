package com.example.ew2026demo.protocol;

import java.util.Calendar;
import java.util.Locale;

public class HysProtocol {

    public static String buildHeartbeat() {
        return "0";
    }

    /**
     * Build NAVI_INFO packet.
     * Format: "1|dirCode|dirText|distText|totalDistText|ETA HH:MM"
     *
     * @param direction       turn direction
     * @param distToTurnM     distance to next turn in meters
     * @param totalRemainingM total remaining distance in meters
     * @param speedKmh        current speed in km/h
     * @return formatted packet string
     */
    public static String buildNaviInfo(Direction direction, double distToTurnM,
                                       double totalRemainingM, double speedKmh) {
        String dirText = direction.getEnglishText();
        String distText = formatDistance(distToTurnM);

        // Append "ahead" for turns
        if (direction != Direction.STRAIGHT) {
            distText = "In " + distText;
        }

        String totalDistText = formatDistance(totalRemainingM);

        // Calculate ETA
        int etaSeconds = (speedKmh > 0) ? (int) (totalRemainingM / (speedKmh * 1000.0 / 3600.0)) : 0;
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.SECOND, etaSeconds);
        String etaTime = String.format(Locale.US, "%02d:%02d",
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));

        return String.format(Locale.US, "%d|%d|%s|%s|%s|ETA %s",
                Protocol.NAVI_INFO.ordinal(),
                direction.getCode(),
                dirText,
                distText,
                totalDistText,
                etaTime);
    }

    /**
     * Format distance for BLE packet: >= 1km -> "X.Xkm", < 1km -> "Xm"
     */
    public static String formatDistance(double meters) {
        double km = meters / 1000.0;
        if (km >= 1.0) {
            return String.format(Locale.US, "%.1fkm", km);
        } else {
            return String.format(Locale.US, "%dm", (int) meters);
        }
    }

    /**
     * Format distance for UI display: >= 1km -> "X.X km", < 1km -> "X m"
     */
    public static String formatDistanceEN(double meters) {
        double km = meters / 1000.0;
        if (km >= 1.0) {
            return String.format(Locale.US, "%.1f km", km);
        } else {
            return String.format(Locale.US, "%d m", (int) meters);
        }
    }
}
