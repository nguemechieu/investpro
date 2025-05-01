package org.investpro.investpro;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mysql.cj.x.protobuf.MysqlxExpr;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Holds Telegram bot-related information, including user details and messages.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)  // Ignores extra JSON fields to prevent errors
public class TelegramBotInfo {
    private int updateId;             // Unique update identifier
    private long chatId;              // Chat ID of sender
    private String botName;           // Bot's name
    private String username;          // Bot's username
    private String firstName;         // Sender's first name
    private String lastName;          // Sender's last name
    private boolean isBot;            // Whether sender is a bot
    private String languageCode;

    // User's language code
    @JsonProperty("message")
    private Object message;           // Text message content
    private String messageType;       // Type of message (text, photo, video, etc.)
    private long date;                // Unix timestamp of message
    private List<String> entities;    // Message formatting (bold, italics, links, etc.)
    private List<String> photoUrls;   // URLs of attached photos
    private String documentUrl;       // URL of an attached document
    private String videoUrl;          // URL of attached video
    private String voiceUrl;          // URL of voice message
    private String location;          // GPS location if included in message
    private boolean canJoinGroups;    // Bot permission: Can join groups
    private boolean canReadAllGroupMessages; // Bot permission: Read all group messages
    private boolean supportsInlineQueries; // Bot permission: Supports inline queries
    private boolean canConnectToBusiness; // Bot permission: Can connect to business
    private boolean hasMainWebApp;    // Bot permission: Has main web app

    /**
     * Default constructor.
     */
    public TelegramBotInfo() {
    }

    /**
     * Gets the unique update ID.
     *
     * @return The update ID.
     */
    public int getId() {
        return updateId;
    }

    @Override
    public String toString() {
        return "📩 TelegramBotInfo {" +
                "updateId=" + updateId +
                ", chatId=" + chatId +
                ", botName='" + botName + '\'' +
                ", username='" + username + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", isBot=" + isBot +
                ", languageCode='" + languageCode + '\'' +
                ", message='" + message + '\'' +
                ", messageType='" + messageType + '\'' +
                ", date=" + date +
                ", entities=" + entities +
                ", photoUrls=" + photoUrls +
                ", documentUrl='" + documentUrl + '\'' +
                ", videoUrl='" + videoUrl + '\'' +
                ", voiceUrl='" + voiceUrl + '\'' +
                ", location='" + location + '\'' +
                ", canJoinGroups=" + canJoinGroups +
                ", canReadAllGroupMessages=" + canReadAllGroupMessages +
                ", supportsInlineQueries=" + supportsInlineQueries +
                ", canConnectToBusiness=" + canConnectToBusiness +
                ", hasMainWebApp=" + hasMainWebApp +
                '}';
    }
}
