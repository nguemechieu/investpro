package org.investpro.investpro;

public class Game {
    private String type;
    private String title;
    private  String description;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getText_entities() {
        return text_entities;
    }

    public void setText_entities(String text_entities) {
        this.text_entities = text_entities;
    }

    public String getAnimation() {
        return animation;
    }

    public void setAnimation(String animation) {
        this.animation = animation;
    }

    private  String text;
    private  String text_entities;
    private  String animation;

//    This object represents a game. Use BotFather to create and edit games, their short names will act as unique identifiers.
//
//    Field	Type	Description
//    title	String	Title of the game
//    description	String	Description of the game
//    photo	Array of PhotoSize	Photo that will be displayed in the game message in chats.
//    text	String	Optional. Brief description of the game or high scores included in the game message. Can be automatically edited to include current high scores for the game when the bot calls setGameScore, or manually edited using editMessageText. 0-4096 characters.
//    text_entities	Array of MessageEntity	Optional. Special entities that appear in text, such as usernames, URLs, bot commands, etc.
//    animation	Animation	Optional. Animation that will be displayed in the game message in chats. Upload via BotFather

    public Game(String type, String title,String description, String text , String text_entities, String animation) {
        this.type = type;
        this.title = title;
        this.description = description;
        this.text = text;
        this.text_entities = text_entities;
        this.animation = animation;



    }

}
