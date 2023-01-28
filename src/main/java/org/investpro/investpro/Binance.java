package org.investpro.investpro;

import javafx.scene.control.Alert ;
import javafx.scene.control.ButtonType;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;

public class Binance {
    public static final String API_URL = "https://api.binance.com";
    public static final String API_VERSION = "v1";
    public static final String API_USER = "";
    public static final String API_PASS = "";
    public static final String TESTNET_API_URL = "https://testnet.binance.com";
    public static final String TESTNET_API_VERSION = "v1";
    public static final String TESTNET_API_USER = "";
    public static final String TESTNET_API_PASS = "";
    public static final String MAINNET_API_URL = "https://api.mainnet.binance.com";
    public static final String MAINNET_API_VERSION = "v1";
    public static final String MAINNET_API_USER = "";
    public static final String MAINNET_API_PASS = "";
    public static final String TESTNET_TESTNET_API_URL = "https://testnet." + "binance.org";
    public static final String TESTNET_TESTNET_API_VERSION = "v1";
    private final String apiSecret;
    private final String apiKey;
    private final String apiPass;


    public  Binance (
            String apiKey,
            String apiSecret,
            String apiPass
            ) throws Exception {

        if (apiKey == null || apiSecret == null || apiPass == null) {
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Please fill in all the required fields",
                    ButtonType.OK);
            alert.setTitle("Binance");
                    alert.setHeaderText("Please fill in all the required fields");
            alert.showAndWait();

            throw new Exception("apiKey, apiSecret and apiPass are required");
        }else {
            this.apiKey = apiKey;
            this.apiSecret = apiSecret;
            this.apiPass = apiPass;
        }

    }

    public void init() {

    }

    public String getApiKey() {
        return apiKey;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public String getApiPass() {
        return apiPass;
    }
    JSONObject makeRequest(String url,String method) throws Exception {

        if (url == null || method == null) {
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Please fill in all the required fields",
                    ButtonType.OK);
            alert.setTitle("Binance");
        }
        else {

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod(method);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("X-MBX-APIKEY", apiKey);
            conn.setRequestProperty("X-MBX-API-PASS", apiPass);
            conn.setRequestProperty("X-MBX-VERSION", API_VERSION);
            conn.setRequestProperty("X-MBX-USER", API_USER);
            conn.setRequestProperty("X-MBX-PASS", API_PASS);
            conn.setDoOutput(true);
            conn.connect();

            if (conn.getResponseCode() == 200) {
                return new JSONObject(new String(conn.getInputStream().readAllBytes()));
            }
            else {System.err.println("Binance connection failed!");
                return new JSONObject(new String(conn.getErrorStream().readAllBytes()));
            }


        }
     return new JSONObject();
    }
}