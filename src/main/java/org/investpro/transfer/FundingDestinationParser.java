package org.investpro.transfer;

import java.util.Locale;

public final class FundingDestinationParser {

    private FundingDestinationParser() {
    }

    public static String parsePaymentMethodId(String destination) {
        String value = destination == null ? "" : destination.trim();
        if (value.isBlank()) {
            return "";
        }

        int colonIndex = value.indexOf(':');
        if (colonIndex > 0 && colonIndex < value.length() - 1) {
            String key = value.substring(0, colonIndex).trim().toLowerCase(Locale.ROOT);
            String id = value.substring(colonIndex + 1).trim();
            if (id.isBlank()) {
                return "";
            }
            if ("bank_account".equals(key)
                    || "debit_card".equals(key)
                    || "payment_method".equals(key)
                    || "payment_method_id".equals(key)) {
                return id;
            }
        }

        return value;
    }
}
