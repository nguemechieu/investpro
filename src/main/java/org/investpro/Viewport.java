package org.investpro;


public class Viewport {
    Northeast northeast;
    Southwest southwest;

    public Viewport(Northeast northeast, Southwest southwest) {
        this.northeast = northeast;
        System.out.println(northeast);
        this.southwest = southwest;
    }

    public Northeast getNortheast() {
        return northeast;
    }

    public void setNortheast(Northeast northeast) {
        this.northeast = northeast;
    }

    public Southwest getSouthwest() {
        return southwest;
    }

    public void setSouthwest(Southwest southwest) {
        this.southwest = southwest;
    }

}
