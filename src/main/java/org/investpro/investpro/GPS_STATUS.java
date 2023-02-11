package org.investpro.investpro;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * --------------------------------------------This the GPS status ---------------------------------------------------
 **/
public enum GPS_STATUS {
    ON, OFF,
    CONNECTED,
    IN_PROGRESS, STOPPED,
    NOT_AVAILABLE,
    DISCONNECTED,
    // SERVER_ERROR_516, GPS_RESPONSE_FAILED, GPS_RESPONSE_OK, IN_PROGRESS, GPS_ENABLED, GPS_NOT_AVAILABLE, GPS_IS_NOT_RUNNING, GPS_IS_RUNNING, GPS_AVAILABLE;


    String, GPS_ENABLED, NETWORK_ERROR, READY, PAUSED, LOCATION_FOUND, LOCATION_NOT_FOUND, UNDEFINED, NOT_READY;


    private static int checker;
    private static int statusCode;

    public static GPS_STATUS NETWORK_RESPONSE(int statusCode) {
        GPS_STATUS.statusCode = statusCode;
        return switch (statusCode) {
            case 0 -> ON;
            case 1 -> OFF;
            case 2 -> CONNECTED;
            case 3 -> IN_PROGRESS;
            case 4 -> STOPPED;
            case 5 -> NOT_AVAILABLE;
            case 6 -> DISCONNECTED;
            default -> null;
        };
    }


    public static int getChecker() {
        return checker;
    }

    public void setChecker(int checker) {
        GPS_STATUS.checker = checker;
    }

    @NotNull
    @Contract(pure = true)
    public java.lang.String toString() {
        return switch (this) {
            case ON -> "ON";
            case CONNECTED -> "CONNECTED";
            case IN_PROGRESS -> "IN PROGRESS";
            case STOPPED -> "STOPPED";
            case NOT_AVAILABLE -> "NOT AVAILABLE";
            case DISCONNECTED -> "DISCONNECTED";
            case NETWORK_ERROR -> "NETWORK ERROR";
            case OFF -> "OFF";
            default -> "UNKNOWN";
        };
    }

    public int compareTo(int statusCode) {
        return switch (this) {
            case ON -> 1;
            case OFF -> -1;
            case CONNECTED -> 2;
            case IN_PROGRESS -> 3;
            case STOPPED -> 4;
            case NOT_AVAILABLE -> 5;
            case DISCONNECTED -> 6;
            default -> statusCode;
        };
    }
}
