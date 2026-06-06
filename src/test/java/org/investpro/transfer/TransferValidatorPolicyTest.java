package org.investpro.transfer;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferValidatorPolicyTest {

    private final TransferValidator validator = new TransferValidator();
    private final TransferFeeCalculator feeCalculator = new TransferFeeCalculator();

    @Test
    void blocksFiatCrossExchangeTransfers() {
        TransferRequest request = new TransferRequest(
                "Coinbase",
                "Spot",
                "Binance",
                "Spot",
                "USD",
                new BigDecimal("100"),
                "",
                "FIAT",
                3,
                Instant.now());

        TransferValidator.ValidationOutcome result = validator.validate(request, providers(), feeCalculator, true);

        assertFalse(result.valid());
        assertTrue(result.errors().stream()
                .anyMatch(msg -> msg.contains("Only crypto transfers are allowed")));
    }

    @Test
    void allowsCryptoCrossExchangeTransfers() {
        TransferRequest request = new TransferRequest(
                "Coinbase",
                "Spot",
                "Binance",
                "Spot",
                "USDC",
                new BigDecimal("100"),
                "",
                "CRYPTO",
                3,
                Instant.now());

        TransferValidator.ValidationOutcome result = validator.validate(request, providers(), feeCalculator, true);

        assertTrue(result.valid(), () -> "Expected valid transfer but got: " + result.errors());
    }

    @Test
    void allowsSpecificCryptoNetworkForCrossExchangeTransfers() {
        TransferRequest request = new TransferRequest(
                "Coinbase",
                "Spot",
                "Binance",
                "Spot",
                "USDC",
                new BigDecimal("100"),
                "",
                "ERC20",
                3,
                Instant.now());

        TransferValidator.ValidationOutcome result = validator.validate(request, providers(), feeCalculator, true);

        assertTrue(result.valid(), () -> "Expected valid transfer but got: " + result.errors());
    }

    @Test
    void blocksAutoNetworkForCrossExchangeTransfers() {
        TransferRequest request = new TransferRequest(
                "Coinbase",
                "Spot",
                "Binance",
                "Spot",
                "USDC",
                new BigDecimal("100"),
                "",
                "AUTO",
                3,
                Instant.now());

        TransferValidator.ValidationOutcome result = validator.validate(request, providers(), feeCalculator, true);

        assertFalse(result.valid());
        assertTrue(result.errors().stream()
                .anyMatch(msg -> msg.contains("crypto network/rail")));
    }

    private Map<String, TransferProvider> providers() {
        TransferProvider provider = new TransferProvider() {
            @Override
            public Map<String, BigDecimal> getBalances() {
                return Map.of(
                        "USD", new BigDecimal("10000"),
                        "USDC", new BigDecimal("10000"),
                        "BTC", new BigDecimal("10"));
            }

            @Override
            public List<String> getSupportedCurrencies() {
                return List.of("USD", "USDC", "BTC");
            }

            @Override
            public BigDecimal estimateFee(TransferRequest request) {
                return BigDecimal.ZERO;
            }

            @Override
            public TransferValidator.ValidationOutcome validateTransfer(TransferRequest request) {
                return new TransferValidator.ValidationOutcome(true, List.of(), List.of());
            }

            @Override
            public TransferResult executeTransfer(TransferRequest request) {
                throw new UnsupportedOperationException("not needed for validator tests");
            }

            @Override
            public TransferStatus getTransferStatus(String transactionId) {
                return TransferStatus.PENDING;
            }
        };

        return Map.of(
                "Coinbase", provider,
                "Binance", provider);
    }
}
