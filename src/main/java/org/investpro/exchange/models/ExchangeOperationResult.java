package org.investpro.exchange.models;

import lombok.Builder;
import lombok.Value;
import java.time.Instant;

/**
 * Generic result wrapper for exchange operations.
 *
 * <p>
 * Replaces null-based error handling with explicit success/failure state.
 *
 * @param <T> The data type on success
 */
@Value
@Builder(toBuilder = true)
public class ExchangeOperationResult<T> {
    boolean success;

    // On success
    T data;

    // On failure
    String errorCode; // e.g., "HTTP_401", "INVALID_PAIR", "RATE_LIMITED"
    String message;
    int httpStatus; // 0 if not an HTTP error
    String endpoint; // Which endpoint failed, if applicable

    Instant timestamp;
}
