package org.investpro.investpro.BinanceUs;

import org.investpro.investpro.TradePair;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Date;

public class BinanceUs {
    public static final String API_URL = "https://api.binance.us";
    public static final String API_VERSION = "v1";
    public static final String API_PUBLIC_KEY = "";
    public static final String API_SECRET_KEY = "";
    public static final String TESTNET_API_URL = "https://testnet.binance.com";



    private BinanceUs() {
    }

    public static String getApiKey() {
        return API_PUBLIC_KEY;
    }

    public static String getSecretKey() {
        return API_SECRET_KEY;
    }

    public static String getBaseUrl() {
        return API_URL;
    }

    public static String getTestnetBaseUrl() {
        return TESTNET_API_URL;
    }

    public static String getVersion() {
        return API_VERSION;
    }

    public static boolean isTestnet() {
        return true;
    }

    public static boolean isPublicNetwork() {
        return true;
    }

    public static boolean isRateLimit() {
        return true;
    }

    public static boolean isMarginMode() {
        return true;
    }

    public static boolean isDemoMode() {
        return true;
    }

    public static boolean isAutoOpenBrowser() {
        return true;
    }

    public static boolean isTorEnabled() {
        return true;
    }

    public static boolean isTorSSLEnabled() {
        return true;
    }

    public static boolean isTorProxyEnabled() {
        return true;
    }

    public static boolean isTorNoProxyEnabled() {
        return true;}

    public static boolean isTorDnsEnabled() {
        return true;
    }

    public static boolean isTorFallbackEnabled() {
        return true;
    }


    public static boolean isDebug() {
        return true;
    }

    public static void createMarketOrder(TradePair tradePair, String type, String side, double size) {
    }

    //makeRequest return JSONObject
    @Contract("_, _ -> new")
    private @NotNull JSONObject makeRequest(String url, String method) throws IOException {
        HttpsURLConnection conn = (HttpsURLConnection) new URL(url).openConnection();
        conn.setRequestMethod(method);
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setUseCaches(false);
        conn.setAllowUserInteraction(false);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Accept", "html/text");
        conn.setRequestProperty("charset", "utf-8");
        conn.setRequestProperty("Accept-Charset", "utf-8");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10)");
        conn.setRequestProperty("CB-ACCESS-KEY",API_PUBLIC_KEY);//	API key as a string
        String timestamp=new Date().toString();
        String body = null;
        conn.setRequestProperty( "CB-ACCESS-SIGN"	,timestamp + method + url + body );
        //"base64-encoded signature (see Signing a Message)");
        conn.setRequestProperty(   "CB-ACCESS-TIMESTAMP",  new Date().toString());//	Timestamp for your request
        conn.setRequestProperty(  "CB-ACCESS-PASSPHRASE",	API_SECRET_KEY);//Passphrase you specified when creating the API key
        conn.setRequestProperty("Connection", "Keep-Alive");
        conn.setRequestProperty("Accept", "*/*");
        conn.setRequestProperty("Pragma", "no-cache");
        conn.setRequestProperty("Cache-Control", "no-cache");
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0" + ";q=0.9,en-GB;q=0.8,en-US;q=0.7,en;q=0.6");
        conn.setRequestProperty("Host", "https://api.binance.us");
        conn.setRequestProperty("Origin", "https://api.binance.us");
      conn.setRequestProperty("Sec-Fetch-Mode", "cors");
      conn.setRequestProperty("Sec-Fetch-Site", "same-origin");
        conn.setRequestProperty("Sec-Fetch-User", "?1");
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
