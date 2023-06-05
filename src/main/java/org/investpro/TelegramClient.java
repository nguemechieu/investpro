package org.investpro;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.animation.Animation;
import javafx.scene.control.Alert;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.ParseException;
import java.time.Duration;
import java.util.*;

import static java.lang.System.out;

//  makeRequest("https://api.telegram.org/bot" + token + "/setWebhook");
public class TelegramClient {
    public static final ArrayList<Chat> ArrayListChat = new ArrayList<>();
    private static final Logger logger = LoggerFactory.getLogger(TelegramClient.class);




    public static int offset = 0;
    public static String message = "";
    protected static Path path;
    static boolean disable_content_type_detection = false;
     int limit = 10;
     int page = 1;
    //chat_id	//Integer or String	Yes	Unique identifier for the target chat or username of the target channel (in the format @channelusername)
    // message_thread_id;//	Integer	Optional	Unique identifier for the target message thread (topic) of the forum; for forum supergroups only
    // text	String	Yes	Text of the message to be sent, 1-4096 characters after entities parsing
    //parse_mode	String	Optional	Mode for parsing entities in the message text. See formatting options for more details.
    // entities	Array of MessageEntity	Optional	A JSON-serialized list of special entities that appear in message text, which can be specified instead of parse_mode
    static boolean disable_web_page_preview;//	Boolean	Optional	Disables link previews for links in this message
    //disable_notification	Boolean	Optional	Sends the message silently. Users will receive a notification with no sound.
    static boolean protect_content;    //Boolean	Optional	Protects the contents of the sent message from forwarding and savingString reply_to_message_id	;//Integer	Optional	If the message is a reply, ID of the original message
    static boolean allow_sending_without_reply;//	Boolean	Optional	Pass True if the message should be sent even if the specified replied-to message is not found
    // reply_markup
    static String channel_Id;
    static Entities entities = new Entities();
    static String message_thread_id;    //Integer	Optional	Unique identifier for the target message thread (topic) of the forum; for forum supergroups only
    static String Optional;////Photo caption (may also be used when resending photos by file_id), 0-1024 characters after entities parsing
    static String parse_mode = "None";//String	Optional	Mode for parsing entities in the photo caption. See formatting options for more details.
    static String caption_entities;//	Array of MessageEntity	Optional	A JSON-serialized list of special entities that appear in the caption, which can be specified instead of parse_mode
    static String disable_notification;    //Boolean	Optional	Sends the message silently. Users will receive a notification with no sound.
    static String id;
    static String title;//": "TradeExpert",
    static String type;//": "channel"
    static Game game;
    static UPDATE_MODE updateMode = UPDATE_MODE.UPDATE_NORMAL;
    static String language = "en-US";
    static String location = "";
    static String last_name = "";
    static String first_name = "";
    static boolean supportsInlineQueries = false;
    static boolean canReadAllGroupMessages = false;
    static boolean isBot = false;
    static boolean canJoinGroups = false;
    static Chat chat = new Chat("", "", "", "");
    static String result;
    static String date;
    static String author_signature;
    static String message_id;
    static String text;
    static boolean ok;
    static int channelId;
    static int channelChatId;
    static String channelName;
    static String method;
    static String chat_id;//Integer or String	Yes	Unique identifier for the target chat or username of the target channel (in the format @channelusername)
    static String chat_title = "";
    static String chat_type = "";
    static String chat_username = "";
    static File chat_voice_file_id;
    static File chat_caption_file_id;

    private static String entity_type = "";
    private static String inline_keyboard_text = "";
    private static boolean is_restricted = false;
    private static boolean is_bot = false;
    private static String chatId = "-1001648392740";
    private static int length = 9;
    private static String chat_last_name = "";
    private static String chat_photo_file_unique_id = "";
    private static String chat_first_name = "";
    private static Chat senderChat;
    private static String update_id = "";
    private static String forward_from_chat = "";
    private static String forward_from_message_id = "";
    private static String inline_query_id = "";
    private static String inline_message_id = "";
    private static File file;
    private static String animation;
    private static boolean canDeleteMessages;
    private static String firstName;
    private static String phoneNumber;
    private static String languageCode;
    private static String description;
    private static boolean canInviteUsers;
    private static boolean canPostMessages;
    private static boolean canEditMessages;
    private static boolean canChangeInfo;
    private static String lastName;
    private static String photo;
    private static String caption;
    private static String reply_to_message_id;
    private static ObjectMapper mapper;
    private static String from_id;
    private static String token;
    private static String chat_photo_file_id = "";
    private static String username;
    private static Object marketInfo;
    private static boolean isOnline;
    private static String networkError;
    public int MinAfter, LastUpd, Upd;
    protected String host = "https://api.telegram.org";
    KeyboardButton[] keyboard = new KeyboardButton[]{new KeyboardButton("1", KeyboardButtonType.BUTTON_TYPE_SINGLE_LINE), new KeyboardButton("2", KeyboardButtonType.BUTTON_TYPE_SINGLE_LINE), new KeyboardButton("3", KeyboardButtonType.BUTTON_TYPE_SINGLE_LINE), new KeyboardButton("4", KeyboardButtonType.BUTTON_TYPE_SINGLE_LINE), new KeyboardButton("5", KeyboardButtonType.BUTTON_TYPE_SINGLE_LINE), new KeyboardButton("6", KeyboardButtonType.BUTTON_TYPE_SINGLE_LINE), new KeyboardButton("7", KeyboardButtonType.BUTTON_TYPE_SINGLE_LINE), new KeyboardButton("8", KeyboardButtonType.BUTTON_TYPE_SINGLE_LINE), new KeyboardButton("9", KeyboardButtonType.BUTTON_TYPE_SINGLE_LINE), new KeyboardButton("0", KeyboardButtonType.BUTTON_TYPE_SINGLE_LINE)};
    static String reply_markup = "";
    int Now;
    int BeforeNewsStop = 30;
    int AfterNewsStop = 60;
    boolean FirstAlert, SecondAlert;
    boolean sendNews = true;
    File chat_video_file_id;
    private static JsonNode response;
    private static String address = "";
    private PrintStream res;
    private boolean Signal;
    private boolean Vhigh;
    private boolean Vmedium, Vlow;
    private boolean Next;
    private boolean NewsFilter;
    private boolean trade;
    private String from_first_name;
    private String judulnews;
    private boolean DrawLines;
    private String lastMessage;
    private String chatDescription;

    static HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    static HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
    private final DoubleSummaryStatistics initializationLatch;
    File thumb = new File("/photo/400PngdpiLogoBW.png");
    private static double latitude;
    private static double longitude;
    private String city;
    private String country;
    private String timezone;
    private static String foursquare_id;
    private static String foursquare_type;
    private String foursquare_name;
    private static String google_place_id;
    private static String google_place_type;
    private Duration duration;


    public static int getLength() {
        return length;
    }

    public static void setLength(int length) {
        TelegramClient.length = length;
    }

    public static String getEntity_type() {
        return entity_type;
    }

    public static void setEntity_type(String entity_type) {
        TelegramClient.entity_type = entity_type;
    }

    public static boolean isIs_bot() {
        return is_bot;
    }

    public static void setIs_bot(boolean is_bot) {
        TelegramClient.is_bot = is_bot;
    }

    public static UPDATE_MODE getUpdateMode() {
        return updateMode;
    }

