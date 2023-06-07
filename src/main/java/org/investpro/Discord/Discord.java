package org.investpro.Discord;


import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;


public class Discord  {
    private String url = "https://discord.com/api/oauth2/authorize?client_id=1087210119097499751&permissions=0&scope=bot";

   private String discordToken = "MTA4NzIxMDExOTA5NzQ5OTc1MQ.GOPoN8.nCemlpkjh1QeNnyK-gQh_Ihjnk0y6AunoXey64";

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDiscordToken() {
        return discordToken;
    }

    public void setDiscordToken(String discordToken) {
        this.discordToken = discordToken;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    private String clientId = "72421114444444444";
    String host =
            "https://api.discord.org/bot" ;//+
                 //   "/sendMessage?chat_id=" +

                   // "&parse_mode=Markdown&text=";
    private String clientSecret = "1087210119097499751";
    private static String apiUrl;
    private static String apiVersion;
    private String accessToken;
    private String refreshToken;
    HttpRequest.Builder requestBuilder;
    HttpClient client;

    public Discord(

            String accessToken) {

        this.accessToken = accessToken;
        requestBuilder = HttpRequest.newBuilder();
        requestBuilder.header("Client-ID", "1087210119097499751");

        requestBuilder.header("Authorization", "Bearer " + accessToken);
        requestBuilder.header("Content-Type", "application/json");
        requestBuilder.header("Accept", "application/json");
        requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36");
      requestBuilder.header("Cache-Control", "no-cache");



//        API_ENDPOINT = 'https://discord.com/api/v10'
//        CLIENT_ID = '332269999912132097'
//        CLIENT_SECRET = '937it3ow87i4ery69876wqire'

requestBuilder.header(     "REDIRECT_URI" , "https://nicememe.website");
requestBuilder.header(
        "Content-Type",
        "application/x-www-form-urlencoded"
    );

requestBuilder.header(
        "Accept",
        "application/json"
    );

//        def exchange_code(code):
        Object data = new String[]{
                "client_id:", clientId,
                "client_secret:", clientSecret,
                "grant_type", accessToken,
                //       'code': code,
                "redirect_uri", "https://nicememe.website"
        };
        requestBuilder.POST(HttpRequest.BodyPublishers.ofString(data.toString()));

//        headers = {
//                'Content-Type': 'application/x-www-form-urlencoded'
//  }
//        r = requests.post('%s/oauth2/token' % API_ENDPOINT, data=data, headers=headers)
//        r.raise_for_status()
//        return r.json()

       this. client = HttpClient.newHttpClient();




    }

    public static String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        Discord.apiUrl = apiUrl;
    }

    public static String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        Discord.apiVersion = apiVersion;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public  void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public void sendMessage(String message) throws IOException, InterruptedException {
        String url = getApiUrl() + getApiVersion() + message;
        System.out.println(url);
        String photo = "xxx";

        Thread.sleep(1000);

        System.out.println(url);

    }

    public static @NotNull Object connect() {
        String url = getApiUrl() + getApiVersion() + "/oauth2/token";
        System.out.println(url);
        return "";

    }

    public String sendPhoto(String photo) throws IOException, InterruptedException {
        String url = getApiUrl() + getApiVersion() + "/photos";
        System.out.println(url);

        Thread.sleep(1000);

        System.out.println(url);
        return  null;
    }
    public String sendPhoto(String photo, String caption) throws IOException, InterruptedException {
        String url = getApiUrl() + getApiVersion() + "/photos";
        System.out.println(url);
        Thread.sleep(1000);

        System.out.println(url);

        return null;
    }
    public  String sendVideo(String video) throws IOException, InterruptedException {
        String url = getApiUrl() + getApiVersion() + "/videos";
        System.out.println(url);
        Thread.sleep(1000);

        System.out.println(url);
        return null;
    }
    public  String sendAudio(String audio) throws IOException, InterruptedException {
        String url = getApiUrl() + getApiVersion() + "/audio";
        System.out.println(url);
        Thread.sleep(1000);

        System.out.println(url);
        return  null;
    }
    public  String sendDocument(String document) throws IOException, InterruptedException {
        String url = getApiUrl() + getApiVersion() + "/documents";
        System.out.println(url);
        Thread.sleep(1000);

        System.out.println(url);
        return null;
    }
    public  String sendSticker(String sticker) throws InterruptedException {
        String url = getApiUrl() + getApiVersion() + "/stickers";
        System.out.println(url);
        Thread.sleep(1000);

        System.out.println(url);
        return  null;
    }
    public  String sendVoice(String voice) throws InterruptedException {
        String url = getApiUrl() + getApiVersion() + "/voice";
        System.out.println(url);
        Thread.sleep(1000);

        System.out.println(url);
        return null;
    }


}