package org.investpro.ai;

import java.math.BigDecimal;

public class AiCreditAccount {
    private BigDecimal paidCredits;
    private BigDecimal freeCredits;

    public AiCreditAccount(BigDecimal paidCredits, BigDecimal freeCredits) {
        this.paidCredits = paidCredits == null ? BigDecimal.ZERO : paidCredits;
        this.freeCredits = freeCredits == null ? BigDecimal.ZERO : freeCredits;
    }

    public BigDecimal paidCredits() {
        return paidCredits;
    }

    public BigDecimal freeCredits() {
        return freeCredits;
    }

    public boolean hasEnoughCredits(BigDecimal estimatedCost) {
        BigDecimal cost = estimatedCost == null ? BigDecimal.ZERO : estimatedCost;
        return paidCredits.add(freeCredits).compareTo(cost) >= 0;
    }

    public void debit(BigDecimal amount) {
        BigDecimal remaining = amount == null ? BigDecimal.ZERO : amount.max(BigDecimal.ZERO);
        BigDecimal fromFree = freeCredits.min(remaining);
        freeCredits = freeCredits.subtract(fromFree);
        remaining = remaining.subtract(fromFree);
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            if (paidCredits.compareTo(remaining) < 0) {
                throw new IllegalArgumentException("Not enough AI credits");
            }
            paidCredits = paidCredits.subtract(remaining);
        }
    }

    public void credit(BigDecimal amount) {
        paidCredits = paidCredits.add(amount == null ? BigDecimal.ZERO : amount.max(BigDecimal.ZERO));
    }
}