    public static void sendMessageToChannel(String s) {
        requestBuilder.uri(URI.create("https://api.telegram.org/bot" + token + "/sendMessage?chat_id=" + chat_id + "&text=" + s + "&parse_mode=Markdown&disable_web_page_preview=true&disable_notification=" + disable_notification + "&reply_to_message_id=" + reply_to_message_id + "&reply_markup=" + reply_markup + "&parse_mode=Markdown&disable_web_page_preview=true&disable_notification=" + disable_notification + "&reply_to_message_id=" + reply_to_message_id + "&reply_markup=" + reply_markup + "&parse_mode=Markdown&disable_web_page_preview=true&disable_notification=" + disable_notification + "&reply_to_message_id=" + reply_to_message_id + "&reply_markup=" + reply_markup + "&parse_mode=Markdown&disable_web_page_preview=true&disable_"));

        //.uri(URI.create("https://api.telegram.org/bot" + token+ "/sendMessage?chat_id=" + chat_id + "&text=" + s + "&parse_mode=Markdown&disable_web_page_preview=true&disable_notification=" + disable_notification + "&reply_to_message_id=" + reply_to_message_id + "&reply_markup=" + reply_markup + "&parse_mode=Markdown&disable_web_page_preview=true&disable_notification=" + disable_notification + "&reply_to_message_id=" + reply_to_message_id + "&reply_markup=" + reply_markup + "&parse_mode=Markdown&disable_web_page_preview=true&disable_notification=" + disable_notification + "&reply_to_message_id=" + reply_to_message_id + "&reply_markup=" + reply_markup    )
        requestBuilder.POST(HttpRequest.BodyPublishers.noBody());
        client.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString()).thenApply(
                HttpResponse::body
        ).thenApply(
                response -> {
                    try {
                        TelegramClient.response = new ObjectMapper().readTree(response);
                        TelegramClient.response = TelegramClient.response.get("result");
                        TelegramClient.response = TelegramClient.response.get("ok");
                        logger.info(TelegramClient.response.toString());
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                    }
                    return null;
                });

    }


    public void setUpdateMode(UPDATE_MODE updateMode) {
        TelegramClient.updateMode = updateMode;
    }


    public static String getLocation() {
        return location;
    }

    private static void setLocation(String locationString) {
        if (locationString != null && !locationString.isEmpty()) {
            location = locationString;
        }
    }

    public static String getLast_name() {
        return last_name;
    }

    public void setLast_name(String last_name) {
        TelegramClient.last_name = last_name;
    }

    public static String getChat_id() {
        return chat_id;
    }

    public static void setChat_id(String chat_id) {
        TelegramClient.chat_id = chat_id;
    }

    public static Game getGame() {
        return game;
    }

    public void setGame(Game game) {
        TelegramClient.game = game;
    }

    public static String getInline_message_id() {
        return inline_message_id;
    }

    public static void setInline_message_id(String inline_message_id) {
        TelegramClient.inline_message_id = inline_message_id;
    }

    public static String getInline_query_id() {
        return inline_query_id;
    }

    public static void setInline_query_id(String inline_query_id) {
        TelegramClient.inline_query_id = inline_query_id;
    }

    public static String getForward_from_chat() {
        return forward_from_chat;
    }

    public static void setForward_from_chat(String forward_from_chat) {
        TelegramClient.forward_from_chat = forward_from_chat;
    }

    public static String getForward_from_message_id() {
        return forward_from_message_id;
    }

    public static void setForward_from_message_id(String forward_from_message_id) {
        TelegramClient.forward_from_message_id = forward_from_message_id;
    }

    public static String getUpdate_id() {
        return update_id;
    }

    public static void setUpdate_id(String update_id) {
        TelegramClient.update_id = update_id;
    }

    public static Chat getSenderChat() {
        return senderChat;
    }

    public static void setSenderChat(Chat senderChat) {
        TelegramClient.senderChat = senderChat;
    }

    public static String getChat_photo_file_unique_id() {
        return chat_photo_file_unique_id;
    }

    public static void setChat_photo_file_unique_id(String chat_photo_file_unique_id) {
        TelegramClient.chat_photo_file_unique_id = chat_photo_file_unique_id;
    }

    public static int getOffset() {
        return offset;
    }

    public static void setOffset(int offset) {
        TelegramClient.offset = offset;
    }

    public static boolean isIs_restricted() {
        return is_restricted;
    }

    public static void setIs_restricted(boolean is_restricted) {
        TelegramClient.is_restricted = is_restricted;
    }

    public static String getInline_keyboard_text() {
        return inline_keyboard_text;
    }

    public static void setInline_keyboard_text(String inline_keyboard_text) {
        TelegramClient.inline_keyboard_text = inline_keyboard_text;
    }

    public static String getChat_last_name() {
        return chat_last_name;
    }

    public void setChat_last_name(String chat_last_name) {
        TelegramClient.chat_last_name = chat_last_name;
    }

    public TelegramClient(String token) throws IOException, TelegramApiException, InterruptedException, ParseException {

        TelegramClient.token = token;
        address = "https://api.telegram.org/bot" + token;
        this.res = System.out;
        this.initializationLatch = new DoubleSummaryStatistics();


        requestBuilder.header("Content-Type", "application/json");
        requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36");
        requestBuilder.header("Accept", "application/json");
        requestBuilder.header("Authorization", "Bearer " + token);
        requestBuilder.header("Cache-Control", "no-cache");
        getMe();

        sendKeyboard(
                new KeyboardButton[]{new KeyboardButton("1", KeyboardButtonType.BUTTON_TYPE_SINGLE_LINE),
                        new KeyboardButton("2", KeyboardButtonType.BUTTON_TYPE_SINGLE_LINE),
                        new KeyboardButton("3", KeyboardButtonType.BUTTON_TYPE_SINGLE_LINE),
                        new KeyboardButton("4", KeyboardButtonType.BUTTON_TYPE_SINGLE_LINE),
                        new KeyboardButton("5", KeyboardButtonType.BUTTON_TYPE_SINGLE_LINE),
                        new KeyboardButton("6", KeyboardButtonType.BUTTON_TYPE_SINGLE_LINE),
                        new KeyboardButton("7", KeyboardButtonType.BUTTON_TYPE_SINGLE_LINE),
                        new KeyboardButton("8", KeyboardButtonType.BUTTON_TYPE_SINGLE_LINE),
                        new KeyboardButton("9", KeyboardButtonType.BUTTON_TYPE_SINGLE_LINE),
                        new KeyboardButton("0", KeyboardButtonType.BUTTON_TYPE_SINGLE_LINE)});


        logger.info("Telegram Client Created");
    }

    public static @NotNull List<String> getCommands() {
        List<String> commands = new ArrayList<>();
        commands.add("/Accounts");
        commands.add("/Charts");
        commands.add("/Chats");
        commands.add("/Trade");
        commands.add("/Screenshot");
        commands.add("/Settings");
        commands.add("/History");
        commands.add("/Help");
        commands.add("/invite");
        commands.add("/leave");
        commands.add("/lock");
        commands.add("/login");
        commands.add("/logout");
        commands.add("/me");
        commands.add("/start");
        commands.add("/stop");
        return commands;
    }

    public static boolean isSupportsInlineQueries() {
        return supportsInlineQueries;
    }

    public static void setSupportsInlineQueries(boolean supportsInlineQueries) {
        TelegramClient.supportsInlineQueries = supportsInlineQueries;
    }

    public static boolean isCanReadAllGroupMessages() {
        return canReadAllGroupMessages;
    }

    public static void setCanReadAllGroupMessages(boolean canReadAllGroupMessages) {
        TelegramClient.canReadAllGroupMessages = canReadAllGroupMessages;
    }

    public static boolean isIsBot() {
        return isBot;
    }

    public static void setIsBot(boolean isBot) {
        TelegramClient.isBot = isBot;
    }

    public static boolean isCanJoinGroups() {
        return canJoinGroups;
    }

    public static void setCanJoinGroups(boolean canJoinGroups) {
        TelegramClient.canJoinGroups = canJoinGroups;
    }

    public static void setFile(File file) {
        TelegramClient.file = file;
    }

    public static String getAnimation() {
        return animation;
    }

    public static void setAnimation(String animation) {
        TelegramClient.animation = animation;
    }

    public static boolean isCanDeleteMessages() {
        return canDeleteMessages;
    }

    public static void setCanDeleteMessages(boolean canDeleteMessages) {
        TelegramClient.canDeleteMessages = canDeleteMessages;
    }

    public static String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        TelegramClient.firstName = firstName;
    }

    public static String getDescription() {
        return description;
    }

    public static void setDescription(String description) {
        TelegramClient.description = description;
    }

    public static boolean isCanInviteUsers() {
        return canInviteUsers;
    }

    public static void setCanInviteUsers(boolean canInviteUsers) {
        TelegramClient.canInviteUsers = canInviteUsers;
    }

    public static boolean isCanPostMessages() {
        return canPostMessages;
    }

    public static void setCanPostMessages(boolean canPostMessages) {
        TelegramClient.canPostMessages = canPostMessages;
    }

    public static boolean isCanEditMessages() {
        return canEditMessages;
    }

    public static void setCanEditMessages(boolean canEditMessages) {
        TelegramClient.canEditMessages = canEditMessages;
    }

    public static boolean isCanChangeInfo() {
        return canChangeInfo;
    }

    public static void setCanChangeInfo(boolean canChangeInfo) {
        TelegramClient.canChangeInfo = canChangeInfo;
    }

    public static String getLastName() {
        return lastName;
    }

    public static void setLastName(String lastName) {
        TelegramClient.lastName = lastName;
    }

    public static String getPhoto() {
        return photo;
    }

    public static void setPhoto(String photo) {
        TelegramClient.photo = photo;
    }

    public static String getCaption() {
        return caption;
    }

    public static void setCaption(String caption) {
        TelegramClient.caption = caption;
    }

    public static boolean isDisable_web_page_preview() {
        return disable_web_page_preview;
    }

    public static void setDisable_web_page_preview(boolean disable_web_page_preview) {
        TelegramClient.disable_web_page_preview = disable_web_page_preview;
    }

    public static boolean isProtect_content() {
        return protect_content;
    }

    public static void setProtect_content(boolean protect_content) {
        TelegramClient.protect_content = protect_content;
    }

    public static boolean isAllow_sending_without_reply() {
        return allow_sending_without_reply;
    }

    public static void setAllow_sending_without_reply(boolean allow_sending_without_reply) {
        TelegramClient.allow_sending_without_reply = allow_sending_without_reply;
    }

    public static String getChannel_Id() {
        return channel_Id;
    }

    public static void setChannel_Id(String channel_Id) {
        TelegramClient.channel_Id = channel_Id;
    }

    public static String getMessage_thread_id() {
        return message_thread_id;
    }

    public static void setMessage_thread_id(String message_thread_id) {
        TelegramClient.message_thread_id = message_thread_id;
    }

    public static String getOptional() {
        return Optional;
    }

    public static void setOptional(String optional) {
        Optional = optional;
    }

    public static String getParse_mode() {
        return parse_mode;
    }

    public static void setParse_mode(String parse_mode) {
        TelegramClient.parse_mode = parse_mode;
    }

    public static String getCaption_entities() {
        return caption_entities;
    }

    public static void setCaption_entities(String caption_entities) {
        TelegramClient.caption_entities = caption_entities;
    }

    public static String getDisable_notification() {
        return disable_notification;
    }

    public static void setDisable_notification(String disable_notification) {
        TelegramClient.disable_notification = disable_notification;
    }

    public static String getId() {
        return id;
    }

    public static void setId(String id) {
        TelegramClient.id = id;
    }

    public static String getTitle() {
        return title;
    }

    public static void setTitle(String title) {
        TelegramClient.title = title;
    }

    public static String getType() {
        return type;
    }

    public static void setType(String type) {
        TelegramClient.type = type;
    }

    public static Entities getEntities() {
        return entities;


    }

    public void setEntities(Entities entities) {
        TelegramClient.entities = entities;
    }

    public static @Nullable Object connect() {
        isOnline = true;

        String url = "https://api.telegram.org/bot" + token + "/getMe";


        requestBuilder.uri(URI.create(url));
        requestBuilder.header("Content-Type", "application/json");
        requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36");
        requestBuilder.header("Accept", "application/json");
        requestBuilder.header("Authorization", "Bearer " + token);
        requestBuilder.method("GET", HttpRequest.BodyPublishers.noBody());
        client.sendAsync(requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString()
        ).thenApply(
                HttpResponse::body
        ).thenApply(response -> {
            try {
                JSONObject jsonObject = new JSONObject(response);
                marketInfo = jsonObject.getJSONObject("result");

                return mapper.readValue(response, NETWORK_RESPONSE.class);


            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });

             return null;
        }

    public String getFrom_first_name() {
        return from_first_name;
    }

    public void setFrom_first_name(String from_first_name) {
        this.from_first_name = from_first_name;
    }

    public String getFrom_id() {
        return from_id;
    }

    public void setFrom_id(String from_id) {
        TelegramClient.from_id = from_id;
    }

    public String getChat_first_name() {
        return chat_first_name;
    }

    public void setChat_first_name(String chat_first_name) {
        TelegramClient.chat_first_name = chat_first_name;
    }

    //makeRequest return JSONObject
    @Contract("_, _ -> new")
    private static @NotNull JSONObject makeRequest(String url, @NotNull String method) {
        HttpResponse<String> response;
        try {   url = url + "?offset=" + offset + "&limit=" + length;



            requestBuilder.uri(URI.create(url));

            requestBuilder.GET();
            requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.)");
            requestBuilder.header("Accept", "application/json");

            response = client.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        try {



            NETWORK_RESPONSE networkResponse;

            if (response.statusCode() != 200) {
                isOnline = false;

                logger.info("Error: " + response.statusCode() + " " + response.body());

                return new JSONObject();
            } else {
                isOnline = true;


            }

        } catch (Exception e) {
            networkError = e.getMessage();
            new Message(
                    "Error: " + " ", e.getMessage()
            );

        }
       /// assert response != null;
        out.println(response.body());
        return new JSONObject(response.body());
    }

    public void setReply_markup(String reply_markup) {
        this.reply_markup = reply_markup;
    }

    public String getChat_photo_file_id() {
        return chat_photo_file_id;
    }

    public void setChat_photo_file_id(String chat_photo_file_id) {
        TelegramClient.chat_photo_file_id = chat_photo_file_id;
    }

    public String getChat_title() {
        return chat_title;
    }

    public void setChat_title(String chat_title) {
        TelegramClient.chat_title = chat_title;
    }

    public String getChat_type() {
        return chat_type;
    }

    public void setChat_type(String chat_type) {
        TelegramClient.chat_type = chat_type;
    }

    public String getChat_username() {
        return chat_username;
    }

    public void setChat_username(String chat_username) {
        TelegramClient.chat_username = chat_username;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        TelegramClient.language = language;
    }

    public String getFirst_name() {
        return first_name;
    }

    public void setFirst_name(String first_name) {
        TelegramClient.first_name = first_name;
    }

    public static String getReply_markup() {
        return reply_markup;
    }

    public void setReply_to_message_id(String reply_to_message_id) {
        TelegramClient.reply_to_message_id = reply_to_message_id;
    }

    public void setChat(Chat chat) {
        TelegramClient.chat = chat;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        TelegramClient.result = result;
    }

    public String getDate() {
        return date;
    }

    public static void setDate(String date) {
        TelegramClient.date = date;
    }

    public String getAuthor_signature() {
        return author_signature;
    }

    public void setAuthor_signature(String author_signature) {
        TelegramClient.author_signature = author_signature;
    }

    public String getMessage_id() {
        return message_id;
    }

    public static void setMessage_id(String message_id) {
        TelegramClient.message_id = message_id;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        TelegramClient.message = message;
    }

    public String getText() {
        return text;
    }

    public static void setText(String text) {
        TelegramClient.text = text;
    }

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        TelegramClient.ok = ok;
    }

    public int getChannelId() {
        return channelId;
    }

    public void setChannelId(int channelId) {
        TelegramClient.channelId = channelId;
    }

    public int getChannelChatId() {
        return channelChatId;
    }

    public void setChannelChatId(int channelChatId) {
        TelegramClient.channelChatId = channelChatId;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        TelegramClient.channelName = channelName;
    }

    public String getSymbol() {

        return "https://api.telegram.org/bot" + "/getChat" + "?chat_id=" + getChatId() + "&disable_content_type_detection=" + disable_content_type_detection;
    }

    public String getBalance() {

        return "https://api.telegram.org/bot" + getToken() + "/getChat" + "?chat_id=" + getChatId() + "&disable_content_type_detection=" + disable_content_type_detection;

    }

    void run() throws IOException, InterruptedException, ParseException {


        if (isOnline()) {
            getUpdates();// update the chat client
            if (getUpdates().length() > 0) {

                //Trade news on
                newsTrade();
            }

        }


    }

    public static String getChatId() {
        return chatId;
    }

    public static void setChatId(String chatId) {
        TelegramClient.chatId = chatId;
    }

    //+------------------------------------------------------------------+
    String ReplyKeyboardHide() {
        return ("{\"hide_keyboard\": false}");
    }

    //+------------------------------------------------------------------+
    String ForceReply() {


        // ForceReply
        //  Upon receiving a message with this object, Telegram clients will display a reply interface to the user (act as if the user has selected the bot's message and tapped 'Reply'). This can be extremely useful if you want to create user-friendly step-by-step interfaces without having to sacrifice privacy mode.

        //Field	Type	Description
        //force_reply	,//True	Shows reply interface to the user, as if they manually selected the bot's message and tapped 'Reply'
        // input_field_placeholder	,//String	Optional. The placeholder to be shown in the input field when the reply is active; 1-64 characters
        // selective	;//Boolean	Optional. Use this parameter if you want to force reply from specific users
        return ("&force_reply=" + false + "&input_field_placeholder=" + 0 + "&selective=" + false);
    }
