package org.investpro;


import java.io.Serial;

public class TelegramApiException extends Throwable {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TelegramApiException.class);

    private final Object exception;

    public TelegramApiException(Object exception) {

        this.exception = exception;
        logger.error((String) exception);
    }

    public Object getException() {
        return exception;
    }
}