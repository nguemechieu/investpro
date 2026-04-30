package org.investpro.core.chat;


import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
@Setter
@Getter
public class TelegramApiException extends Throwable {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TelegramApiException.class);

    private final Object exception;

    public TelegramApiException(Object exception) {

        this.exception = exception;
        logger.error((String) exception);
    }

}