//
//
//    private @NotNull String ReplyKeyboardMarkup() {
//
//
//        reply_markup = Arrays.toString(keyboard) +    //Array of KeyboardButton	Array of button rows, each represented by an Array of KeyboardButton objects
//                "&resize_keyboard=" + false +//Boolean	Optional. Requests clients to resize the keyboard vertically for optimal fit (e.g., make the keyboard smaller if there are just two rows of buttons). Defaults to false, in which case the custom keyboard is always of the same height as the app's standard keyboard.
//                "&one_time_keyboard=" + false +//	Boolean	Optional. Requests clients to hide the keyboard as soon as it's been used. The keyboard will still be available, but clients will automatically display the usual letter-keyboard in the chat - the user can press a special button in the input field to see the custom keyboard again. Defaults to false.
//                "&input_field_placeholder=" + "" +    //String	Optional. The placeholder to be shown in the input field when the keyboard is active; 1-64 characters
//                "&selective=" + false;////Boolean	Optional. Use this parameter if you want to show the keyboard to specific users only. Targets: 1) users that are @mentioned in the text of the Message object; 2) if the bot's message is a reply (has reply_to_message_id), sender of the original message.
//        //       Example: A user requests to change the bots' language, bot replies to the request with a keyboard to select the new language. Other users in the group don't see the keyboard.
//        return "&reply_markup=" + reply_markup;
//    }

    public static void sendMessage(String text) throws IOException, InterruptedException {

        String path = getToken() + "/sendMessage";//&chat_id=" + chatId + "&text=" + text + "&parse_mode=Markdown";


        boolean one_time_keyboard = true;
        String input_field_placeholder = "";
        boolean selective = true;//"&chat_id=" + chatId + "&text=" + text + "&parse_mode=Markdown";
        String params = "as_HTML=" + true + "silently=" + true;
        //"&parse_mode=Markdown" + "&disable_notification=" + disable_notification + "&protect_content=" + protect_content + "&allow_sending_without_reply=" + allow_sending_without_reply + "&channel_id=" + getChannel_Id() + "&reply_markup=" + ReplyKeyboardMarkup() + "&force_reply=" + ForceReply() + "&reply_to_message_id=" + getReplyToMessageId() + "&one_time_keyboard=" + one_time_keyboard + "&input_field_placeholder=" + input_field_placeholder + "&selective=" + selective;

        sendChatAction(ENUM_CHAT_ACTION.typing);
        String url = "https://api.telegram.org/bot";
//setHost("https://api.telegram.org");
        HttpResponse<String> response;

//        private static final String INSTANCE_ID = "YOUR_INSTANCE_ID_HERE";
//        private static final String CLIENT_ID = "YOUR_CLIENT_ID_HERE";
//        private static final String CLIENT_SECRET = "YOUR_CLIENT_SECRET_HERE";
//        private static final String TG_GATEWAY_URL = "https://api.whatsmate.net/v3/telegram/group/text/message/" + INSTANCE_ID;


        String group_name = "Muscle Men Club";  //  TODO: Specify the group name here.
        String group_admin = "19159876123";     //  TODO: Specify the number of the group admin here.
        String message = "Your six-pack is on the way!";

        //  TelegramGroupTextSender.sendGroupMessage(group_name, group_admin, message);


        /**
         * Sends out a group message via WhatsMate Telegram Gateway.
         */
        //  public static void sendGroupMessage(String group_name, String group_admin, String message) throws Exception {
        // TODO: Should have used a 3rd party library to make a JSON string from an object
        String jsonPayload = "{" + "\"chat_id\": \"" + getChatId() + "\"," +
                "\"text\":\"" +
                text +
                "\"," +
                "\"parse_mode\":\"" + "Markdown" +
                "\"" +
                "}";

        URL urls = new URL(url + path);


        HttpURLConnection conn = (HttpURLConnection) urls.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        // conn.setRequestProperty("X-WM-CLIENT-ID", 12332);
        conn.setRequestProperty("Authorization", getToken());
        conn.setRequestProperty("Content-Type", "application/json");

        OutputStream os = conn.getOutputStream();
        os.write(jsonPayload.getBytes());
        os.flush();
        os.close();

        int statusCode = conn.getResponseCode();
        logger.info("Telegram " + statusCode + " " + conn.getResponseMessage());

        System.out.println("Status Code: " + statusCode);
        BufferedReader br = new BufferedReader(new InputStreamReader(
                (statusCode == 200) ? conn.getInputStream() : conn.getErrorStream()
        ));
        String output;
        while ((output = br.readLine()) != null) {
            logger.info(output);
        }
        conn.disconnect();


    }

    private void setHost(String s) {
        host = s;
    }

    private String getReplyToMessageId() {
        return reply_to_message_id;
    }

    public static String getToken() {
        return token;
    }
