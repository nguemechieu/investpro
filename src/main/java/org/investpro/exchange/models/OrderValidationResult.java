package org.investpro.exchange.models;

import lombok.Builder;
import lombok.Value;
import java.util.List;

/**
 * Result of validating an order before placement.
 *
 * <p>
 * Used to catch invalid orders before they are sent to the exchange, improving
 * diagnostics
 * and preventing unnecessary HTTP round-trips.
 */
@Value
@Builder(toBuilder = true)
public class OrderValidationResult {
    boolean success;
    String orderId; // Placeholder ID or validation token if applicable
    List<String> validationMessages; // E.g., "Lot size below minimum", "Instrument not available"
    String errorCode; // E.g., "INVALID_QUANTITY", "UNSUPPORTED_ORDER_TYPE"
    int httpStatus; // 0 if validation-only, HTTP code if from exchange
}
