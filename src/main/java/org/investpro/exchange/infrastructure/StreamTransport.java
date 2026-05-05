package org.investpro.exchange.infrastructure;

public enum StreamTransport {
    WEBSOCKET,
    HTTP_STREAM,
    POLLING,
    INTERNAL_EVENT_BUS,
    NONE
}