// sender_chat": {
//    "id": -1001659738763,
//    "title": "TradeExpert",
//            "username": "tradeexpert_infos",
//            "type": "channel"

    public void setToken(String token1) {
        token = token1;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        TelegramClient.username = username;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        TelegramClient.phoneNumber = phoneNumber;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public void setLanguageCode(String languageCode) {
        TelegramClient.languageCode = languageCode;
    }

    void sendGame(String chat_id, String game_short_name) throws IOException, InterruptedException {
//    Use this method to send a game. On success, the sent Message is returned.
//
//    Parameter	Type	Required	Description
//    chat_id	Integer	Yes	Unique identifier for the target chat
//    message_thread_id	Integer	Optional	Unique identifier for the target message thread (topic) of the forum; for forum supergroups only
//    game_short_name	String	Yes	Short name of the game, serves as the unique identifier for the game. Set up your games via @BotFather.
//    disable_notification	Boolean	Optional	Sends the message silently. Users will receive a notification with no sound.
//    protect_content	Boolean	Optional	Protects the contents of the sent message from forwarding and saving
//    reply_to_message_id	Integer	Optional	If the message is a reply, ID of the original message
//    allow_sending_without_reply	Boolean	Optional	Pass True if the message should be sent even if the specified replied-to message is not found
//    reply_markup	InlineKeyboardMarkup	Optional	A JSON-serialized object for an inline keyboard. If empty, one 'Play game_title' button will be shown. If not empty, the first button must launch the game.


        makeRequest("https://api.telegram.org/bot" + token + "/sendGame" + "?chat_id=" + chat_id + "&message_thread_id=" + message_thread_id + "&game_short_name=" + game_short_name + "&disable_notification=" + disable_notification + "&protect_content=" + protect_content + "&reply_to_message_id=" + reply_to_message_id + "&allow_sending_without_reply=" + allow_sending_without_reply + "&reply_markup=" + reply_markup, "POST");
    }

    public static String getReply_to_message_id() {
        return reply_to_message_id;
    }

    @Contract(pure = true)
    private @NotNull String getCreateKeyBoard() {


//enum KEYS_TRADE    (m_lang==LANGUAGE_EN)?"[[\"/BUY\"],[\"/SELL\"],[\"/BUYLIMIT\"],[\"/SELLIMIT\"],[\"/BUYSTOP\"],[\"/SELLSTOP\"]":"[[\"Информация\"],[\"Котировки\"],[\"Графики\"]]"

//enum KEYB_SYMBOLS "[[\""+EMOJI_TOP+"\",\"/GBPUSD\",\"/EURUSD\"],[\"/AUDUSD\",\"/USDJPY\",\"/EURJPY\"],[\"/USDCAD\",\"/USDCHF\",\"/EURCHF\"]]"

//enum KEYB_PERIODS cccc


        return "[[" + "EMOJI_TOP" + ",\"M1\",\"M5\",\"M15\"],[\"" + "EMOJI_BACK" + "\",\"M30\",\"H1\",\"H4\"],[\" \",\"D1\",\"W1\",\"MN1\"]]";


    }

    //Create payment keyboard
    public void createPayment() throws IOException {
        sendChatAction(ENUM_CHAT_ACTION.choose_sticker);


    }

    public void createInvoice(String title, String description, String amount, String currency, String payment_mode, String payment_card_number, String payment_card_expiration_month, String payment_card_expiration_year, String payment_card_cvv, String payment_card_holder_name, String payment_card_number_type, String payment_card_type, String payment_card_brand, String payment_card_last4) throws IOException {
        sendChatAction(ENUM_CHAT_ACTION.upload_photo);

        try {
            String photo_width = null;
            String start_parameter = null;
            String currency_code = null;
            String photo_height = null;
            String photo_description = null;
            makeRequest("https://api.telegram.org/bot" + getToken() + "/sendInvoice" + "?chat_id=" + getChatId() + "&title=" + title + "&description=" + description + "&start_parameter=" + start_parameter + "&currency_code=" + currency_code + "&photo=" + file + "&photo_width=" + photo_width + "&photo_height=" + photo_height + "&photo_description=" + photo_description + "message_thread_id=" + message_thread_id + "&disable_notification=" + disable_notification + "&allow_sending_without_reply=" + allow_sending_without_reply, "POST");


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getMethod() {
        return method;
    }

    public static void setMethod(String method) {
        TelegramClient.method = method;
    }

    @Override
    public String toString() {
        return "TelegramClient{" + "chat=" + chat + ", result='" + result + '\'' + ", date=" + date + ", author_signature='" + author_signature + '\'' + ", message_id='" + message_id + '\'' + ", text='" + text + '\'' + ", ok=" + ok + ", channelId=" + channelId + ", channelChatId=" + channelChatId + ", channelName='" + channelName + '\'' + ", file=" + file + ", animation='" + animation + '\'' + ", canDeleteMessages=" + canDeleteMessages + ", firstName='" + firstName + '\'' + ", username='" + username + '\'' + ", phoneNumber='" + phoneNumber + '\'' + ", languageCode='" + languageCode + '\'' + ", description='" + description + '\'' + ", canInviteUsers=" + canInviteUsers + ", canPostMessages=" + canPostMessages + ", canEditMessages=" + canEditMessages + ", canChangeInfo=" + canChangeInfo + ", lastName='" + lastName + '\'' + ", photo='" + photo + '\'' + ", token='" + token + '\'' + ", chatId='" + getChatId() + '\'' + ", method='" + method + '\'' + '}';
    }

    @Contract(pure = true)
    private @Nullable String getMessageId() {

        if (message_thread_id == null) return null;

        if (message_thread_id.equals("")) return null;

        return message_thread_id;
    }

    void logOut() {

        setMethod("DELETE");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/logout", "POST");


    }
    // static String entities;//	Array of MessageEntity	Optional	A JSON-serialized list of special entities that appear in message text, which can be specified instead of parse_mode

    void close() throws IOException, InterruptedException {

        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/close" + "?chat_id=" + chat_id, "POST");

    }

    void forwardMessage() throws IOException, InterruptedException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/forwardMessage", "POST");
    }

    void copyMessage(String reply_markup) throws IOException {

        makeRequest("https://api.telegram.org/bot" + getToken() + "/copyMessage" + "?chat_id=" + chatId + "&text=" + text + "&caption=" + caption + "&parse_mode=" + parse_mode + "&disable_notification=" + disable_notification + "&reply_to_message_id=" + reply_to_message_id + "&allow_sending_without_reply=" + allow_sending_without_reply + "&reply_markup=" + reply_markup + "&entities=" + entities + "&caption_entities=" + caption_entities, "POST");


    }

    //sendPhoto to Telegram
    public static void sendPhoto(File file) {
        try {


            String boundary =
                    "-------3141592653589793238462643383279502884197169399375105820974944592";
            String contentDisposition = "form-data; name=\"photo\"; filename=\"" + file.getName() + "\"";
            requestBuilder.header(
                    "Content-Type",
                    "multipart/form-data; boundary=" + boundary
            );
            requestBuilder.header(
                    "Content-Disposition",
                    contentDisposition
            );


            requestBuilder.header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.163 Safari/537.36"
            );

            requestBuilder.header(
                    "Accept",
                    "application/json"
            );

            requestBuilder.header(
                    "Origin",
                    "https://api.telegram.org"
            );

            requestBuilder.header(
                    "Referer",
                    "https://api.telegram.org/bot" + token + "/getUpdates"
            );

            sendChatAction(
                    ENUM_CHAT_ACTION.upload_photo
            );
            sendVenue();
            String url = "https://api.telegram.org/bot" + token + "/sendPhoto";
//
//            Parameter	Type	Required	Description
//            chat_id	Integer or String	Yes	Unique identifier for the target chat or username of the target channel (in the format @channelusername)
//            message_thread_id	Integer	Optional	Unique identifier for the target message thread (topic) of the forum; for forum supergroups only
//            photo	InputFile or String	Yes	Photo to send. Pass a file_id as String to send a photo that exists on the Telegram servers (recommended), pass an HTTP URL as a String for Telegram to get a photo from the Internet, or upload a new photo using multipart/form-data. The photo must be at most 10 MB in size. The photo's width and height must not exceed 10000 in total. Width and height ratio must be at most 20. More information on Sending Files »
//            caption	String	Optional	Photo caption (may also be used when resending photos by file_id), 0-1024 characters after entities parsing
//            parse_mode	String	Optional	Mode for parsing entities in the photo caption. See formatting options for more details.
//            caption_entities	Array of MessageEntity	Optional	A JSON-serialized list of special entities that appear in the caption, which can be specified instead of parse_mode
//            has_spoiler	Boolean	Optional	Pass True if the photo needs to be covered with a spoiler animation
//            disable_notification	Boolean	Optional	Sends the message silently. Users will receive a notification with no sound.
//            protect_content	Boolean	Optional	Protects the contents of the sent message from forwarding and saving
//            reply_to_message_id	Integer	Optional	If the message is a reply, ID of the original message
//            allow_sending_without_reply	Boolean	Optional	Pass True if the message should be sent even if the specified replied-to message is not found
//            reply_markup	InlineKeyboardMarkup or ReplyKeyboardMarkup or ReplyKeyboardRemove or ForceReply	Optional	Additional interface options. A JSON-serialized object for an inline keyboard, custom reply keyboard, instructions to remove reply keyboard or to force a reply from the user.

            logger.info("File Size: " + file.length());
            logger.info("File Name: " + file.getName());
            requestBuilder.POST(
                    HttpRequest.BodyPublishers.ofString(
                            "{\"chat_id\":\"" + getChatId() + "\"," +
                                    "\"photo\":\"" + file.toURI() + "\"," +
                                    "\"caption\":\"" + getCaption() + "\"," +
                                    "\"parse_mode\":\"" + getParse_mode() + "\"," +
                                    "\"caption_entities\":\"" + getCaption_entities() + "\"," +
                                    "\"disable_notification\":\"" + getDisable_notification() + "\"," +
                                    "\"protect_content\":\"" + protect_content + "\"," +
                                    "\"reply_to_message_id\":\"" + getReply_to_message_id() + "\"," +
                                    "\"allow_sending_without_reply\":\"" + allow_sending_without_reply + "\"," +
                                    "\"reply_markup\":\"" + getReply_markup() + "\"}"
                    )

            );
            requestBuilder.uri(
                    URI.create(url)
            );
            HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            System.out.println(response.body());
            System.out.println(response.statusCode());
            System.out.println(response.headers());
            if (response.statusCode() != 200) {
                System.out.println(response.body());
                System.out.println(response.statusCode());
                System.out.println(response.headers());
                System.out.println(response.body());
                new Message(

                        String.valueOf(response.statusCode())
                        ,
                        response.body()
                );
            } else {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Success");
                alert.setHeaderText(null);
                alert.setContentText("Photo sent");
                alert.showAndWait();
            }


//
//            chat_id	Integer or String	Yes	Unique identifier for the target chat or username of the target channel (in the format @channelusername)
//            message_thread_id	Integer	Optional	Unique identifier for the target message thread (topic) of the forum; for forum supergroups only
//            photo	InputFile or String	Yes	Photo to send. Pass a file_id as String to send a photo that exists on the Telegram servers (recommended), pass an HTTP URL as a String for Telegram to get a photo from the Internet, or upload a new photo using multipart/form-data. The photo must be at most 10 MB in size. The photo's width and height must not exceed 10000 in total. Width and height ratio must be at most 20. More information on Sending Files »
//            caption	String	Optional	Photo caption (may also be used when resending photos by file_id), 0-1024 characters after entities parsing
//            parse_mode	String	Optional	Mode for parsing entities in the photo caption. See formatting options for more details.
//            caption_entities	Array of MessageEntity	Optional	A JSON-serialized list of special entities that appear in the caption, which can be specified instead of parse_mode
//            disable_notification	Boolean	Optional	Sends the message silently. Users will receive a notification with no sound.
//            protect_content	Boolean	Optional	Protects the contents of the sent message from forwarding and saving
//            reply_to_message_id	Integer	Optional	If the message is a reply, ID of the original message
//            allow_sending_without_reply	Boolean	Optional	Pass True if the message should be sent even if the specified replied-to message is not found
//            reply_markup

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void sendAudio(File audioFile) throws IOException, InterruptedException {
//        Parameter	Type	Required	Description
//        chat_id	Integer or String	Yes	Unique identifier for the target chat or username of the target channel (in the format @channelusername)
//        message_thread_id	Integer	Optional	Unique identifier for the target message thread (topic) of the forum; for forum supergroups only
//        audio	InputFile or String	Yes	Audio file to send. Pass a file_id as String to send an audio file that exists on the Telegram servers (recommended), pass an HTTP URL as a String for Telegram to get an audio file from the Internet, or upload a new one using multipart/form-data. More information on Sending Files »
//        caption	String	Optional	Audio caption, 0-1024 characters after entities parsing
//        parse_mode	String	Optional	Mode for parsing entities in the audio caption. See formatting options for more details.
//        caption_entities	Array of MessageEntity	Optional	A JSON-serialized list of special entities that appear in the caption, which can be specified instead of parse_mode
//        duration	Integer	Optional	Duration of the audio in seconds
//        performer	String	Optional	Performer
//        title	String	Optional	Track name
//        thumbnail	InputFile or String	Optional	Thumbnail of the file sent; can be ignored if thumbnail generation for the file is supported server-side. The thumbnail should be in JPEG format and less than 200 kB in size. A thumbnail's width and height should not exceed 320. Ignored if the file is not uploaded using multipart/form-data. Thumbnails can't be reused and can be only uploaded as a new file, so you can pass “attach://<file_attach_name>” if the thumbnail was uploaded using multipart/form-data under <file_attach_name>. More information on Sending Files »
//        disable_notification	Boolean	Optional	Sends the message silently. Users will receive a notification with no sound.
//        protect_content	Boolean	Optional	Protects the contents of the sent message from forwarding and saving
//        reply_to_message_id	Integer	Optional	If the message is a reply, ID of the original message
//        allow_sending_without_reply	Boolean	Optional	Pass True if the message should be sent even if the specified replied-to message is not found
//        reply_markup	InlineKeyboardMarkup or ReplyKeyboardMarkup or ReplyKeyboardRemove or ForceReply
        String url = "https://api.telegram.org/bot" + getToken() + "/sendAudio";
        sendChatAction(ENUM_CHAT_ACTION.upload_audio);
        requestBuilder.uri(URI.create(url));
        int duration = 0;
        String performer = "none";
        String title = "audio";
        String thumbnail = "";
        requestBuilder.POST(
                HttpRequest.BodyPublishers.ofString(
                        "{\"chat_id\":\"" + chatId + "\"," +
                                "\"audio\":\"" + audioFile.getAbsolutePath() + "\"," +
                                "\"caption\":\"" + caption + "\"," +
                                "\"parse_mode\":\"" + parse_mode + "\"," +
                                "\"duration\":\"" + duration + "\"," +
                                "\"performer\":\"" + performer + "\"," +
                                "\"title\":\"" + title + "\"," +
                                "\"thumbnail\":\"" + thumbnail + "\"," +
                                "\"disable_notification\":\"" + disable_notification + "\"," +
                                "\"reply_to_message_id\":\"" + reply_to_message_id + "\"," +
                                "\"allow_sending_without_reply\":\"" + allow_sending_without_reply + "\"," +
                                "\"reply_markup\":\"" + reply_markup + "}"
                )
        );
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            out.println("Audio file sent");
        } else {
            out.println("Audio file not sent");
            out.println(response.body());
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText(response.body());
            alert.showAndWait();
        }

    }

    public void sendDocument(@NotNull File document) throws IOException, InterruptedException {
        sendChatAction(ENUM_CHAT_ACTION.upload_document);
//       chat_id	Integer or String	Yes	Unique identifier for the target chat or username of the target channel (in the format @channelusername)
//       message_thread_id	Integer	Optional	Unique identifier for the target message thread (topic) of the forum; for forum supergroups only
//       document	InputFile or String	Yes	File to send. Pass a file_id as String to send a file that exists on the Telegram servers (recommended), pass an HTTP URL as a String for Telegram to get a file from the Internet, or upload a new one using multipart/form-data. More information on Sending Files »
//       thumb	InputFile or String	Optional	Thumbnail of the file sent; can be ignored if thumbnail generation for the file is supported server-side. The thumbnail should be in JPEG format and less than 200 kB in size. A thumbnail's width and height should not exceed 320. Ignored if the file is not uploaded using multipart/form-data. Thumbnails can't be reused and can be only uploaded as a new file, so you can pass “attach://<file_attach_name>” if the thumbnail was uploaded using multipart/form-data under <file_attach_name>. More information on Sending Files »
//       caption	String	Optional	Document caption (may also be used when resending documents by file_id), 0-1024 characters after entities parsing
//       parse_mode	String	Optional	Mode for parsing entities in the document caption. See formatting options for more details.
//       caption_entities	Array of MessageEntity	Optional	A JSON-serialized list of special entities that appear in the caption, which can be specified instead of parse_mode
//       disable_content_type_detection	Boolean	Optional	Disables automatic server-side content type detection for files uploaded using multipart/form-data
//       disable_notification	Boolean	Optional	Sends the message silently. Users will receive a notification with no sound.
//       protect_content	Boolean	Optional	Protects the contents of sent message from forwarding and saving
//       reply_to_message_id	Integer	Optional	If the message is a reply, ID of the original message
//       allow_sending_without_reply	Boolean	Optional	Pass True if the message should be sent even if the specified replied-to message is not found
//       reply_markup	InlineKeyboardMarkup or ReplyKeyboardMarkup or ReplyKeyboardRemove or ForceReply	Optional	Additional interface options. A JSON-serialized object for an inline keyboard, custom reply keyboard, instructions to remove reply keyboard or to force a reply from the user.
        setMethod("POST");
        String url = "https://api.telegram.org/bot" + getToken() +
                "/sendDocument";
        requestBuilder.uri(URI.create(url));
        requestBuilder.POST(HttpRequest.BodyPublishers.ofString(
                "{\"chat_id\":\"" + chatId + "\"," +
                        "\"document\":\"" + document.getAbsolutePath() + "\"," +
                        "\"thumb\":\"" + thumb.getAbsolutePath() + "\"," +
                        "\"caption\":\"" + caption + "\"," +
                        "\"parse_mode\":\"" + parse_mode + "\"," +
                        "\"caption_entities\":\"" + caption_entities + "\"," +
                        "\"disable_content_type_detection\":\"" + disable_content_type_detection + "\"," +
                        "\"disable_notification\":\"" + disable_notification + "\"," +
                        "\"reply_to_message_id\":\"" + reply_to_message_id + "\"," +
                        "\"allow_sending_without_reply\":\"" + allow_sending_without_reply + "\"," +
                        "\"reply_markup\":\"" + reply_markup + "\"}"
        ));

        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200 || response.statusCode() != 201) {
            out.println("Document not sent");
            out.println(response.body());
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText(response.body());
            alert.showAndWait();
        } else {
            out.println("Document sent");
        }


    }

    @Contract(pure = true)
    private @NotNull String StringConcatenate(String msg1, String s1, String nl1) {
        return msg1 + s1 + nl1;
    }

    void sendVideo(@NotNull File file, String reply_markup) throws IOException {
        sendChatAction(ENUM_CHAT_ACTION.record_video);
//
//              Parameter	Type	Required	Description
//              chat_id	Integer or String	Yes	Unique identifier for the target chat or username of the target channel (in the format @channelusername)
//              message_thread_id	Integer	Optional	Unique identifier for the target message thread (topic) of the forum; for forum supergroups only
//              video	InputFile or String	Yes	Video to send. Pass a file_id as String to send a video that exists on the Telegram servers (recommended), pass an HTTP URL as a String for Telegram to get a video from the Internet, or upload a new video using multipart/form-data. More information on Sending Files »
//              duration	Integer	Optional	Duration of sent video in seconds
//              width	Integer	Optional	Video width
//              height	Integer	Optional	Video height
//              thumb	InputFile or String	Optional	Thumbnail of the file sent; can be ignored if thumbnail generation for the file is supported server-side. The thumbnail should be in JPEG format and less than 200 kB in size. A thumbnail's width and height should not exceed 320. Ignored if the file is not uploaded using multipart/form-data. Thumbnails can't be reused and can be only uploaded as a new file, so you can pass “attach://<file_attach_name>” if the thumbnail was uploaded using multipart/form-data under <file_attach_name>. More information on Sending Files »
//              caption	String	Optional	Video caption (may also be used when resending videos by file_id), 0-1024 characters after entities parsing
//              parse_mode	String	Optional	Mode for parsing entities in the video caption. See formatting options for more details.
//              caption_entities	Array of MessageEntity	Optional	A JSON-serialized list of special entities that appear in the caption, which can be specified instead of parse_mode
//              supports_streaming	Boolean	Optional	Pass True if the uploaded video is suitable for streaming
//              disable_notification	Boolean	Optional	Sends the message silently. Users will receive a notification with no sound.
//              protect_content	Boolean	Optional	Protects the contents of the sent message from forwarding and saving
//              reply_to_message_id	Integer	Optional	If the message is a reply, ID of the original message
//              allow_sending_without_reply	Boolean	Optional	Pass True if the message should be sent even if the specified replied-to message is not found
//              reply_markup	InlineKeyboardMarkup or ReplyKeyboardMarkup or ReplyKeyboardRemove or ForceReply
        setMethod("POST");
        makeRequest("https://api.telegram.org/" + file + "/bot" + getToken() + "/sendVideo" + "?chat_id=" + chat_id + "&message_thread_id=" + message_thread_id + "&video=" + file.toURI() + "&caption=" + caption + "&parse_mode=" + parse_mode + "&caption_entities=" + caption_entities + "&disable_content_type_detection=" + disable_content_type_detection + "&disable_notification=" + disable_notification + "&protect_content=" + protect_content + "&reply_to_message_id=" + reply_to_message_id + "&allow_sending_without_reply=" + allow_sending_without_reply + "&reply_markup=" + reply_markup, "POST");

    }

    void sendAnimation(Animation animation) throws IOException {
        sendChatAction(ENUM_CHAT_ACTION.typing);
        setMethod("POST");
        makeRequest("https://api.telegram.org/" + file + "/bot" + getToken() + "/sendAnimation", "POST");
        setMethod("POST");

    }

    public void sendVoice(File file, String reply_markup) throws IOException, InterruptedException {
//       Parameter	Type	Required	Description
//       chat_id	Integer or String	Yes	Unique identifier for the target chat or username of the target channel (in the format @channelusername)
//       message_thread_id	Integer	Optional	Unique identifier for the target message thread (topic) of the forum; for forum supergroups only
//       voice	InputFile or String	Yes	Audio file to send. Pass a file_id as String to send a file that exists on the Telegram servers (recommended), pass an HTTP URL as a String for Telegram to get a file from the Internet, or upload a new one using multipart/form-data. More information on Sending Files »
//       caption	String	Optional	Voice message caption, 0-1024 characters after entities parsing
//       parse_mode	String	Optional	Mode for parsing entities in the voice message caption. See formatting options for more details.
//       caption_entities	Array of MessageEntity	Optional	A JSON-serialized list of special entities that appear in the caption, which can be specified instead of parse_mode
//       duration	Integer	Optional	Duration of the voice message in seconds
//       disable_notification	Boolean	Optional	Sends the message silently. Users will receive a notification with no sound.
//       protect_content	Boolean	Optional	Protects the contents of the sent message from forwarding and saving
//       reply_to_message_id	Integer	Optional	If the message is a reply, ID of the original message
//       allow_sending_without_reply	Boolean	Optional	Pass True if the message should be sent even if the specified replied-to message is not found
//       reply_markup	InlineKeyboardMarkup or ReplyKeyboardMarkup or ReplyKeyboardRemove or ForceReply
        sendChatAction(ENUM_CHAT_ACTION.record_voice);
        requestBuilder.uri(URI.create("https://api.telegram.org/bot" + getToken() +
                "/sendVoice"));
        requestBuilder.POST(HttpRequest.BodyPublishers.ofString(
                "chat_id=" + chat_id + "&message_thread_id=" + message_thread_id + "&voice=" + file.toURI() + "&caption=" + caption + "&parse_mode=" + parse_mode + "&caption_entities=" + caption_entities + "&duration=" + duration + "&disable_content_type_detection=" + disable_content_type_detection + "&disable_notification=" + disable_notification + "&protect_content=" + protect_content + "&reply_to_message_id=" + reply_to_message_id + "&allow_sending_without_reply=" + allow_sending_without_reply + "&reply_markup=" + reply_markup, StandardCharsets.UTF_8
        ));
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        out.println(response.statusCode());
        out.println(response.body());
        if (response.statusCode() != 200 || response.statusCode() != 201) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Failed to send voice");
            alert.setContentText(response.body());
            alert.showAndWait();
        } else {
            logger.info("Voice sent");
        }
    }

    void sendVideoNote(File file) throws IOException {
        sendChatAction(ENUM_CHAT_ACTION.record_video_note);
        setMethod("POST");
        makeRequest("https://api.telegram.org/" + file + "/bot" + getToken() + "/sendVideoNote", "POST");


    }

    String InlineKeyboardMarkup(String inlineKeyboardButton) {//inline_keyboard	Array of Array of InlineKeyboardButton


        return inlineKeyboardButton;


    }

    void sendMediaGroup(File file) throws IOException {
        sendChatAction(ENUM_CHAT_ACTION.record_video_note);
        setMethod("POST");
        makeRequest("https://api.telegram.org/" + file + "/bot/" + getToken() + "/" + "sendMediaGroup", "POST");
    }

    void sendLocation(String longitude, String latitude, String live_period, String heading, String proximity_alert_radius, String reply_markup) throws IOException {
        sendChatAction(ENUM_CHAT_ACTION.typing);
//      sendLocation
//      Use this method to send point on the map. On success, sent Message is returned.
//
//      Parameter	Type	Required	Description
//      chat_id	Integer or String	Yes	Unique identifier for the target chat or username of the target channel (in the format @channelusername)
//      message_thread_id	Integer	Optional	Unique identifier for the target message thread (topic) of the forum; for forum supergroups only
//      latitude	Float number	Yes	Latitude of the location
//      longitude	Float number	Yes	Longitude of the location
//      horizontal_accuracy	Float number	Optional	The radius of uncertainty for the location, measured in meters; 0-1500
//      live_period	Integer	Optional	Period in seconds for which the location will be updated (see Live Locations, should be between 60 and 86400.
//      heading	Integer	Optional	For live locations, a direction in which the user is moving, in degrees. Must be between 1 and 360 if specified.
//              proximity_alert_radius	Integer	Optional	For live locations, a maximum distance for proximity alerts about approaching another chat member, in meters. Must be between 1 and 100000 if specified.
//              disable_notification	Boolean	Optional	Sends the message silently. Users will receive a notification with no sound.
//      protect_content	Boolean	Optional	Protects the contents of the sent message from forwarding and saving
//      reply_to_message_id	Integer	Optional	If the message is a reply, ID of the original message
//      allow_sending_without_reply	Boolean	Optional	Pass True if the message should be sent even if the specified replied-to message is not found
//      reply_markup	InlineKeyboardMarkup or ReplyKeyboardMarkup or ReplyKeyboardRemove or ForceReply	Optional	Additional interface options. A JSON-serialized object for an inline keyboard, custom reply keyboard, instructions to remove reply keyboard or to force a reply from the user.
//        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/sendLocation" + "?chat_id=" + chat_id + "&message_thread_id=" + message_thread_id + "&latitude=" + latitude + "&longitude=" + longitude + "&live_period=" + live_period + "&heading=" + heading + "&proximity_alert_radius=" + proximity_alert_radius + "&disable_notification=" + disable_notification + "&protect_content=" + protect_content + "&reply_to_message_id=" + reply_to_message_id + "&allow_sending_without_reply=" + allow_sending_without_reply + "&reply_markup=" + reply_markup, "POST");


    }

    void editMessageLiveLocation() throws IOException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot/" + getToken() + "/editMessageLiveLocation", "POST");
    }

    public void stopMessageLiveLocation() throws IOException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "stopMessageLiveLocation", "POST");
    }

    public static void sendVenue() throws IOException {

        String url = "https://api.telegram.org/bot/" + getToken() + "/sendVenue" + "?chat_id=" + chat.getChat_id();
//
//        Parameter	Type	Required	Description
//        chat_id	Integer or String	Yes	Unique identifier for the target chat or username of the target channel (in the format @channelusername)
//        message_thread_id	Integer	Optional	Unique identifier for the target message thread (topic) of the forum; for forum supergroups only
//        latitude	Float number	Yes	Latitude of the venue
//        longitude	Float number	Yes	Longitude of the venue
//        title	String	Yes	Name of the venue
//        address	String	Yes	Address of the venue
//        foursquare_id	String	Optional	Foursquare identifier of the venue
//        foursquare_type	String	Optional	Foursquare type of the venue, if known. (For example, “arts_entertainment/default”, “arts_entertainment/aquarium” or “food/icecream”.)
//        google_place_id	String	Optional	Google Places identifier of the venue
//        google_place_type	String	Optional	Google Places type of the venue. (See supported types.)
//        disable_notification	Boolean	Optional	Sends the message silently. Users will receive a notification with no sound.
//        protect_content	Boolean	Optional	Protects the contents of the sent message from forwarding and saving
//        reply_to_message_id	Integer	Optional	If the message is a reply, ID of the original message
//        allow_sending_without_reply	Boolean	Optional	Pass True if the message should be sent even if the specified replied-to message is not found
//        reply_markup	InlineKeyboardMarkup or ReplyKeyboardMarkup or ReplyKeyboardRemove or ForceReply	Optional	Additional interface options. A JSON-serialized object for an inline keyboard, custom reply keyboard, instructions to remove reply keyboard or to force a reply from the user.
//


        requestBuilder.uri(URI.create(url));
        requestBuilder.POST(
                HttpRequest.BodyPublishers.ofString(
                        "{\"chat_id\":\"" + chat.getChat_id() + "\",\"latitude\":\"" + latitude + "\",\"longitude\":\"" + longitude + "\",\"title\":\"" + title + "\",\"address\":\"" + address + "\",\"foursquare_id\":\"" + foursquare_id + "\",\"foursquare_type\":\"" + foursquare_type + "\",\"google_place_id\":\"" + google_place_id + "\",\"google_place_type\":\"" + google_place_type + "\",\"disable_notification\":\"" + disable_notification + "\",\"protect_content\":\"" + protect_content + "\",\"reply_to_message_id\":\"" + reply_to_message_id + "\",\"allow_sending_without_reply\":\"" + allow_sending_without_reply + "\",\"reply_markup\":\"" + reply_markup +
                                "}")


        );

    }

    void sendContact(String chat_id, String phone_number, String first_name, String last_name, String vcard, String reply_markup) throws IOException {
        sendChatAction(ENUM_CHAT_ACTION.typing);
//        Parameter	Type	Required	Description
//        chat_id	Integer or String	Yes	Unique identifier for the target chat or username of the target channel (in the format @channelusername)
//        message_thread_id	Integer	Optional	Unique identifier for the target message thread (topic) of the forum; for forum supergroups only
//        phone_number	String	Yes	Contact's phone number
//        first_name	String	Yes	Contact's first name
//        last_name	String	Optional	Contact's last name
//        vcard	String	Optional	Additional data about the contact in the form of a vCard, 0-2048 bytes
//        disable_notification	Boolean	Optional	Sends the message silently. Users will receive a notification with no sound.
//        protect_content	Boolean	Optional	Protects the contents of the sent message from forwarding and saving
//        reply_to_message_id	Integer	Optional	If the message is a reply, ID of the original message
//        allow_sending_without_reply	Boolean	Optional	Pass True if the message should be sent even if the specified replied-to message is not found
//        reply_markup	InlineKeyboardMarkup or ReplyKeyboardMarkup or ReplyKeyboardRemove or ForceReply	Optional	Additional interface options. A JSON-serialized object for an inline keyboard, custom reply keyboard, instructions to remove reply keyboard or to force a reply from the user.
//


        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/sendContact" + "?chat_id=" + getChatId() + "&message_thread_id=" + message_thread_id + "&phone_number=" + phone_number + "&first_name=" + first_name + "&last_name=" + last_name + "&vcard=" + vcard + "&disable_notification=" + disable_notification + "&protect_content=" + protect_content + "&reply_to_message_id=" + reply_to_message_id + "&allow_sending_without_reply=" + allow_sending_without_reply + "&reply_markup=" + reply_markup, "POST");

    }

    void sendPoll() throws IOException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/sendPoll", "POST");
    }

    void sendDice() throws IOException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot/" + getToken() + "/sendDice", "POST");

    }

    static void sendChatAction(ENUM_CHAT_ACTION action) throws IOException {
        // Type of action to broadcast. Choose one, depending on what the user is about to receive: typing for text messages, upload_photo for photos, record_video or upload_video for videos, record_voice or upload_voice for voice notes, upload_document for general files, choose_sticker for stickers, find_location for location data, record_video_note or upload_video_note for video notes.

        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/sendChatAction" + "?chat_id=" + getChatId() + "&action=" + action, "POST");
    }

    public void getUserProfilePhotos() throws IOException {
        setMethod("GET");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/getUserProfilePhotos", "POST");
    }

    public void getFile() throws IOException {
        setMethod("GET");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/getFile", "POST");
    }

    public void banChatMember() throws IOException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/banChatMember", "POST");
    }

    public void unbanChatMember() throws IOException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/unbanChatMember", "POST");
    }

    public void restrictChatMember() throws IOException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/restrictChatMember", "POST");
    }

    void promoteChatMember() throws IOException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/promoteChatMember", "POST");
    }

    void setChatAdministratorCustomTitle() throws IOException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/setChatAdministratorCustomTitle", "POST");
    }

    public void banChatSenderChat() throws IOException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/" + "/banChatSenderChat", "POST");
    }

    void unbanChatSenderChat() throws IOException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/" + "/unbanChatSenderChat", "POST");
    }

    void setChatPermissions() throws IOException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/setChatPermissions", "POST");


    }

    void exportChatInviteLink() throws IOException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/exportChatInviteLink", "POST");
    }

    void createChatInviteLink() throws IOException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/createChatInviteLink", "POST");
    }

    void editChatInviteLink() throws IOException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/editChatInviteLink", "POST");
    }

    void revokeChatInviteLink() throws IOException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/editChatInviteLink", "POST");
    }

    void approveChatJoinRequest() throws IOException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/approveChatJoinRequest", "POST");
    }

    void declineChatJoinRequest() throws IOException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/approveChatJoinRequest", "POST");
    }

    void setChatPhoto() throws IOException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/setChatPhoto", "POST");

    }

    void deleteChatPhoto() throws IOException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/deleteChatPhoto", "POST");
    }

    void setChatTitle() throws IOException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/setChatTitle", "POST");
    }

    void editChatTitle() throws IOException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/editChatTitle", "POST");
    }

    void setChatDescription() throws IOException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/setChatDescription", "POST");
    }

    void pinChatMessage() throws IOException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/pinChatMessage", "POST");
    }

    void unpinChatMessage() throws IOException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/unpinChatMessage", "POST");
    }

    void setChatPhoto(String file) throws IOException, InterruptedException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/" + file + "bot" + getToken() + "/setChatPhoto", "POST");
    }

    void unpinAllChatMessages() throws IOException, InterruptedException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/unpinAllChatMessages", "POST");
    }

    void editMyCommands() throws IOException, InterruptedException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/editMyCommands", "POST");
    }

    void getMyCommands() throws IOException, InterruptedException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/getMyCommands", "POST");
    }

    void answerCallbackQuery() throws IOException, InterruptedException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/answerCallbackQuery", "POST");
    }

    void leaveChat() throws IOException, InterruptedException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/leaveChat", "POST");
    }

    public String getUpdates() throws IOException {

        String url = "https://api.telegram.org/bot" +token + "/getUpdates" + "?&offset=" + offset +//\tInteger\tOptional\tIdentifier of the first update to be returned. Must be greater by one than the highest among the identifiers of previously received updates. By default, updates starting with the earliest unconfirmed update are returned. An update is considered confirmed as soon as getUpdates is called with an offset higher than its update_id. The negative offset can be specified to retrieve updates starting from -offset update from the end of the updates queue. All previous updates will forgotten.\n" +
                "&limit=" + 1 +//\tInteger\tOptional\tLimits the number of updates to be retrieved. Values between 1-100 are accepted. Defaults to 100.\n" +
                "&timeout=" + 0 +//\tInteger\tOptional\tTimeout in seconds for long polling. Defaults to 0, i.e. usual short polling. Should be positive, short polling should be used for testing purposes only.\n" +
                "&allowed_updates=" + true;//\tBoolean\tOptional;
        JSONArray jsonResponse1 = makeRequest(url, "GET").getJSONArray("result");
        for (int i = 0; i < jsonResponse1.length(); i++) {
            JSONObject jsonResponse = jsonResponse1.getJSONObject(i);

            out.println("TELEGRAM update  " + jsonResponse);
            if (jsonResponse.has("update_id")) {
                update_id = String.valueOf(jsonResponse.getLong("update_id"));
                out.println(jsonResponse1);
            }
            if (jsonResponse.has("result")) {
                result = jsonResponse.getString("result");
                ok = result.equals("ok");

                out.println("result " + jsonResponse);
            }


            if (jsonResponse.has("result")) {
                JSONArray results = jsonResponse.getJSONArray("result");
                for (i = 0; i < results.length(); i++) {
                    JSONObject result = results.getJSONObject(i);
                    if (result.has("ok")) {
                        ok = result.getBoolean("ok");
                    }
                    if (result.has("error")) {
                        String error = result.getString("error");
                        out.println("ERROR :" + error);
                    }
                    if (result.has("update_id")) {
                        update_id = String.valueOf(result.getInt("update_id"));
                    }

                    if (result.has("entities")) {
                        JSONArray entities = result.getJSONArray("entities");
                        for (int j = 0; j < entities.length(); j++) {
                            JSONObject entity = entities.getJSONObject(j);
                            if (entity.has("offset")) {
                                offset = entity.getInt("offset");
                            }
                            if (entity.has("length")) {
                                length = entity.getInt("length");
                            }
                            if (entity.has("type")) {
                                entity_type = entity.getString("type");
                            }
                        }
                    }
                    if (result.has("message")) {

                        JSONObject message = result.getJSONObject("message");
                        if (message.has("text")) {
                            text = message.getString("text");
                            if (message.has("parse_mode")) {
                                parse_mode = message.getString("parse_mode");
                            }
                            lastMessage = text;
                        }
                        if (message.has("date")) {
                            date = String.valueOf(message.getLong("date"));
                        }
                        if (message.has("chat")) {
                            JSONObject chat = message.getJSONObject("chat");
                            if (chat.has("id")) {
                                int chat_id = Integer.parseInt(String.valueOf(chat.getInt("id")));


                                if (chat.has("type")) {
                                    chat_type = chat.getString("type");
                                }
                                if (chat.has("title")) {
                                    chat_title = chat.getString("title");
                                }
                                if (chat.has("username")) {
                                    chat_username = chat.getString("username");
                                }
                                if (chat.has("first_name")) {
                                    chat_first_name = chat.getString("first_name");
                                }
                                if (chat.has("last_name")) {
                                    chat_last_name = chat.getString("last_name");
                                }
                                if (chat.has("photo")) {
                                    JSONObject photo = chat.getJSONObject("photo");
                                    if (photo.has("file_id")) {
                                        chat_photo_file_id = String.valueOf(photo.getInt("file_id"));
                                    }
                                    if (photo.has("file_unique_id")) {
                                        chat_photo_file_unique_id = String.valueOf(photo.getInt("file_unique_id"));
                                    }
                                }
                                if (chat.has("video")) {
                                    JSONObject video = chat.getJSONObject("video");
                                    if (video.has("file_id")) {
                                        chat_video_file_id = new File(String.valueOf(video.getInt("file_id")));
                                        //Saving file to disk
                                        if (chat_video_file_id.exists()) {
                                            // chat_video_file_id.deleteOnExit();
                                            continue;
                                        } else {
                                            FileWriter writer = new FileWriter(chat_video_file_id);
                                            BufferedWriter bufferedWriter = new BufferedWriter(writer);
                                            bufferedWriter.write(video.getString("file_path"));
                                            bufferedWriter.close();
                                            chat_video_file_id.deleteOnExit();
                                        }
                                    }
                                }
                                if (chat.has("voice")) {
                                    JSONObject voice1 = chat.getJSONObject("voice");
                                    if (voice1.has("file_id")) {
                                        chat_voice_file_id = new File(String.valueOf(voice1.getInt("file_id")));
                                        //Saving file to disk
                                        if (chat_voice_file_id.exists()) {
                                            // chat_voice_file_id.deleteOnExit();
                                            continue;
                                        } else {
                                            FileWriter writer = new FileWriter(chat_voice_file_id);
                                            BufferedWriter bufferedWriter = new BufferedWriter(writer);
                                            bufferedWriter.write(voice1.getString("file_path"));
                                            bufferedWriter.close();
                                            chat_voice_file_id.deleteOnExit();
                                        }
                                    }
                                }
                                if (chat.has("caption")) {
                                    JSONObject caption = chat.getJSONObject("caption");
                                    if (caption.has("file_id")) {
                                        chat_caption_file_id = new File(String.valueOf(caption.getInt("file_id")));
                                        //Saving file to disk
                                        if (chat_caption_file_id.exists()) {
                                            // chat_caption_file_id.deleteOnExit();
                                            continue;
                                        } else {
                                            FileWriter writer = new FileWriter(chat_caption_file_id);
                                            BufferedWriter bufferedWriter = new BufferedWriter(writer);
                                            bufferedWriter.write(caption.getString("file_path"));
                                            bufferedWriter.close();
                                            chat_caption_file_id.deleteOnExit();
                                        }
                                    }
                                }
                                if (chat.has("new_chat_members")) {
                                    JSONArray new_chat_members = chat.getJSONArray("new_chat_members");
                                }
                                if (chat.has("left_chat_member")) {
                                    String left_chat_member = String.valueOf(chat.getJSONObject("left_chat_member"));
                                }
                                if (chat.has("text")) {
                                    text = String.valueOf(chat.getJSONObject("text"));
                                }
                                if (chat.has("message")) {
                                    JSONObject chatMessage = chat.getJSONObject("message");
                                }
                                ArrayListChat.add(i, new Chat(chat_id, chat_type, chat_title, text, chat_first_name, chat_last_name, chat_username));

                            }
                        }
                        if (message.has("from")) {
                            JSONObject from = message.getJSONObject("from");
                            if (from.has("id")) {
                                from_id = String.valueOf(from.getInt("id"));
                            }
                            if (from.has("is_bot")) {
                                is_bot = from.getBoolean("is_bot");
                            }
                            if (from.has("first_name")) {
                                from_first_name = from.getString("first_name");
                            }
                            if (from.has("last_name")) {
                                last_name = from.getString("last_name");
                            }
                            if (from.has("username")) {
                                username = from.getString("username");
                            }
                            if (from.has("photo")) {
                                JSONObject photo = from.getJSONObject("photo");
                                if (photo.has("file_id")) {
                                    String from_photo_file_id = String.valueOf(photo.getInt("file_id"));
                                }
                                if (photo.has("file_unique_id")) {
                                    String from_photo_file_unique_id = String.valueOf(photo.getInt("file_unique_id"));
                                    out.println(from_photo_file_unique_id);
                                }
                            }
                        }
                        if (message.has("chat_id")) {
                            JSONObject chat_id = message.getJSONObject("chat_id");
                            if (chat_id.has("id")) {
                                setChat_id(String.valueOf(chat_id.getInt("id")));

                            }
                        }
                        if (message.has("reply_to_message")) {
                            JSONObject reply_to_message = message.getJSONObject("reply_to_message");
                            if (reply_to_message.has("message_id")) {
                                reply_to_message_id = String.valueOf(reply_to_message.getInt("message_id"));
                            }
                        }
                        if (message.has("reply_markup")) {
                            JSONObject reply_markup = message.getJSONObject("reply_markup");
                            if (reply_markup.has("inline_keyboard")) {
                                JSONArray inline_keyboard = reply_markup.getJSONArray("inline_keyboard");
                                for (int j = 0; j < inline_keyboard.length(); j++) {
                                    JSONObject inline_keyboard_item = inline_keyboard.getJSONObject(j);
                                    if (inline_keyboard_item.has("text")) {
                                        inline_keyboard_text = inline_keyboard_item.getString("text");
                                    }
                                }
                            }
                        }
                        if (message.has("input_message_content")) {
                            JSONObject input_message_content = message.getJSONObject("input_message_content");
                            if (input_message_content.has("message_text")) {
                                String input_message_content_text = input_message_content.getString("message_text");
                            }
                        }
                        if (message.has("input_message_entities")) {
                            JSONObject input_message_entities = message.getJSONObject("input_message_entities");
                        }
                        if (message.has("inline_query")) {
                            JSONObject inline_query = message.getJSONObject("inline_query");
                            if (inline_query.has("id")) {
                                String inline_query_id = String.valueOf(inline_query.getInt("id"));
                            }
                        }
                        if (message.has("chosen_inline_result")) {

                            JSONObject chosen_inline_result = message.getJSONObject("chosen_inline_result");
                            if (chosen_inline_result.has("result_text")) {
                                String chosen_inline_result_text = chosen_inline_result.getString("result_text");

                            }
                        }
                        if (message.has("callback_query")) {
                            JSONObject callback_query = message.getJSONObject("callback_query");
                            if (callback_query.has("id")) {
                                String callback_query_id = String.valueOf(callback_query.getInt("id"));
                            }
                        }
                        if (message.has("shipping_query")) {
                            JSONObject shipping_query = message.getJSONObject("shipping_query");
                        }
                        if (message.has("pre_checkout_query")) {
                            JSONObject pre_checkout_query = message.getJSONObject("pre_checkout_query");
                            if (pre_checkout_query.has("pre_checkout_query_id")) {
                                String pre_checkout_query_id = String.valueOf(pre_checkout_query.getInt("pre_checkout_query_id"));
                            }
                        }
                        if (message.has("poll_answer_query")) {
                            JSONObject poll_answer_query = message.getJSONObject("poll_answer_query");
                            if (poll_answer_query.has("poll_answer_query_id")) {
                                String poll_answer_query_id = String.valueOf(poll_answer_query.getInt("poll_answer_query_id"));
                            }
                        }
                        if (message.has("my_chat_member")) {
                            JSONObject my_chat_member = message.getJSONObject("my_chat_member");
                            if (my_chat_member.has("user")) {
                                JSONObject user = my_chat_member.getJSONObject("user");
                                if (user.has("id")) {
                                    String user_id = String.valueOf(user.getInt("id"));
                                }
                                if (user.has("is_bot")) {
                                    is_bot = user.getBoolean("is_bot");
                                }
                                if (user.has("is_restricted")) {
                                    is_restricted = user.getBoolean("is_restricted");
                                }
                                if (user.has("date_first_contacted")) {
                                    JSONObject date_first_contacted = user.getJSONObject("date_first_contacted");
                                    if (date_first_contacted.has("date")) {
                                        String date = String.valueOf(date_first_contacted.getInt("date"));
                                        if (date.length() == 10) {
                                            String date_first_contacted_time = date;
                                            date_first_contacted_time = date_first_contacted_time.replace("T", " ");
                                        }
                                    }
                                }
                            }
                        }
                        if (message.has("chat_member")) {
                            JSONObject chat_member = message.getJSONObject("chat_member");
                            if (chat_member.has("user")) {
                                JSONObject user = chat_member.getJSONObject("user");
                                if (user.has("id")) {
                                    String user_id = String.valueOf(user.getInt("id"));
                                }
                            }
                        }
                        if (message.has("chat_join_request")) {
                            JSONObject chat_join_request = message.getJSONObject("chat_join_request");
                            if (chat_join_request.has("user")) {
                                JSONObject user = chat_join_request.getJSONObject("user");
                                if (user.has("id")) {
                                    String user_id = String.valueOf(user.getInt("id"));
                                }
                                if (user.has("is_bot")) {
                                    is_bot = user.getBoolean("is_bot");
                                }
                            }
                        }
                        if (message.has("chat_join_accept")) {
                            JSONObject chat_join_accept = message.getJSONObject("chat_join_accept");
                        }
                        if (message.has("chat_leave_request")) {
                            JSONObject chat_leave_request = message.getJSONObject("chat_leave_request");
                        }
                        if (message.has("chat_leave_accept")) {
                            JSONObject chat_leave_accept = message.getJSONObject("chat_leave_accept");
                        }
                        if (message.has("chat_member_left_channel")) {
                            JSONObject chat_member_left_channel = message.getJSONObject("chat_member_left_channel");
                        }
                        if (message.has("inline_query_result")) {
                            JSONObject inline_query_result = message.getJSONObject("inline_query_result");
                            if (inline_query_result.has("id")) {
                                String inline_query_id = String.valueOf(inline_query_result.getInt("id"));
                            }
                        }
                        if (message.has("chosen_inline_result")) {
                            JSONObject chosen_inline_result = message.getJSONObject("chosen_inline_result");
                        }
                        if (message.has("callback_query_result")) {
                            JSONObject callback_query_result = message.getJSONObject("callback_query_result");
                        }

                    }
                    if (result.has("chat_id")) {
                        chatId = String.valueOf(result.getInt("chat_id"));
                    }
                    if (result.has("edited_message")) {
                        String editedMessage = result.getString("edited_message");
                    }
                    if (result.has("inline_message_id")) {
                        int inlineMessageID = result.getInt("inline_message_id");
                    }
                    if (result.has("inline_message_text")) {
                        String inlineMessageText = result.getString("inline_message_text");
                    }
                    if (result.has("inline_message_entities")) {
                        JSONArray inlineMessageEntities = result.getJSONArray("inline_message_entities");
                        for (i = 0; i < inlineMessageEntities.length(); i++) {
                            JSONObject entity = inlineMessageEntities.getJSONObject(i);
                            if (entity.has("type")) {
                                type = entity.getString("type");
                            }
                            if (entity.has("offset")) {
                                offset = entity.getInt("offset");
                            }
                            if (entity.has("length")) {
                                length = entity.getInt("length");
                            }
                        }
                    }
                    if (result.has("chosen_inline_message_entity")) {
                        int chosenInlineMessageEntity = result.getInt("chosen_inline_message_entity");
                    }
                    if (result.has("chosen_inline_message_offset")) {
                        int chosenInlineMessageOffset = result.getInt("chosen_inline_message_offset");
                    }
                    if (result.has("chosen_inline_message_length")) {
                        int chosenInlineMessageLength = result.getInt("chosen_inline_message_length");
                    }
                    if (result.has("chosen_inline_message_entities")) {
                        JSONArray chosenInlineMessageEntities = result.getJSONArray("chosen_inline_message_entities");
                        for (i = 0; i < chosenInlineMessageEntities.length(); i++) {
                            JSONObject entity = chosenInlineMessageEntities.getJSONObject(i);
                            if (entity.has("type")) {
                                type = entity.getString("type");
                            }
                            if (entity.has("offset")) {
                                offset = entity.getInt("offset");
                            }
                            if (entity.has("length")) {
                                length = entity.getInt("length");
                            }
                        }
                    }
                    if (result.has("chosen_inline_message_entity_offset")) {
                        int chosenInlineMessageEntityOffset = result.getInt("chosen_inline_message_entity_offset");
                    }
                    if (result.has("chosen_inline_message_entity_length")) {
                        int chosenInlineMessageEntityLength = result.getInt("chosen_inline_message_entity_length");
                    }
                    if (result.has("sender_chat")) {
                        senderChat.setType(result.getString("type"));
                        senderChat.setId(result.getString("id"));
                        senderChat.setName(result.getString("first_name"));
                        senderChat.setUsername(result.getString("username"));
                        senderChat.setPhoto(result.getString("photo"));
                    }
                    if (result.has("chat")) {

                        JSONObject chat = result.getJSONObject("chat");
                        if (chat.has("id")) {
                            chatId = chat.getString("id");
                        }
                        if (chat.has("type")) {
                            String chatType = chat.getString("type");
                            if (chatType.equals("private")) {
                                boolean isPrivate = true;
                                if (chat.has("title")) {

                                    if (chat.has("username")) {
                                        username = chat.getString("username");
                                    }
                                    if (chat.has("first_name")) {
                                        first_name = chat.getString("first_name");
                                    }
                                    if (chat.has("last_name")) {
                                        last_name = chat.getString("last_name");
                                    }
                                }
                            }

                        }
                        if (chat.has("title")) {
                            String chatTitle = chat.getString("title");
                            chatTitle = chatTitle.substring(0, Math.min(chatTitle.length(), 50));
                        }
                        if (chat.has("username")) {
                            String chatUsername = chat.getString("username");

                            if (chatUsername.contains("@")) {
                                chatUsername = chatUsername.substring(0, chatUsername.indexOf("@"));

                                out.println(chatUsername);

                            }
                        }
                        if (chat.has("first_name")) {
                            String chatFirstName = chat.getString("first_name");
                            chatFirstName = chatFirstName.substring(0, Math.min(chatFirstName.length(), 50));

                            out.println(chatFirstName);
                        }
                        if (chat.has("last_name")) {
                            String chatLastName = chat.getString("last_name");
                            out.println(chatLastName);
                        }
                        if (chat.has("photo")) {
                            String chatPhoto = chat.getString("photo");

                        }
                        if (chat.has("bio")) {
                            String chatBio = chat.getString("bio");
                        }
                        if (chat.has("description")) {
                            chatDescription = chat.getString("description");
                        }
                        if (chat.has("invite_link")) {
                            String chatInviteLink = chat.getString("invite_link");

                            out.println(chatInviteLink);
                        }
                        if (chat.has("invite_link_pretext")) {
                            String chatInviteLinkPretext = chat.getString("invite_link_pretext");
                            out.println(chatInviteLinkPretext);
                        }

                    }

                    if (result.has("date")) {

                        JSONObject date = result.getJSONObject("date");
                        if (date.has("date")) {
                            String dateString = date.getString("date");
                            dateString = dateString.substring(0, Math.min(dateString.length(), 10));
                            out.println(dateString);
                            setDate(dateString);
                        }
                    }

                    if (result.has("location")) {

                        JSONObject location = result.getJSONObject("location");
                        if (location.has("location")) {
                            String locationString = location.getString("location");
                            out.println(locationString);
                            setLocation(locationString);
                        }
                    }

                    if (result.has("duration")) {

                        JSONObject duration = result.getJSONObject("duration");
                        if (duration.has("duration")) {
                            String durationString = duration.getString("duration");

                            out.println(durationString);
                        }
                    }

                    if (result.has("sender_chat")) {

                        JSONObject senderChat = result.getJSONObject("sender_chat");
                        if (senderChat.has("sender_chat")) {
                            String senderChatString = senderChat.getString("sender_chat");

                        }
                    }


                    if (jsonResponse.has("update_id")) {
                        setUpdate_id(jsonResponse.getString("update_id"));
                    }
                    if (jsonResponse.has("message")) {
                        message = jsonResponse.getString("message");
                    }
                    JSONArray jsonObject = new JSONArray(jsonResponse.getJSONArray("result"));
                    for (i = 0; i < jsonObject.length(); i++) {
                        JSONObject jsonObject1 = jsonObject.getJSONObject(i);
                        if (jsonObject1.has("update")) {
                            JSONObject jsonObject2 = jsonObject1.getJSONObject("update");
                            if (jsonObject2.has("message_id")) {
                                setMessage_id(jsonObject2.getString("message_id"));
                                if (jsonObject2.has("date")) {
                                    setDate(String.valueOf(jsonObject2.getString("date")));
                                }
                            }
                            if (jsonObject2.has("from")) {
                                username = jsonObject2.getString("from");
                                if (jsonObject2.has("date")) {
                                    date = jsonObject2.getString("date");
                                }
                            }
                        }
                        if (jsonObject1.has("text")) {
                            setText(jsonObject1.getString("text"));
                        }
                        if (jsonObject1.has("inline_message_id")) {
                            setInline_message_id(jsonObject1.getString("inline_message_id"));

                        }
                        if (jsonObject1.has("chat_id")) {
                            setChatId(String.valueOf(jsonObject1.getInt("chat_id")));
                        }
                        if (jsonObject1.has("reply_to_message_id")) {
                            reply_to_message_id = jsonObject1.getString("reply_to_message_id");
                        }
                        if (jsonObject1.has("inline_query_id")) {
                            inline_query_id = jsonObject1.getString("inline_query_id");
                        }
                        if (jsonObject1.has("from")) {
                            username = jsonObject1.getString("from");
                            if (jsonObject1.has("date")) {
                                date = jsonObject1.getString("date");
                            }
                        }
                        if (jsonObject1.has("message_id")) {
                            message_id = jsonObject1.getString("message_id");
                            if (jsonObject1.has("date")) {
                                date = jsonObject1.getString("date");
                            }
                        }
                        if (jsonObject1.has("forward_from_chat")) {
                            forward_from_chat = jsonObject1.getString("forward_from_chat");
                            if (jsonObject1.has("id")) {
                                id = String.valueOf(jsonObject1.getLong("id"));

                            }
                            if (jsonObject1.has("title")) {
                                title = jsonObject1.getString("title");
                            }
                            if (jsonObject1.has("type")) {
                                type = jsonObject1.getString("type");
                            }
                            if (jsonObject1.has("from")) {
                                username = jsonObject1.getString("from");
                                if (jsonObject1.has("date")) {
                                    date = jsonObject1.getString("date");
                                }
                            }
                            if (jsonObject1.has("forward_from_message_id")) {
                                forward_from_message_id = jsonObject1.getString("forward_from_message_id");
                            }

                        }
                        if (jsonObject1.has("chat")) {
                            chat = new Chat(String.valueOf(jsonObject1.getInt("chat_id")), String.valueOf(jsonObject1.getInt("title")), String.valueOf(jsonObject1.getInt("type")), String.valueOf(jsonObject1.getInt("username"))

                            );
                        }
                        if (jsonObject1.has("sender_chat")) {
                            senderChat = new Chat(String.valueOf(jsonObject1.getInt("sender_chat_id")), String.valueOf(jsonObject1.getInt("title")), String.valueOf(jsonObject1.getInt("type")), String.valueOf(jsonObject1.getInt("username")));

                        }
                        if (jsonObject1.has("game")) {
                            TelegramClient.game = new Game(String.valueOf(jsonObject1.getInt("game_short_name")), String.valueOf(jsonObject1.getInt("short_name")), String.valueOf(jsonObject1.getInt("game_id")), String.valueOf(jsonObject1.getInt("creator_user_id")), String.valueOf(jsonObject1.getInt("creator_user_name")),

                                    String.valueOf(jsonObject1.getInt("game_short_code")));

                        }
                    }
                }
                logger.info("Updated successfully");
                return url;
            } else {
                logger.info("Something went wrong while updating the bot" + jsonResponse);
            }
        }
        return update_id;

    }

    //
    void getChat() {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/getChat", "POST");
    }

    public String getMe() {

        JSONObject jsonResponse = makeRequest("https://api.telegram.org/bot" + getToken() + "/getMe", "GET");

        if (jsonResponse.has("ok") && jsonResponse.getBoolean("ok") && jsonResponse.has("result")) {
            JSONObject result = jsonResponse.getJSONObject("result");

            if (result.has("first_name") && result.has("last_name")) firstName = result.getString("first_name");
            if (result.has("username")) username = result.getString("username");
            if (result.has("language_code")) languageCode = result.getString("language_code");
            if (result.has("phone_number")) phoneNumber = result.getString("phone_number");
            if (result.has("photo")) photo = result.getString("photo");
            if (result.has("description")) description = result.getString("description");
            if (result.has("can_invite_users")) canInviteUsers = result.getBoolean("can_invite_users");
            if (result.has("can_change_info")) canChangeInfo = result.getBoolean("can_change_info");
            if (result.has("can_post_messages")) canPostMessages = result.getBoolean("can_post_messages");
            if (result.has("can_edit_messages")) canEditMessages = result.getBoolean("can_edit_messages");
            if (result.has("can_delete_messages")) lastName = result.getString("last_name");
            if (result.has("chat")) {
                canDeleteMessages = result.getBoolean("can_delete_messages");
            }
            if (result.has("lastName")) {
                setLastName(result.getString("last_name"));
            }
            if (result.has("is_bot")) {

                setIsBot(result.getBoolean("is_bot"));

            }
            // if (result.has("can_send_messages")) canSendMessages = result.getBoolean("can_send_messages");
            if (result.has("can_join_groups")) {
                canJoinGroups = result.getBoolean("can_join_groups");
            }
            if (result.has("can_read_all_group_messages")) {
                canReadAllGroupMessages = result.getBoolean("can_read_all_group_messages");
            }
            if (result.has("username")) {
                setUsername(username = result.getString("username"));
            }
            if (result.has("chat_id")) {
                setChatId(result.getString("chat_id"));
            }
            if (result.has("method")) {
                method = result.getString("method");
            }
            if (result.has("ok")) {
                ok = result.getBoolean("ok");
            }
            if (result.has("supports_inline_queries")) {
                supportsInlineQueries = result.getBoolean("supports_inline_queries");
            }
            out.println(jsonResponse);
        }
        return lastName;
    }

    void getChatAdministrators() throws IOException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/getChatAdministrators", "POST");
    }

    boolean newsTrade() throws IOException, ParseException, InterruptedException//RETURN TRUE IF TRADE IS ALLOWED
    {
        ArrayList<News> news = NewsManager.getNewsList();

        offset = gmtOffset();
        double CheckNews;
        if (MinAfter > 0) {

            if (new Date().getTime() - LastUpd >= Upd) {
                out.println("News Loading...");
                sendMessage(
                        """
                                News Loading...

                                Please wait until the news is updated

                                """
                );

                LastUpd = (int) new Date().getTime();


                sendMessage("News Loading ...");

            }


            for (News news1 : news) {
                //    String google_urlx = "https://www.forexfactory.com/calendar?day";


                int power = 0;
                if (Vhigh && String.valueOf(judulnews).equals(news1.getTitle())) power = 1;

                if (Vhigh && Objects.equals(news1.getImpact(), "High")) power = 1;
                if (Vmedium && Objects.equals(news1.getImpact(), "Medium")) power = 2;
                if (Vlow && Objects.equals(news1.getImpact(), "Low")) power = 3;
                if (power == 0) {

                    continue;
                }
                String ambergris;
                if (new Date().getTime() + BeforeNewsStop > news1.getDate().getTime() && new Date().getTime() - 60L * AfterNewsStop < news1.getDate().getTime() && news1.getTitle() != "") {
                    ambergris = "==>Within " + news1.getMinutes() + " minutes\n" + news;

                    CheckNews = 1;
                    String ms = message = news1.toString();//get message data with format

                    sendAlert(ambergris + " " + ms);

                } else {
                    CheckNews = 0;

                }
                if ((CheckNews == 1 && Signal) || (CheckNews == 1 && sendNews)) {

                    message = news1.toString();
                    sendMessage(message);

                    CheckNews = 0;


                }
                if (CheckNews > 0 && NewsFilter) trade = false;
                String inferiority;
                if (CheckNews > 0) {

                    if (NewsFilter) {
                        inferiority = " we are in the framework of the news\nAttention!! News Time \n!";


                        /////  We are doing here if we are in the framework of the news

                        sendAlert(inferiority);
                        if (news1.getMinutes() == AfterNewsStop - 1 && FirstAlert && sendNews) {
                            sendMessage("-->>First Alert\n " + message);


                        }
                        //--- second alert
                        if (news1.getMinutes() == BeforeNewsStop - 1 && SecondAlert && sendNews) {
                            sendMessage(">>Second Alert\n " + message);
                            SecondAlert = true;

                        }


                    }
                } else {

                    if (NewsFilter) trade = true;

                    // We are out of scope of the news release (No News)
                    sendAlert(">>News Release << " + message);
                }


            }

            return trade;
        }
        return trade;
    }

    private int gmtOffset() {
        return offset;
    }

    @Contract(pure = true)
    boolean StringFind(@NotNull String title, String dullness) {
        return title.contains(dullness);
    }

    public void getTradeNews() throws IOException, ParseException, InterruptedException {
        if (newsTrade()) {
            sendAlert("Trade is allowed No news ");
        }
    }

    public void sendAlert(String alert) {
        makeRequest("https://api.telegram.org/bot" + getToken() + "/sendMessage?chat_id=" + getChatId() + "&text=" + alert +
                "&parse_mode=" + "MarkDown" + "&disable_web_page_preview=" + false + "&disable_notification=" + false, "POST");
    }

    void getChatMember() throws IOException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/getChatMember", "POST");
    }

    void setChatStickerSet() {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/getChatStickerSet", "POST");
    }

    void deleteChatStickerSet() throws IOException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/deleteChatStickerSet", "POST");
    }
