package org.investpro;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

public class NewsDataProvider {

    public NewsDataProvider() {


    }

    @Contract("null -> fail")
    public static Date StringToDate(String str) throws ParseException {//TODO implement date
        if (str == null) throw new IllegalArgumentException("Invalid date string");
        //ZonedDateTime.from(DateTimeFormatter.ofPattern( "yyyy-MM-dd'T'HH:mm:ss").parse(str.substring(0, 19)));
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(str);
    }

    List<News> getNews() throws ParseException, IOException, InterruptedException {

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();


        requestBuilder.uri(URI.create("https://nfs.faireconomy.media/ff_calendar_thisweek.json?version=1bed8a31256f1525dbb0b6daf6898823"));


        HttpResponse<String> response = HttpClient.newHttpClient().send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());


        ArrayList<News> news = new ArrayList<>();//
        JSONArray jsonArray = new JSONArray(response.body());
        News news1;
        int length = jsonArray.length();
        for (int i = 0; i < length; i++) {
            JSONObject json = jsonArray.getJSONObject(i);
            String title = null;
            news1 = new News(null, null, null, null, "0", "");
            if (json.has("title")) {
                title = json.getString("title");
                news1.setTitle(title);
            }
            String country = null;
            if (json.has("country")) {
                country = json.getString("country");
                news1.setCountry(country);
            }

            String impact = "";
            if (json.has("impact")) {
                impact = json.getString("impact");
                news1.setImpact(impact);
            }
            String date = "";
            if (json.has("date")) {
                date = json.getString("date");

                news1.setOffset(date.codePointCount(16, 19));
                news1.setDate(StringToDate(date));
            }
            String forecast = "";
            if (json.has("forecast")) {
                forecast = json.getString("forecast");
                news1.setForecast(forecast);
            }
            String previous = "01";
            if (json.has("previous")) {
                previous = json.getString("previous");
                news1.setPrevious(previous);
                news.add(i, new News(title, country, impact, StringToDate(date), forecast, previous));
            } else {
                news.add(i, new News(title, country, impact, StringToDate(date), forecast, previous));
            }


        }
        return news;
    }

}