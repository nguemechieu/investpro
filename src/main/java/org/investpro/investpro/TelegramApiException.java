package org.investpro.investpro;

public class TelegramApiException extends Throwable {
    private final int code;

    public TelegramApiException(String description, int code) {
        super(description);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
