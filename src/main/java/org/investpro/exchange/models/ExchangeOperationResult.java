package org.investpro.exchange.models;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Represents the outcome of an exchange operation (order submission,
 * balance fetch, cancellation, etc.).
 *
 * <p>A result is either successful (carrying a payload) or failed
 * (carrying an error code and message). Callers should always check
 * {@link #isSuccess()} before calling {@link #getPayload()}.
 *
 * @param <T> the payload type on success
 */
public final class ExchangeOperationResult<T> {

    private final boolean success;
    @Nullable
    private final T payload;
    @Nullable
    private final String errorCode;
    @Nullable
    private final String errorMessage;
    private final int httpStatus;
    @NotNull
    private final String operation;
    @NotNull
    private final String exchangeId;

    private ExchangeOperationResult(
            boolean success,
            @Nullable T payload,
            @Nullable String errorCode,
            @Nullable String errorMessage,
            int httpStatus,
            @NotNull String operation,
            @NotNull String exchangeId
    ) {
        this.success = success;
        this.payload = payload;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.httpStatus = httpStatus;
        this.operation = operation;
        this.exchangeId = exchangeId;
    }

    /** Creates a successful result carrying the given payload. */
    public static <T> ExchangeOperationResult<T> success(
            @NotNull String operation,
            @NotNull String exchangeId,
            @NotNull T payload
    ) {
        return new ExchangeOperationResult<>(true, payload, null, null, 200, operation, exchangeId);
    }

    /** Creates a failure result with the given error code and message. */
    public static <T> ExchangeOperationResult<T> failure(
            @NotNull String operation,
            @NotNull String exchangeId,
            @NotNull String errorCode,
            @NotNull String errorMessage
    ) {
        return new ExchangeOperationResult<>(false, null, errorCode, errorMessage, 0, operation, exchangeId);
    }

    /** Creates a failure result indicating the operation is not supported by this exchange. */
    public static <T> ExchangeOperationResult<T> unsupported(
            @NotNull String operation,
            @NotNull String exchangeId
    ) {
        return failure(operation, exchangeId, "UNSUPPORTED",
                "Operation '" + operation + "' is not supported by exchange '" + exchangeId + "'");
    }

    /** Creates a failure result for an authentication failure. */
    public static <T> ExchangeOperationResult<T> authFailure(
            @NotNull String operation,
            @NotNull String exchangeId
    ) {
        return failure(operation, exchangeId, "AUTH_FAILED",
                "Authentication failed for exchange '" + exchangeId + "'");
    }

    /** Creates a failure result for a network timeout. */
    public static <T> ExchangeOperationResult<T> timeout(
            @NotNull String operation,
            @NotNull String exchangeId
    ) {
        return failure(operation, exchangeId, "TIMEOUT",
                "Request timed out for operation '" + operation + "' on exchange '" + exchangeId + "'");
    }

    public boolean isSuccess()            { return success; }
    public boolean isFailure()            { return !success; }
    public @NotNull String getOperation() { return operation; }
    public @NotNull String getExchangeId() { return exchangeId; }
    public int getHttpStatus() { return httpStatus; }

    /**
     * Returns the operation payload on success.
     *
     * @throws IllegalStateException if the result is a failure
     */
    public @NotNull T getPayload() {
        if (!success || payload == null) {
            throw new IllegalStateException(
                    "Cannot get payload of failed ExchangeOperationResult: " + errorMessage);
        }
        return payload;
    }

    /** Backward-compatible alias for {@link #getPayload()}. */
    public @NotNull T getData() {
        return getPayload();
    }

    /** Returns the payload as Optional — empty if this is a failure result. */
    public @NotNull Optional<T> getPayloadOptional() {
        return Optional.ofNullable(payload);
    }

    public @NotNull Optional<String> getErrorCode()    { return Optional.ofNullable(errorCode); }
    public @NotNull Optional<String> getErrorMessage() { return Optional.ofNullable(errorMessage); }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static final class Builder<T> {
        private boolean success;
        @Nullable
        private T data;
        @Nullable
        private String errorCode;
        @Nullable
        private String message;
        private int httpStatus;
        private String operation = "generic";
        private String exchangeId = "unknown";

        public Builder<T> success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder<T> data(@Nullable T data) {
            this.data = data;
            return this;
        }

        public Builder<T> errorCode(@Nullable String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder<T> message(@Nullable String message) {
            this.message = message;
            return this;
        }

        public Builder<T> httpStatus(int httpStatus) {
            this.httpStatus = httpStatus;
            return this;
        }

        public Builder<T> operation(@NotNull String operation) {
            this.operation = operation;
            return this;
        }

        public Builder<T> exchangeId(@NotNull String exchangeId) {
            this.exchangeId = exchangeId;
            return this;
        }

        public ExchangeOperationResult<T> build() {
            return new ExchangeOperationResult<>(
                    success,
                    data,
                    errorCode,
                    message,
                    httpStatus,
                    operation,
                    exchangeId);
        }
    }

    @Override
    public String toString() {
        return success
                ? "ExchangeOperationResult{OK, op=%s, exchange=%s}".formatted(operation, exchangeId)
                : "ExchangeOperationResult{FAIL, op=%s, exchange=%s, code=%s, msg=%s}"
                        .formatted(operation, exchangeId, errorCode, errorMessage);
    }
}
