package org.investpro.exchange.models;

import lombok.Builder;
import lombok.Value;
import java.time.Instant;
import java.util.Map;

/**
 * Result of an authentication check against an exchange.
 *
 * <p>
 * Distinguishes between:
 * <ul>
 * <li>Valid credentials (success=true)</li>
 * <li>Invalid credentials (success=false, credentialIssue=true)</li>
 * <li>Network/endpoint failure (success=false, credentialIssue=false)</li>
 * </ul>
 *
 * <p>
 * This allows operators to understand whether the app is misconfigured vs. the
 * exchange
 * is unreachable.
 */
@Value
@Builder(toBuilder = true)
public class AuthCheckResult {
    String exchangeName;
    boolean success;

    // Diagnostic info
    String credentialSource; // e.g., "ENV_VAR", "CONFIG_FILE", "PARAMETER"
    String endpointTested; // e.g., "/v3/accounts", "/api/v3/account"
    int httpStatus; // HTTP status code from the check attempt
    String message; // Human-readable result

    // True if credentials are invalid; false if endpoint/network failure
    boolean credentialIssue;

    Instant checkedAt;
    Map<String, String> metadata; // Extra context: key name format, scope hints, etc.
}
