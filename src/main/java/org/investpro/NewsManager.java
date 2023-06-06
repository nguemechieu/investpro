package org.investpro;


import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Objects;

import static java.lang.System.out;
import static org.investpro.TelegramClient.requestBuilder;

public class NewsManager {

    static final String url = "https://nfs.faireconomy.media/ff_calendar_thisweek.json?version=1bed8a31256f1525dbb0b6daf6898823";
    public static Logger logger = LoggerFactory.getLogger(NewsManager.class);
    static ArrayList<News> news = new ArrayList<>();
    static News news1 = new News("", "", "", new Date(), "", "");

    public NewsManager() throws ParseException, IOException, InterruptedException {
        load();
        news.add(news1);

    }


    public static ArrayList<News> load() throws ParseException, IOException, InterruptedException {
        news = new ArrayList<>();//
        JSONArray jsonArray = Objects.requireNonNull(makeRequest());
        int length = jsonArray.length();
        for (int i = 0; i < length; i++) {
            JSONObject json = jsonArray.getJSONObject(i);
            String title = null;
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
            out.println("New" + news1);


        }
        return news;
    }

    @Contract("null -> fail")
    public static Date StringToDate(String str) throws ParseException {//TODO implement date
        if (str == null) throw new IllegalArgumentException("Invalid date string");
        //ZonedDateTime.from(DateTimeFormatter.ofPattern( "yyyy-MM-dd'T'HH:mm:ss").parse(str.substring(0, 19)));
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(str);
    }


    //makeRequest return JSONObject
    private static @Nullable JSONArray makeRequest() throws IOException, InterruptedException {

        requestBuilder.uri(
                URI.create(
                        NewsManager.url
                )
        );
        requestBuilder.header("Accept", "application/json");
        requestBuilder.header("Content-Type", "application/json");

        HttpResponse<String> response;
        HttpClient client = HttpClient.newHttpClient();
        response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            logger.error(response.body());
            return null;

        }
        return new JSONArray(response.body());


    }


}
