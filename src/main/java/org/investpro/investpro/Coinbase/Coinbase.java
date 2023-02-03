package org.investpro.investpro.Coinbase;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Date;

public class Coinbase {

    protected String PASSPHRASE = "w73hzit0cgl";
    protected String API_SECRET = "FEXDflwq+XnAU2Oussbk1FOK7YM6b9A4qWbCw0TWSj0xUBCwtZ2V0MVaJIGSjWWtp9PjmR/XMQoH9IZ9GTCaKQ==";
    protected String API_KEY0 = "39ed6c9ec56976ad7fcab4323ac60dac";
    public static final String API_URL = "https://api.coinbase.com/v2/exchange-rates?currency=BTC";
    public static final String API_VERSION = "v2";
    public static final String API_USER_AGENT = "coinbase-java/" + Coinbase.API_VERSION;


    public Coinbase(
            String apiKey,
            String apiSecret,
            String apiPass
    ) throws Exception {

        if (apiKey == null || apiSecret == null || apiPass == null) {
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Please fill in all the required fields",
                    ButtonType.OK);

            alert.setHeaderText("Please fill in all the required fields");
            alert.showAndWait();

            throw new Exception("apiKey, apiSecret and apiPass are required");
        }
    }

    public void init() throws IOException {
        makeRequest(
                "https://api.coinbase.com/" ,"GET"
        );

    }


    //makeRequest return JSONObject
    private JSONObject makeRequest(String url, String method) throws IOException {
        HttpsURLConnection conn = (HttpsURLConnection) new URL(url).openConnection();
        conn.setRequestMethod(method);
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setUseCaches(false);
        conn.setAllowUserInteraction(false);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Accept", "html/text");
        //   conn.setRequestProperty("charset", "utf-8");
        // conn.setRequestProperty("Accept-Charset", "utf-8");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10)");
        conn.setRequestProperty("CB-ACCESS-KEY", API_KEY0);//	API key as a string
        String timestamp = new Date().toString();

        conn.setRequestProperty("CB-ACCESS-SIGN", timestamp + method + url);
        //"base64-encoded signature (see Signing a Message)");
        conn.setRequestProperty("CB-ACCESS-TIMESTAMP", new Date().toString());//	Timestamp for your request
        conn.setRequestProperty("CB-ACCESS-PASSPHRASE", PASSPHRASE);//Passphrase you specified when creating the API key
        conn.setRequestProperty("Connection", "Keep-Alive");
        conn.setRequestProperty("Accept", "*/*");
        conn.setRequestProperty("Pragma", "no-cache");
        conn.setRequestProperty("Cache-Control", "no-cache");
        //       conn.setRequestProperty("Accept-Language", "en-US,en;q=0" + ";q=0.9,en-GB;q=0.8,en-US;q=0.7,en;q=0.6");
        conn.setRequestProperty("Host", "https://api.telegram.org");
//        conn.setRequestProperty("Origin", "https://api.telegram.org");
//       conn.setRequestProperty("Sec-Fetch-Mode", "cors");
        //conn.setRequestProperty("Sec-Fetch-Site", "same-origin");
        //conn.setRequestProperty("Sec-Fetch-User", "?1");
        conn.setRequestProperty("Upgrade-Insecure-Requests", "1");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10)");
        conn.setRequestProperty("Accept-Encoding", "gzip, deflate");
        conn.connect();
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String inputLine;
        StringBuilder response = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }  System.out.println("COINBASE "+ response);
        in.close();

        return new JSONObject(response.toString());
    }

}
