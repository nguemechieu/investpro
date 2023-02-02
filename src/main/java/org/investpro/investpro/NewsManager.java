package org.investpro.investpro;

import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Objects;

import static java.lang.System.out;

public class NewsManager {

    private static final String url = "https://nfs.faireconomy.media/ff_calendar_thisweek.json?version=1bed8a31256f1525dbb0b6daf6898823";
    static ArrayList<News> news;
    static News news1 = new News(null, null, null, "", "", "");
    public NewsManager() {


    }

    public static ArrayList<org.investpro.investpro.News> getNewsList() {
        return load();

    }

    private static ArrayList<News> load() {
        news = new ArrayList<>();//
        JSONArray jsonArray = Objects.requireNonNull(makeRequest(url));
        int length = jsonArray.length();

        for (int i = 0; i < length; i++) {
            JSONObject json = jsonArray.getJSONObject(i);
            String title = "hello";
            if (json.has("title")) {
                title = json.getString("title");
                news1.setTitle(title);
            }
            String country=null;
            if (json.has("country")) {
                country = json.getString("country");
                news1.setCountry(country);
            }

            String impact=null;
            if (json.has("impact")) {
                impact = json.getString("impact");
                news1.setImpact(impact);
            }
            String date=null;
            if (json.has("date")) {
                date = json.getString("date");
                news1.setDate(date);
            }
            String forecast= "";
            if (json.has("forecast")) {
                forecast = json.getString("forecast");
                news1.setForecast(forecast);
            }
            String previous = "01";
            if (json.has("previous")) {
                previous = json.getString("previous");
                news1.setPrevious(previous);
            }
            news.add(i, new News(title, country, impact, date, forecast, previous));

            assert date != null;

            out.println("New" + news1);


        }
        return news;
    }


    //makeRequest return JSONObject
    private static @Nullable JSONArray makeRequest(String url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setUseCaches(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            // connection.setRequestProperty("Authorization", "Bearer " + getToken());
            connection.connect();
            int responseCode = connection.getResponseCode();
            out.printf("Response Code: %d%n", responseCode);
            InputStream in = connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader((in)));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = reader.readLine())!= null) {
                response.append(inputLine);
                response.append("\r\n");
            }
            reader.close();
            return new JSONArray(response.toString());



        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


}
