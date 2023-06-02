package org.investpro;

public class KeyboardButton {
    public static Object Button;
    //    text	String	Text of the button. If none of the optional fields are used, it will be sent as a message when the button is pressed
//    request_contact	Boolean	Optional. If True, the user's phone number will be sent as a contact when the button is pressed. Available in private chats only.
//    request_location	Boolean	Optional. If True, the user's current location will be sent when the button is pressed. Available in private chats only.
//    request_poll	KeyboardButtonPollType	Optional. If specified, the user will be asked to create a poll and send it to the bot when the button is pressed. Available in private chats only.
//    web_app	WebAppInfo
    private String text;
    private boolean request_contact;
    private boolean request_location;

    public KeyboardButton(String start, TelegramClient.KeyboardButtonType buttonTypeSingleLine) {
        this.text = start;
        this.request_contact = false;
        this.request_location = false;
    }

    @Override
    public String toString() {
        return
                "text='" + text + '\'' +
                        ", request_contact=" + request_contact +
                        ", request_location=" + request_location;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public boolean isRequest_contact() {
        return request_contact;
    }

    public void setRequest_contact(boolean request_contact) {
        this.request_contact = request_contact;
    }

    public boolean isRequest_location() {
        return request_location;
    }

    public void setRequest_location(boolean request_location) {
        this.request_location = request_location;
    }

}
