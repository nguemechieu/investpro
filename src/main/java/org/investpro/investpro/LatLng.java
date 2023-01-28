package org.investpro.investpro;

public class LatLng {
    private final double lat;
    private final double lng;

    public LatLng(double v, double v1) {
        this.lng = v;
        this.lat = v1;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    public char[] getLongitude() {
        return new char[]{(char) (this.lng * 1.1)};
    }

    public char[] getLatitude() {
        return new char[]{(char) (this.lat * 1.1)};
    }
}
