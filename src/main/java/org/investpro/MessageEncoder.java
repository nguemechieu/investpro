package org.investpro;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.EndpointConfig;

public class MessageEncoder implements Encoder.Text<Message> {
    private static final Logger logger = LoggerFactory.getLogger(MessageEncoder.class);

    private static final Gson gson = new Gson();

    @Override
    public String encode(Message message) throws EncodeException {
        logger.info("MessageEncoder encode");
        return gson.toJson(message);
    }


    @Override
    public void init(EndpointConfig endpointConfig) {
        logger.info("MessageEncoder init");

    }

    @Override
    public void destroy() {
        // Close resources
        logger.info("MessageEncoder destroyed");
    }
}
