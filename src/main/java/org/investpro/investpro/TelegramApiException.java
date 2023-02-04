package org.investpro.investpro;


public class TelegramApiException extends Throwable {


    private final Object exception;

    public TelegramApiException(Object exception) {

        this.exception = exception;
        Log.error((String) exception);
    }

    public Object getException() {
        return exception;
    }
}
