package org.investpro.service;

public record AuthResult(boolean success, String message) {
    public static AuthResult success(String message) {
        return new AuthResult(true, message);
    }

    public static AuthResult failure(String message) {
        return new AuthResult(false, message);
    }
}
