package org.investpro.investpro;


public class Chat {
    public Chat m_new_one;
    public boolean done;
    public String message_text;
    String  chat_id;
    String title;
    private String photo;
    private String description;

    public Chat(int chat_id, String chat_type, String chat_title, String text, String chat_first_name, String chat_last_name, String chat_username) {

        this.message_text = text;
        this.chat_id = String.valueOf(chat_id);
        this.title = chat_title;
        this.photo = chat_first_name + " " + chat_last_name +
                " <" + chat_username + ">";

    }

    public String getChat_id() {
        return chat_id;
    }

    public void setChat_id(String chat_id) {
        this.chat_id = chat_id;
    }

    @Override
    public String toString() {
        return "Chat " +
                "chat_id='" + chat_id + '\'' +
                ", title='" + title + '\'' +
                ", photo='" + photo + '\'' +
                ", description='" + description + '\'' +
                ", type='" + type + '\'' +
                ", username='" + username + '\''
                ;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Chat(String chat_id, String title, String type, String username) {
        this.chat_id = chat_id;
        this.title = title;
        this.type = type;
        this.username = username;
    }

    String type;
    String username ;

    public void setDescription(String description) {
        this.description = description;
    }

    public void setPhoto(String photo) {
        this.photo = photo;
    }

    public String getPhoto() {
        return photo;
    }

    public String getDescription() {
        return description;
    }

    public void setName(String first_name) {
        this.title = first_name;
    }

    public void setId(String id) {
        this.chat_id = id;
    }
}