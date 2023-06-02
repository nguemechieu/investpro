package org.investpro;


public class CONNECTION_STATUS {
    public static final int CONNECTED = 0;
    public static final int DISCONNECTED = 1;
    public static final int CONNECTING = 2;
    public static final int DISCONNECTING = 3;
    public static final int DISCONNECTED_WITH_ERROR = 4;
    public static final int CONNECTING_WITH_ERROR = 5;
    public static final int IN_PROGRESS = 8;
    public static final int NO_CONNECTION = 9;
    private static final int CONNECTED_WITH_ERROR = 6;
    private static final int DISCONNECTING_WITH_ERROR = 7;

    public CONNECTION_STATUS(int status) {
        switch (status) {
            case CONNECTED:
            case CONNECTED_WITH_ERROR:
            case CONNECTING_WITH_ERROR:
            case CONNECTING:
            case DISCONNECTED_WITH_ERROR:
            case DISCONNECTING_WITH_ERROR:
            case DISCONNECTED:
            case DISCONNECTING:
                break;
        }
    }
}
