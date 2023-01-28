package org.investpro.investpro;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Date;

import static java.lang.StrictMath.pow;
import static java.lang.StrictMath.sqrt;
public
class Geocode {
    String address;

    private String url;
    private String city;
    private String zip;
    private String country;
    private double longitude = 0;
    private double latitude = 0;
    private double altitude;
    private String heading;
    private String satellites;
    private String hdop;
    private String streetNumber;
    private String administrativeAreaLevel2;
    private String state;
    private String northeast;
    private String southwest;
    private String street_name;
    private String phone;
    private String fax;
    private String name;
    private String email;
    private String website;
    private String timezone;
    private String timeStamp;
    private String localization;
    private String currency;
    private GPS_STATUS status;
    private String locationType;
    private String lat;
    private String viewport;
    private String formatted_address;
    private String lng;
    private String global_code;
    private String compound_code;


    private String location;
    private String street_number;
    private String street;
    private int gpsBearing;
    private boolean bearing;
    private String short_name;
    private String locality;
    private String region;
    private String countryCode;
    private String types;



    public String getHeading() {
        return heading;
    }

    public void setHeading(String heading) {
        this.heading = heading;
    }

    public String getSatellites() {
        return satellites;
    }

    public void setSatellites(String satellites) {
        this.satellites = satellites;
    }

    public String getStreetNumber() {
        return streetNumber;
    }

    public void setStreetNumber(String streetNumber) {
        this.streetNumber = streetNumber;
    }

    @Override
    public String toString() {
        return
                "status: " + getStatus() + " "+
                getFormatted_address()+
                ", lat='" + lat + '\'' +
                ", lng='" + lng + '\'' +
                        ", Speed= ''"+ getSpeed();
    }

    public String getAdministrativeAreaLevel2() {
        return administrativeAreaLevel2;
    }

    public void setAdministrativeAreaLevel2(String administrativeAreaLevel2) {
        this.administrativeAreaLevel2 = administrativeAreaLevel2;
    }

    public String getNortheast() {
        return northeast;
    }

    public void setNortheast(String northeast) {
        this.northeast = northeast;
    }

    public String getSouthwest() {
        return southwest;
    }

    public void setSouthwest(String southwest) {
        this.southwest = southwest;
    }

    public String getStreet_name() {
        return street_name;
    }

