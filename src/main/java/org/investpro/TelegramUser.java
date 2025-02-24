package org.investpro;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TelegramUser {
    private long id;
    private String username;

    // Constructor
    public TelegramUser(long id, String username) {
        this.id = id;
        this.username = username;
    }

}
