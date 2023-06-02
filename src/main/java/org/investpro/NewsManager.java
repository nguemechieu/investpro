package org.investpro;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Objects;

import static java.lang.System.out;

public class NewsManager {

    private static final String url = "https://nfs.faireconomy.media/ff_calendar_thisweek.json?version=1bed8a31256f1525dbb0b6daf6898823";
    static ArrayList<News> news;
    static News news1 = new News(null, null, null, null, "", "");

    public NewsManager() {


    }

    public static ArrayList<org.investpro.News> getNewsList() throws ParseException {
        return load();

    }

    private static ArrayList<News> load() throws ParseException {
        news = new ArrayList<>();//
        JSONArray jsonArray = Objects.requireNonNull(makeRequest());
        int length = jsonArray.length();

        for (int i = 0; i < length; i++) {
            JSONObject json = jsonArray.getJSONObject(i);
            String title = "hello";
            if (json.has("title")) {
                title = json.getString("title");
                news1.setTitle(title);
            }
            String country = null;
            if (json.has("country")) {
                country = json.getString("country");
                news1.setCountry(country);
            }

            String impact = null;
            if (json.has("impact")) {
                impact = json.getString("impact");
                news1.setImpact(impact);
            }
            String date = null;
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
            }
            assert date != null;

            news.add(i, new News(title, country, impact, StringToDate(date), forecast, previous));

            out.println("New" + news1);


        }
        return news;
    }

    @Contract("null -> fail")
    public static Date StringToDate(String str) throws ParseException {//TODO implement date
        if (str == null) throw new IllegalArgumentException("Invalid date string");


        //ZonedDateTime.from(DateTimeFormatter.ofPattern( "yyyy-MM-dd'T'HH:mm:ss").parse(str.substring(0, 19)));
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
                .parse(str);
    }


    //makeRequest return JSONObject
    private static @Nullable JSONArray makeRequest() {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(NewsManager.url).openConnection();
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
            while ((inputLine = reader.readLine()) != null) {
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
