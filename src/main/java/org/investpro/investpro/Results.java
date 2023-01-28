package org.investpro.investpro;


import org.json.JSONObject;

abstract class address_components {
    String long_name;
    String short_name;
    String[] types;

    public String getLong_name() {
        return long_name;
    }

    public void setLong_name(String long_name) {
        this.long_name = long_name;
    }

    public String getShort_name() {
        return short_name;
    }

    public void setShort_name(String short_name) {
        this.short_name = short_name;
    }

    public String[] getTypes() {
        return types;
    }

    public void setTypes(String[] types) {
        this.types = types;
    }
}

class plus_code {
    private static final String compound_code = "";
    private static final String global_code = "";


}

public class Results {

    public String global_code;
    public String address;
    public String locality;
    public double lat;
    public double lng;
    public String plus_name;
    address_components address_component1;
    String formatted_address;
    String plus_code;
    String location;
    String place_id;
    String location_type;
    Viewport viewport;
    private String toString;
    private double speed;
    private Object result;
    private String[] types;
    private String short_name;
    private String compound_code;
    private String long_name;

    public Results(String lat, String lon, String formatted_address, double speed, String toString) {
        this.result = lat;
        this.short_name = lat;
        this.types = new String[]{lat, lon};
        this.formatted_address = formatted_address;
        this.speed = speed;
        this.toString = toString;
    }

    public Results(String toString, JSONObject jsonResponse) {
        this.toString = toString;
        // this.global_code = jsonResponse.getString("global_code");
        // this.location = jsonResponse.getString("location");
        //   this.result = jsonResponse.getString("result");
        // this.locality = jsonResponse.getString("locality");
        this.address = jsonResponse.getString("formatted_address");
        this.long_name = jsonResponse.getString("long_name");
        this.short_name = jsonResponse.getString("short_name");
        this.types = jsonResponse.getString("types").split(",");

        if (jsonResponse.has("compound_code")) {
            this.compound_code = jsonResponse.getString("compound_code");
            this.global_code = jsonResponse.getString("compound_code");
            this.address = jsonResponse.getString("compound_code");

        }
        if (jsonResponse.has("global_code")) {
            this.global_code = jsonResponse.getString("global_code");
            this.address = jsonResponse.getString("global_code");
            this.global_code = jsonResponse.getString("global_code");
        }
        if (jsonResponse.has("location_type")) {
            this.location_type = jsonResponse.getString("location_type");
            this.address = jsonResponse.getString("location_type");
        }


    }

    public Results(String body) {
    }

    public Results(String[] types, String compound_code, address_components address_component1, String formatted_address, plus_code plus_code, String place_id, String location_type, Viewport viewport) {
        this.types = types;
        this.compound_code = compound_code;
        this.address_component1 = address_component1;
        this.formatted_address = formatted_address;
        this.plus_code = plus_code.toString();
        this.place_id = place_id;
        this.location_type = location_type;
        this.viewport = viewport;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public String[] getTypes() {
        return types;
    }

    public void setTypes(String[] types) {
        this.types = types;
    }

    public String getShort_name() {
        return short_name;
    }

    public void setShort_name(String short_name) {
        this.short_name = short_name;
    }

    public String getCompound_code() {
        return compound_code;
    }

    public void setCompound_code(String compound_code) {
        this.compound_code = compound_code;
    }

    public String getGlobal_code() {
        return global_code;
    }

    public void setGlobal_code(String global_code) {
        this.global_code = global_code;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getLocality() {
        return locality;
    }

    public void setLocality(String locality) {
        this.locality = locality;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLng() {
        return lng;
    }

    public void setLng(double lng) {
        this.lng = lng;
    }

    public String getPlus_name() {
        return plus_name;
    }

    public void setPlus_name(String plus_name) {
        this.plus_name = plus_name;
    }

    public address_components getAddress_component1() {
        return address_component1;
    }

    public void setAddress_component1(address_components address_component1) {
        this.address_component1 = address_component1;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    @Override
    public String toString() {
        return "Results{" +
                "global_code='" + global_code + '\'' +
                ", address='" + address + '\'' +
                ", locality='" + locality + '\'' +
                ", lat=" + lat +
                ", lng=" + lng +
                ", plus_name='" + plus_name + '\'' +
                ", address_component1=" + address_component1 +
                ", formatted_address='" + formatted_address + '\'' +
                ", plus_code='" + plus_code + '\'' +
                ", location='" + location + '\'' +
                ", place_id='" + place_id + '\'' +
                ", location_type='" + location_type + '\'' +
                ", viewport=" + viewport +
                '}';
    }

    public String getFormatted_address() {
        return formatted_address;
    }

    public void setFormatted_address(String formatted_address) {
        this.formatted_address = formatted_address;
    }

    public String getPlus_code() {
        return plus_code;
    }

    public void setPlus_code(String plus_code) {
        this.plus_code = plus_code;
    }



    public String getPlace_id() {
        return place_id;
    }

    public void setPlace_id(String place_id) {
        this.place_id = place_id;
    }

    public String getLocation_type() {
        return location_type;
    }

    public void setLocation_type(String location_type) {
        this.location_type = location_type;
    }

    public Viewport getViewport() {
        return viewport;
    }

    public void setViewport(Viewport viewport) {
        this.viewport = viewport;
    }

    public String getLong_name() {
        return long_name;
    }

    public void setLong_name(String long_name) {
        this.long_name = long_name;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public String getToString() {
        return toString;
    }

    public void setToString(String toString) {
        this.toString = toString;
    }
}
