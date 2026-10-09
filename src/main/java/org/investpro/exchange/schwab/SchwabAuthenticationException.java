package org.investpro.exchange.schwab;

/** Messages contain only safe local descriptions and HTTP status codes. */
public class SchwabAuthenticationException extends java.io.IOException {
    public SchwabAuthenticationException(String message) { super(message); }
}
