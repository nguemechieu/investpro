//MakeMoney runs gps
package org.investpro.investpro;

public class Gps {
    Geocode geocode;

    public Gps(String url) {
        geocode = new Geocode(url);
    }

    @Override
    public String toString() {
        return String.valueOf(geocode);
    }


}
/*
 * Licensed to the Apache Software Foundation (ASF) under one
 **/