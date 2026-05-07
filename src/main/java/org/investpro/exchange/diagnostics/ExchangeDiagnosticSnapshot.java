package org.investpro.exchange.diagnostics;

import lombok.Builder;
import lombok.Value;
import org.investpro.exchange.models.ExchangeCapability;
import org.investpro.exchange.models.MarketDepthType;

import java.time.Instant;

/**
 * A snapshot of the current diagnostic state of an exchange.
 *
 * <p>
 * Captures:
 * <ul>
 * <li>Credential source and auth status</li>
 * <li>Last endpoint tested and its HTTP status</li>
 * <li>Last error message (sanitized)</li>
 * <li>Capability snapshot at test time</li>
 * <li>Timestamp of the snapshot</li>
 * </ul>
 */
@Value
@Builder(toBuilder = true)
public class ExchangeDiagnosticSnapshot {
    String exchangeName;

    // Credentials
    String credentialSource; // e.g., "ENV_VAR", "CONFIG_FILE"
    String credentialKeyFormat; // e.g., "API_KEY_PRESENT", "JWT_PRESENT"

    // Auth Status
    boolean authSuccess;
    String authMessage;

    // Last Endpoint Test
    String lastEndpointTested;
    int lastHttpStatus; // 0 if no test
    String lastErrorMessage; // Sanitized (no secrets)

    // Capability snapshot
    ExchangeCapability capability;

    // Additional status from recent operations
    boolean lastPriceSuccess;
    String lastPriceEndpoint;

    boolean lastOrderBookSuccess;
    String lastOrderBookEndpoint;
    MarketDepthType marketDepthType;

    boolean lastAccountSuccess;
    String lastAccountEndpoint;

    // Timestamp
    Instant snapshotTime;
}