    public void setStreet_name(String street_name) {
        this.street_name = street_name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getFax() {
        return fax;
    }

    public void setFax(String fax) {
        this.fax = fax;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(String timeStamp) {
        this.timeStamp = timeStamp;
    }

    public String getLocalization() {
        return localization;
    }

    public void setLocalization(String localization) {
        this.localization = localization;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getLocationType() {
        return locationType;
    }

    public void setLocationType(String locationType) {
        this.locationType = locationType;
    }

    public void setViewport(String viewport) {
        this.viewport = viewport;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public boolean isBearing() {
        return bearing;
    }

    public void setBearing(boolean bearing) {
        this.bearing = bearing;
    }

    public String getLocality() {
        return locality;
    }

    public void setLocality(String locality) {
        this.locality = locality;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getPlace_id() {
        return place_id;
    }

    private String place_id;

    public String getLong_name() {
        return long_name;
    }

    private String long_name;

    public Geocode(String url) {
        this.url = url;
        runGps();
    }


    public void setTypes(String types) {
        this.types = types;
    }


    //

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public void runGps() {
        status = GPS_STATUS.ON;
//Get GPS Connection
        try {
            NETWORK_RESPONSE network_response = NETWORK_RESPONSE.NO_CONTENT;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://google-maps-geocoding.p.rapidapi.com/geocode/json?latlng=40.714224%2C-73.96145&language=en"))
                    .header("X-RapidAPI-Key", "9f4bfd488fmsha9d8ea8e37dc27dp161a32jsn14dbebc7b731")
                    .header("X-RapidAPI-Host", "google-maps-geocoding.p.rapidapi.com")
                    .method("GET", HttpRequest.BodyPublishers.noBody())
                    .build();
            status = GPS_STATUS.DISCONNECTED;
            //String url = "https://google-maps-geocoding.p.rapidapi.com/geocode/json?latlng=40.714224%2C-73.96145&language=en";

            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

status=GPS_STATUS.DISCONNECTED;
            if (response.statusCode() == 200) {
                status = GPS_STATUS.CONNECTED;
                network_response = NETWORK_RESPONSE.SERVER_OK;
                System.out.println(network_response);

                JSONObject json = new JSONObject(response);
                try {status=GPS_STATUS.IN_PROGRESS;
                    json = new JSONObject(response.body());
                    if (json.has("place_id")) {setPlace_id(json.getString("place_id"));
                    }
                    if (json.has("long_name")) {
                        setLong_name(json.getString("long_name"));
                    }
                    if (json.has("types")) {setTypes(json.getString("types"));}
                    if (json.has("location")) {
                        setLocation(json.getString("location"));
                    }
                    if (json.has("results")) {
                        JSONArray results = json.getJSONArray("results");
                        for (int i = 0; i < results.length(); i++) {
                            JSONObject result = results.getJSONObject(i);

                            if (result.has("location")) {
                                setLocation( result.getString("location"));
                            }
                            if (result.has("geometry")) {
                                JSONObject geometry = result.getJSONObject("geometry");
                                if (geometry.has("location")) {
                                    JSONObject location = geometry.getJSONObject("location");
                                    if (location.has("lat")) {
                                        latitude= location.getDouble("lat");
                                        setLat(String.valueOf(latitude));
                                    }
                                    if (location.has("lng")) {
                                        longitude= location.getDouble("lng");
                                        setLng(String.valueOf(longitude));
                                    }
                                }
status= GPS_STATUS.READY;
                            }
                            if (result.has("formatted_address")) {
                                setFormattedAddress( result.getString("formatted_address"));

                                System.out.println(formatted_address);
                                break;
                            }
                            status = GPS_STATUS.READY;
                        }

                    }
                }
                catch (JSONException e) {
                    status = GPS_STATUS.NETWORK_ERROR;
                    e.printStackTrace();
                }





            }else {

                status = GPS_STATUS.DISCONNECTED;
                System.out.println(network_response);
            }













        } catch (Exception e) {
            e.printStackTrace();
            status = GPS_STATUS.NETWORK_ERROR;
            System.out.println(status);
        }


        status = GPS_STATUS.READY;
    }

    private void setPlace_id(String place_id) {
        this.place_id = place_id;
    }

    private void setShort_name(String short_name) {
        this.short_name = short_name;
    }

    private void setLong_name(String long_name) {
        this.long_name = long_name;
    }

    private void setBounds(double southwest, double northeast, double southeast, double northeast1) {
        this.southwest = String.valueOf(southwest);
        this.northeast = String.valueOf(northeast);
    }

    private void setViewport(JSONObject jsonObject) {
        if (jsonObject.has("viewport")) {
            JSONObject viewport = jsonObject.getJSONObject("viewport");
            if (viewport.has("lat")) {
                latitude = Double.parseDouble(viewport.get("lat").toString());
            }
            if (viewport.has("lng")) {
                longitude = Double.parseDouble(viewport.get("lng").toString());
                if (viewport.has("zoom")) {
                    String zoom = viewport.get("zoom").toString();
                }
            }
        }
    }

    private void setAdministrativeAreaLevel(JSONObject administrative_area_level_) {
        try {
            JSONObject administrative_area_level = new JSONObject();
            if (administrative_area_level_.has("administrative_area_level")) {
                administrative_area_level = administrative_area_level_.getJSONObject("administrative_area_level");
            }
            if (administrative_area_level.has("level")) {
                String level = administrative_area_level.getString("level");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void setBounds(String bounds) {
        JSONObject json = new JSONObject(bounds);
        System.out.println(json.getString("bounds"));
        System.out.println(json.getJSONObject("bounds"));
        System.out.println(json.getString("bounds"));
    }

    private void setGeometry(Object geometry) {
        if (geometry != null) {
            JSONObject json = new JSONObject(geometry);
            if (json.has("location")) {
                JSONObject location = json.getJSONObject("location");
                if (location.has("lat")) {
                    this.lat = String.valueOf(location.getDouble("lat"));
                }
                if (location.has("lng")) {
                    this.lng = String.valueOf(location.getDouble("lng"));
                }

            }
        }
    }

    private void setFormattedAddress(String formatted_address) {
        this.formatted_address = formatted_address;
    }


    public String getStreet_number() {
        return street_number;
    }

    public void setStreet_number(String street_number) {
        this.street_number = street_number;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getZip() {
        return zip;
    }

    public void setZip(String zip) {
        this.zip = zip;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public int getGpsBearing() {
        return gpsBearing;
    }

    public void setGpsBearing(int gpsBearing) {
        this.gpsBearing = gpsBearing;
    }

    public String getHdop() {
        return hdop;
    }

    public void setHdop(String hdop) {
        this.hdop = hdop;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public GPS_STATUS getStatus() {
        return status;
    }

    public void setStatus(GPS_STATUS status) {
        this.status = status;
    }

    /**
     * -------   GPS_CONSTRUCTOR ----------------------------------------------------------------
     **/


    double getTime() {
        return (new Date()).getTime();
    }

    double getSpeed() {

        double speed = 0;
        String gps = "MakeMoney--GPS";
        double time = getTime();
        speed = sqrt(pow(latitude, 2) + pow(longitude, 2)) / getTime();
        //return String.valueOf(this.getLatitude()) + "," + String.valueOf(this.getLongitude());

        return speed;
    }

    private void setSpeed(String speed) {
        if (speed != null) {
            try {
                JSONObject jsonObj = new JSONObject(speed);
                if (jsonObj.has("speed")) {
                    System.out.println(jsonObj.getString("speed"));
                    setSpeed(jsonObj.getString("speed"));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    double getAltitude() {
        return altitude;

    }

    public void setAltitude(double altitude) {
        this.altitude = altitude;
    }

    String getAccuracy() {
        if (this.getLatitude() < -90 || this.getLatitude() > 90) {
            return "Not available";
        }
        if (this.getLongitude() < -180 || this.getLongitude() > 180) {
            return "Not available";

        }
        if (this.getLatitude() > this.getLongitude()) {
            return "Not available";

        }
        return "Good";
    }


    double getLatitude() {
        return this.latitude;

    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    double getLongitude() {
        return this.longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public boolean init() {
        return true;
    }

    public String getFormatted_address() {
        return formatted_address;
    }

    public void setFormatted_address(String formatted_address) {
        this.formatted_address = formatted_address;
    }

    public String getViewport() {
        return viewport;
    }

    private void setViewport(String s, String string, String viewport) {
        this.viewport = viewport;
    }

    public String getLat() {
        return lat;
    }

    private void setLat(String lat) {
        this.lat = lat;
    }

    public String getLng() {
        return lng;
    }

    private void setLng(String lng) {
        this.lng = lng;
    }

    public String getGlobal_code() {
        return global_code;
    }

    public void setGlobal_code(String global_code) {
        this.global_code = global_code;
    }

    public String getCompound_code() {
        return compound_code;
    }

    public void setCompound_code(String compound_code) {
        this.compound_code = compound_code;
    }

 

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getShort_name() {
        return short_name;
    }

    public String getTypes() {
        return types;
    }
}
