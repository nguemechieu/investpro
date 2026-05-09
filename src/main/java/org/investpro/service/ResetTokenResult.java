package org.investpro.service;

public record ResetTokenResult(boolean success, String token, String email, String message) {
    public static ResetTokenResult success(String token, String email, String message) {
        return new ResetTokenResult(true, token, email, message);
    }

    public static ResetTokenResult failure(String message) {
        return new ResetTokenResult(false, "", "", message);
    }
}