//    editForumTopic
//            closeForumTopic
//    reopenForumTopic
//            deleteForumTopic
//    unpinAllForumTopicMessages
//            answerCallbackQuery

    //            }
    void getForumTopicIconStickers() {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/getForumTopicIconStickers", "POST");
    }

    void createForumTopic() {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/createChat", "POST");
    }

    void setMyCommands() {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/setMyCommands" + "?chat_id=" + getChatId() + "&command=" + getCommands().get(0) + "&text=" + getCommands().get(1) + "&parse_mode=" + parse_mode, "POST");
    }

    //            deleteMyCommands
    void deleteMyCommands() throws IOException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/deleteMyCommands" + "?chat_id=" + getChatId() + "&command=" + getCommands() + "&text=" + getCommands().get(1) + "&parse_mode=" + parse_mode, "POST");
    }

    void setChatMenuButton() throws IOException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/setChatMenuButton", "POST");
    }

    void getChatMenuButton() throws IOException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/getChatMenuButton", "POST");
    }

    void setMyDefaultAdministratorRights() throws IOException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/setMyDefaultAdministratorRights", "POST");
    }

    void getMyDefaultAdministratorRights() throws IOException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/getMyDefaultAdministratorRights", "POST");
    }

    public String Keyboard() {
        return getCreateKeyBoard();
    }

    void getChatMemberCount() throws IOException {
        setMethod("POST");
        makeRequest("https://api.telegram.org/bot" + getToken() + "/getChatMemberCount", "POST");
    }

    void sendInvoice(String chat_id, String title, String description, String suggested_tip_amount, String max_tip_amount, String provider_data, String start_parameter, String photo_url, String photo_size, String photo_width, String photo_height, String need_name, String need_phone_number, String need_shipping_address, String is_flexible, String send_phone_number_to_provider, String send_email_to_provider, String need_email, String playload, String provider_token, String currency_code, String prices, String payload, String reply_markup) throws IOException {
        // Use this method to send invoices. On success, sent Message is returned.

        setMethod("POST");


//
//
//                    //Parameter	Type	Required	Description
//      parameters=   ("chat_id?" + chat_id+    //Integer or String	Yes	Unique identifier for the target chat or username of the target channel (in the format @channelusername)
//                    "message_thread_id" + message_thread_id+   //Integer	Optional	Unique identifier for the target message thread (topic) of the forum; for forum supergroups only
//                    "title" + title+    //String	Yes	Product name, 1-32 characters
//                    "description" + description+    //String	Yes	Product description, 1-255 characters
//                    "payload" + payload+//String	Yes	Bot-defined invoice payload, 1-128 bytes. This will not be displayed to the user, use for your internal processes.
//                    "provider_token" + provider_token+//String	Yes	Payment provider token, obtained via @BotFather
//                    "currency" + currency+    //String	Yes	Three-letter ISO 4217 currency code, see more on currencies
//                    "prices" + prices+    //Array of LabeledPrice	Yes	Price breakdown, a JSON-serialized list of components (e.g. product price, tax, discount, delivery cost, delivery tax, bonus, etc.)
//                    "max_tip_amount" + max_tip_amount+    //Integer	Optional	The maximum accepted amount for tips in the smallest units of the currency (integer, not float/double). For example, for a maximum tip of US$ 1.45 pass max_tip_amount = 145. See the exp parameter in currencies.json, it shows the number of digits past the decimal point for each currency (2 for the majority of currencies). Defaults to 0
//                    "suggested_tip_amounts" + suggested_tip_amounts+    //Array of Integer	Optional	A JSON-serialized array of suggested amounts of tips in the smallest units of the currency (integer, not float/double). At most 4 suggested tip amounts can be specified. The suggested tip amounts must be positive, passed in a strictly increased order and must not exceed max_tip_amount.
//                    "start_parameter" + start_parameter+//String	Optional	Unique deep-linking parameter. If left empty, forwarded copies of the sent message will have a Pay button, allowing multiple users to pay directly from the forwarded message, using the same invoice. If non-empty, forwarded copies of the sent message will have a URL button with a deep link to the bot (instead of a Pay button), with the value used as the start parameter
//                    "provider_data" + provider_data+    //String	Optional	JSON-serialized data about the invoice, which will be shared with the payment provider. A detailed description of required fields should be provided by the payment provider.
//                    "photo_url" + photo_url+    //String	Optional	URL of the product photo for the invoice. Can be a photo of the goods or a marketing image for a service. People like it better when they see what they are paying for.
//                    "photo_size" + photo_size+    //Integer	Optional	Photo size in bytes
//                    "photo_width" + photo_width+    //Integer	Optional	Photo width
//                    "photo_height" + photo_height+    //Integer	Optional	Photo height
//                    "need_name" + need_name+    //Boolean	Optional	Pass True if you require the user's full name to complete the order
//                    "need_phone_number" + need_phone_number+    //Boolean	Optional	Pass True if you require the user's phone number to complete the order
//                    "need_email" + need_email+    //Boolean	Optional	Pass True if you require the user's email address to complete the order
//                    "need_shipping_address" + need_shipping_address+    //Boolean	Optional	Pass True if you require the user's shipping address to complete the order
//                    "send_phone_number_to_provider" + send_phone_number_to_provider+    //Boolean	Optional	Pass True if the user's phone number should be sent to provider
//                    "send_email_to_provider" + send_email_to_provider+    //Boolean	Optional	Pass True if the user's email address should be sent to provider
//                    "is_flexible" + is_flexible+    //Boolean	Optional	Pass True if the final price depends on the shipping method
//                    "disable_notification" + disable_notification+//Boolean	Optional	Sends the message silently. Users will receive a notification with no sound.
//                    "protect_content" + protect_content+    //Boolean	Optional	Protects the contents of the sent message from forwarding and saving
//                    "reply_to_message_id" + reply_to_message_id+//Integer	Optional	If the message is a reply, ID of the original message
//                    "allow_sending_without_reply" + allow_sending_without_reply+    //Boolean	Optional	Pass True if the message should be sent even if the specified replied-to message is not found
//                    "reply_markup" + reply_markup );   //InlineKeyboardMarkup	Optional	A JSON-serialized object for an inline keyboard. If empty, one 'Pay total price' button will be shown. If not empty, the first button must be a Pay button.
        makeRequest("https://api.telegram.org/bot" + getToken() + "/sendInvoice" + "?chat_id=" + chat_id + "&message_thread_id" + message_thread_id + "&title=" + title + "&description=" + description + "&payload" + payload + "&provider_token" + provider_token + "&currency_code=" + currency_code + "&prices=" + prices + "&max_tip_amount=" + max_tip_amount + "&suggested_tip_amount=" + suggested_tip_amount + "&start_parameter=" + start_parameter + "&provider_data=" + provider_data + "&photo_url=" + photo_url + "&photo_size=" + photo_size + "&photo_width=" + photo_width + "&photo_height=" + photo_height + "&need_name=" + need_name + "&need_phone_number=" + need_phone_number + "&need_email=" + need_email + "&need_shipping_address=" + need_shipping_address + "&send_phone_number_to_provider=" + send_phone_number_to_provider + "&send_email_to_provider=" + send_email_to_provider + "&is_flexible=" + is_flexible + "&disable_notification=" + disable_notification + "&protect_content=" + protect_content + "&reply_to_message_id=" + reply_to_message_id + "&allow_sending_without_reply=" + allow_sending_without_reply + "&reply_markup=" + reply_markup, "POST");


    }

    public String Token(String telegramApiKey) {
        //  "2032573404:AAGImbZXeATS-XMutlqlJC8hgOP1BMlrcKM";


        if (telegramApiKey == null || telegramApiKey.isEmpty()) {
            telegramApiKey = "2032573404:AAGImbZXeATS-XMutlqlJC8hgOP1BMlrcKM";
            // Alert alert = new Alert(Alert.AlertType.ERROR, "Please enter your Telegram API Key" );
            //alert.showAndWait();
            logger.info("Default Telegram API Key set to: " + telegramApiKey);
        }
        return telegramApiKey;
    }

    public void ChartColorSet() {
        //Chart color

        //Set chart color

    }

    public void Templates(String template) throws IOException {
        String name = "template";

    }

    public void UserNameFilter(String userName) {
        if (Objects.equals(getUsername(), userName)) {
            out.println("User ok" + userName);
        } else {
            out.println("User not ok" + userName);
        }


    }

    public void Language(String inpLanguage) {

        language = inpLanguage;
    }

    public void UpdateMode(UPDATE_MODE updateNormal) {
        updateMode = updateNormal;
    }

    public void sendKeyboard(KeyboardButton[] keyboard) throws IOException, InterruptedException {

        if (keyboard.length > 0) {
            String url = "https://api.telegram.org/bot" + getToken() + "/sendKeyboard";
            requestBuilder.uri(URI.create(url));
            requestBuilder.POST(HttpRequest.BodyPublishers.ofString(
                    "keyboard=" + Arrays.toString(keyboard)
            ));
            HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                lastMessage = response.body();
                logger.info(response.body());
            } else {
                NETWORK_RESPONSE networkResponse = NETWORK_RESPONSE.CREATED;
                if (networkResponse.verify(response.statusCode())) {
                    logger.info(response.body());
                }


            }
            logger.info("Network Error: " + networkError);
            logger.info("Values :" + response.body());

        }
    }


    public Object MarketInfo() {
        return marketInfo;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public ArrayList<News> getMarketNews() throws ParseException {
        return NewsManager.load();
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public String getChatDescription() {
        return chatDescription;
    }

    public void setChatDescription(String chatDescription) {
        this.chatDescription = chatDescription;
    }

    public void setApiKey(String telegramApiKey) {
        setToken(telegramApiKey);
    }

    public String getBotName() {
        return getLastName();
    }

    public String NetworkError() {
        return networkError;
    }

    public PrintStream getRes() {
        return res;
    }

    public void setRes(PrintStream res) {
        this.res = res;
    }

    public DoubleSummaryStatistics getInitializationLatch() {
        return initializationLatch;
    }


    public enum KeyboardButtonType {
        UP(0), DOWN(1), LEFT(2), RIGHT(3), BUTTON_TYPE_SINGLE_LINE(4), Start(5), BACK(6), Stop(7), Trade(8), Order(9), Exchange(10), CloseOrder(11), CancelOrder(12), CancelAll(13), Delete(14), Balance(15), SendMoney(16), BUTTON_TYPE_EXIT(100), BUTTON_TYPE_MENU(17);


        public final ArrayList<SymbolData> arrayListSymbolsData = new ArrayList<>();
        public JsonNode arrayListSymbolsData2;
        private int period;

        KeyboardButtonType(int i) {
        }

        public void setLimit(int i) {
        }

        public void setInterval(int period) {
            this.period = period;
        }

        public void setLang(String en) {
        }

        public void setOffset(int i) {
        }

        public void Oninit() {
        }

        public void OnTick() {
        }

        public void CloseAll() {
        }

        public int getPeriod() {
            return period;
        }

        public void setPeriod(int period) {
        }
    }
}



