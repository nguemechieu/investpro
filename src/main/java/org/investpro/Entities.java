package org.investpro;

public class Entities {
    // type	String	Type of the entity. Currently, can be “mention” (@username), “hashtag” (#hashtag), “cashtag” ($USD), “bot_command” (/start@jobs_bot), “url” (https://telegram.org), “email” (do-not-reply@telegram.org), “phone_number” (+1-212-555-0123), “bold” (bold text), “italic” (italic text), “underline” (underlined text), “strikethrough” (strikethrough text), “spoiler” (spoiler message), “code” (monowidth string), “pre” (monowidth block), “text_link” (for clickable text URLs), “text_mention” (for users without usernames), “custom_emoji” (for inline custom emoji stickers)
    // offset	Integer	Offset in UTF-16 code units to the start of the entity
    // length	Integer	Length of the entity in UTF-16 code units
    // url	String	Optional. For “text_link” only, URL that will be opened after user taps on the text
    //user	Optional. For “text_mention” only, the mentioned user
    //language	String	Optional. For “pre” only, the programming language of the entity text
    //custom_emoji_id	String

    public String type;
    public Integer offset;
    public Integer length;
    public String url;
    public User user;
    public String language;
    public String custom_emoji_id;

    public Entities(
            String type,
            Integer offset,
            Integer length,
            String url,
            User user,
            String custom_emoji_id,
            String language
    ) {
        this.type = type;
        this.offset = offset;
        this.length = length;
        this.url = url;
        this.user = user;
        this.custom_emoji_id = custom_emoji_id;
        this.language = language;
    }

    public Entities() {
    }

    @Override
    public String toString() {
        return
                "type='" + type + '\'' +
                        ", offset=" + offset +
                        ", length=" + length +
                        ", url='" + url + '\'' +
                        ", user=" + user +
                        ", custom_emoji_id='" + custom_emoji_id + '\'' +
                        ", language='" + language + '\'';
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getCustom_emoji_id() {
        return custom_emoji_id;
    }

    public void setCustom_emoji_id(String custom_emoji_id) {
        this.custom_emoji_id = custom_emoji_id;
    }

    public long getOffset() {
        return offset;
    }

    public void setOffset(Integer offset) {
        this.offset = offset;
    }

    public void setOffset(long offset1) {
        this.offset = Math.toIntExact(offset1);
    }

    public long getLength() {
        return length;
    }

    public void setLength(Integer length) {
        this.length = length;
    }

    public void setLength(long length1) {
        this.length = Math.toIntExact(length1);
    }

    public String getType() {
        return type;
    }

    public void setType(String type1) {
        this.type = type1;
    }


}
