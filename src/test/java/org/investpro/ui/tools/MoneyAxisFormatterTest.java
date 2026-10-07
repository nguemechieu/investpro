package org.investpro.ui.tools;

import org.junit.jupiter.api.Test;
import org.investpro.models.currency.Currency;
import org.investpro.models.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MoneyAxisFormatterTest {
    @Test void usdAxisPreservesSmallPricesAndExplicitInstrumentPrecision() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            Currency usd = mock(Currency.class);
            when(usd.getCurrencyType()).thenReturn(CurrencyType.FIAT);
            when(usd.getFractionalDigits()).thenReturn(2);
            when(usd.getSymbol()).thenReturn("$"); when(usd.getCode()).thenReturn("USD");
            assertEquals("$0.00123", new MoneyAxisFormatter(usd, 2).toString(new BigDecimal("0.00123")));
            assertEquals("-$0.00123", new MoneyAxisFormatter(usd, 2).toString(-0.00123));
            assertEquals("$1.23456", new MoneyAxisFormatter(usd, 5).toString(1.23456));
            assertEquals("$1,234.56", new MoneyAxisFormatter(usd, 2).toString(1234.56));
            assertEquals("$0.00000123", new MoneyAxisFormatter(usd, 2).toString(0.00000123));
            assertEquals("", new MoneyAxisFormatter(usd).toString(Double.NaN));
        } finally { Locale.setDefault(previous); }
    }
}
