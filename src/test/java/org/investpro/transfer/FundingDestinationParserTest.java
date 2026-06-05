package org.investpro.transfer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FundingDestinationParserTest {

    @Test
    void parsesBankAccountPrefix() {
        assertEquals("pm_bank_123", FundingDestinationParser.parsePaymentMethodId("bank_account:pm_bank_123"));
    }

    @Test
    void parsesDebitCardPrefix() {
        assertEquals("pm_card_789", FundingDestinationParser.parsePaymentMethodId("debit_card:pm_card_789"));
    }

    @Test
    void keepsRawPaymentMethodIdWhenNoPrefix() {
        assertEquals("pm_direct_456", FundingDestinationParser.parsePaymentMethodId("pm_direct_456"));
    }
}
