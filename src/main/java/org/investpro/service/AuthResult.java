package org.investpro.service;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public record AuthResult(boolean success, String message) {
    @Contract("_ -> new")
    public static @NotNull AuthResult success(String message) {
        return new AuthResult(true, message);
    }

    @Contract("_ -> new")
    public static @NotNull AuthResult failure(String message) {
        return new AuthResult(false, message);
    }
}
