package org.investpro.exchange.schwab;

public final class SchwabApiException extends java.io.IOException {
    private final int status;
    public SchwabApiException(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
}
