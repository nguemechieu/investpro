package org.investpro.transfer;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class TransferFeeCalculator {

    public BigDecimal calculate(TransferRequest request, boolean isInternal, boolean stablecoinTransfer) {
        BigDecimal amount = request.amount() == null ? BigDecimal.ZERO : request.amount();
        if (amount.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        if (isInternal) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal bps = stablecoinTransfer ? new BigDecimal("0.0005") : new BigDecimal("0.0015");
        BigDecimal networkSurcharge = "CRYPTO".equalsIgnoreCase(request.network())
                ? new BigDecimal("1.50")
                : BigDecimal.ZERO;

        return amount.multiply(bps)
                .add(networkSurcharge)
                .max(new BigDecimal("0.10"))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
