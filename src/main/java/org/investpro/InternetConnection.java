package org.investpro;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;

public class InternetConnection {

    public InternetConnection() {


    }

    public static boolean isInternetAvailable() throws IOException {

        HttpURLConnection urlConnection = (HttpURLConnection) URI.create("https://www.google.com").toURL().openConnection();
        urlConnection.setConnectTimeout(5000);
        urlConnection.setReadTimeout(5000);
        urlConnection.setRequestMethod("GET");
        urlConnection.setDoInput(true);
        urlConnection.setDoOutput(true);
        urlConnection.setUseCaches(false);
        urlConnection.setRequestProperty("User-Agent", "Mozilla/5.0");

        int responseCode = urlConnection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            urlConnection.disconnect();
            return true;
        }


        return false;
    }


}
