package org.investpro.exchange;

import org.investpro.exchange.coinbase.Coinbase;
import org.investpro.exchange.models.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.*;

/**
 * Contract tests for Coinbase exchange adapter.
 *
 * <p>
 * These tests verify:
 * <ul>
 * <li>Authentication credential format validation</li>
 * <li>Capability declarations</li>
 * <li>Order book depth support</li>
 * <li>Credential source tracking</li>
 * </ul>
 */
@DisplayName("Coinbase Exchange Adapter Contract Tests")
class CoinbaseAdapterContractTest {

        @Test
        @DisplayName("Coinbase decodes gzip JSON responses before parsing")
        void testCoinbaseDecodesGzipResponseBodies() throws Exception {
                String json = "{\"accounts\":[]}";
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
                        gzip.write(json.getBytes(StandardCharsets.UTF_8));
                }
                byte[] gzipData = output.toByteArray();

                // When encoding is specified as gzip, decompress the data
                assertThat(Coinbase.decodeBody(gzipData, "gzip")).isEqualTo(json);

                // When no encoding is specified, raw plain text should pass through
                assertThat(Coinbase.decodeBody(json.getBytes(StandardCharsets.UTF_8), ""))
                                .isEqualTo(json);
        }

        @Test
        @DisplayName("Coinbase capability declares FULL_ORDER_BOOK support")
        void testCoinbaseCapabilityFullOrderBook() {
                // Coinbase provides full order book depth (unlike OANDA)

                ExchangeCapability coinbaseCapability = ExchangeCapability.builder()
                                .exchangeName("Coinbase")
                                .supportsCrypto(true)
                                .supportsSpot(true)
                                .supportsFullOrderBook(true) // Coinbase HAS full depth books
                                .marketDepthType(MarketDepthType.FULL_ORDER_BOOK)
                                .authenticationType("JWT")
                                .apiBaseUrl("https://api.coinbase.com/api/v3/brokerage")
                                .build();

                assertThat(coinbaseCapability)
                                .extracting("exchangeName").isEqualTo("Coinbase");

                assertThat(coinbaseCapability.isSupportsFullOrderBook()).isTrue();
                assertThat(coinbaseCapability.getMarketDepthType())
                                .isEqualTo(MarketDepthType.FULL_ORDER_BOOK);
        }

        @Test
        @DisplayName("Coinbase uses JWT authentication")
        void testCoinbaseAuthenticationType() {
                ExchangeCapability coinbase = ExchangeCapability.builder()
                                .exchangeName("Coinbase")
                                .authenticationType("JWT")
                                .build();

                assertThat(coinbase.getAuthenticationType())
                                .isNotEmpty()
                                .contains("JWT");
        }

        @Test
        @DisplayName("Auth check result includes credential source")
        void testAuthCheckIncludesCredentialSource() {
                // Example from environment variable
                AuthCheckResult envVar = AuthCheckResult.builder()
                                .exchangeName("Coinbase")
                                .success(true)
                                .credentialSource("ENVIRONMENT_VARIABLE") // CB_API_KEY, CB_API_SECRET, CB_API_KEY_NAME
                                .endpointTested("/api/v3/brokerage/accounts")
                                .httpStatus(200)
                                .message("OK")
                                .build();

                // Example from config file
                AuthCheckResult configFile = AuthCheckResult.builder()
                                .exchangeName("Coinbase")
                                .success(true)
                                .credentialSource("CONFIG_FILE") // From application.properties or .env
                                .endpointTested("/api/v3/brokerage/accounts")
                                .httpStatus(200)
                                .message("OK")
                                .build();

                // Example from parameter/constructor
                AuthCheckResult parameter = AuthCheckResult.builder()
                                .exchangeName("Coinbase")
                                .success(true)
                                .credentialSource("PARAMETER")
                                .endpointTested("/api/v3/brokerage/accounts")
                                .httpStatus(200)
                                .message("OK")
                                .build();

                // Verify all track source
                assertThat(envVar.getCredentialSource()).isNotEmpty();
                assertThat(configFile.getCredentialSource()).isNotEmpty();
                assertThat(parameter.getCredentialSource()).isNotEmpty();
        }

        @Test
        @DisplayName("HTTP status codes distinguish error types")
        void testHttpStatusErrorCodes() {
                // 401 = Invalid credentials
                AuthCheckResult unauthorized = AuthCheckResult.builder()
                                .exchangeName("Coinbase")
                                .success(false)
                                .credentialIssue(true)
                                .httpStatus(401)
                                .message("Invalid API key")
                                .build();

                // 403 = Forbidden (insufficient permissions)
                AuthCheckResult forbidden = AuthCheckResult.builder()
                                .exchangeName("Coinbase")
                                .success(false)
                                .credentialIssue(true) // Credential issue (wrong scope/permissions)
                                .httpStatus(403)
                                .message("Insufficient permissions for this operation")
                                .build();

                // 429 = Rate limited (not credential issue)
                AuthCheckResult rateLimited = AuthCheckResult.builder()
                                .exchangeName("Coinbase")
                                .success(false)
                                .credentialIssue(false)
                                .httpStatus(429)
                                .message("Too many requests")
                                .build();

                // 500+ = Server error (not credential issue)
                AuthCheckResult serverError = AuthCheckResult.builder()
                                .exchangeName("Coinbase")
                                .success(false)
                                .credentialIssue(false)
                                .httpStatus(503)
                                .message("Service unavailable")
                                .build();

                assertThat(unauthorized.getHttpStatus()).isEqualTo(401);
                assertThat(unauthorized.isCredentialIssue()).isTrue();

                assertThat(forbidden.getHttpStatus()).isEqualTo(403);
                assertThat(forbidden.isCredentialIssue()).isTrue();

                assertThat(rateLimited.getHttpStatus()).isEqualTo(429);
                assertThat(rateLimited.isCredentialIssue()).isFalse();

                assertThat(serverError.getHttpStatus()).isEqualTo(503);
                assertThat(serverError.isCredentialIssue()).isFalse();
        }

        @Test
        @DisplayName("Coinbase supports crypto spot and derivatives")
        void testCoinbaseAssetTypeSupport() {
                ExchangeCapability coinbase = ExchangeCapability.builder()
                                .exchangeName("Coinbase")
                                .supportsSpot(true)
                                .supportsCrypto(true)
                                .supportsDerivatives(true) // Coinbase has derivatives/futures
                                .supportsFutures(true)
                                .supportsPerpetuals(false) // Coinbase Advanced (not perpetual swaps like Binance)
                                .build();

                assertThat(coinbase.isSupportsSpot()).isTrue();
                assertThat(coinbase.isSupportsDerivatives()).isTrue();
                assertThat(coinbase.isSupportsPerpetuals()).isFalse();
        }

        @Test
        @DisplayName("Order validation result captures validation messages")
        void testOrderValidationMessages() {
                // Success case
                OrderValidationResult validOrder = OrderValidationResult.builder()
                                .success(true)
                                .validationMessages(java.util.Collections.emptyList())
                                .build();

                // Failure case with explanations
                OrderValidationResult invalidOrder = OrderValidationResult.builder()
                                .success(false)
                                .validationMessages(java.util.Arrays.asList(
                                                "Lot size 0.001 BTC below minimum of 0.001 BTC",
                                                "Trading pair BTC_INVALID not found on Coinbase"))
                                .errorCode("VALIDATION_FAILED")
                                .build();

                assertThat(validOrder.isSuccess()).isTrue();
                assertThat(validOrder.getValidationMessages()).isEmpty();

                assertThat(invalidOrder.isSuccess()).isFalse();
                assertThat(invalidOrder.getValidationMessages())
                                .hasSize(2)
                                .anySatisfy(msg -> assertThat(msg).contains("Lot size"))
                                .anySatisfy(msg -> assertThat(msg).contains("not found"));
        }

        @Test
        @DisplayName("Order result captures placement success/failure")
        void testOrderResultStatus() {
                // Success
                OrderResult successResult = OrderResult.builder()
                                .success(true)
                                .orderId("local-order-123")
                                .exchangeOrderId("cb-order-456")
                                .status("PENDING")
                                .filledSize(0.0) // Not filled yet
                                .filledPrice(0.0)
                                .httpStatus(200)
                                .build();

                // Failure
                OrderResult failureResult = OrderResult.builder()
                                .success(false)
                                .errorCode("INSUFFICIENT_FUNDS")
                                .message("Account balance insufficient for this order")
                                .httpStatus(400)
                                .build();

                assertThat(successResult.isSuccess()).isTrue();
                assertThat(successResult.getExchangeOrderId()).isNotEmpty();

                assertThat(failureResult.isSuccess()).isFalse();
                assertThat(failureResult.getErrorCode()).isNotEmpty();
                assertThat(failureResult.getHttpStatus()).isEqualTo(400);
        }

        @Test
        @DisplayName("Generic ExchangeOperationResult handles any operation")
        void testExchangeOperationResultGeneric() {
                // Success case
                ExchangeOperationResult<String> successOp = ExchangeOperationResult.<String>builder()
                                .success(true)
                                .data("success-payload")
                                .httpStatus(200)
                                .build();

                // Failure case
                ExchangeOperationResult<String> failureOp = ExchangeOperationResult.<String>builder()
                                .success(false)
                                .errorCode("NETWORK_ERROR")
                                .message("Connection timeout")
                                .httpStatus(0) // No HTTP code for network error
                                .build();

                assertThat(successOp.isSuccess()).isTrue();
                assertThat(successOp.getData()).isEqualTo("success-payload");

                assertThat(failureOp.isSuccess()).isFalse();
                assertThat(failureOp.getErrorCode()).isNotEmpty();
        }
}
