package org.investpro.strategy.json;

import org.investpro.strategy.context.SignalContext;
import org.investpro.strategy.model.SignalAction;
import org.investpro.strategy.model.TradingSignal;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonRuleSignalProviderTest {
    @Test
    void evaluatesSimpleAndOrRulesSafely() {
        JsonSignalDefinition definition = new JsonSignalDefinition(
                "SIGNAL",
                "rsi-json",
                "RSI JSON",
                "1.0.0",
                "Test",
                "",
                1.2,
                List.of("RSI_14"),
                List.of("5m"),
                List.of(new JsonSignalRule("RSI_14 < 30 AND ATR > 1", SignalAction.BUY, 0.7, "oversold"))
        );
        JsonRuleSignalProvider provider = new JsonRuleSignalProvider(definition);
        SignalContext context = new SignalContext(null, "test", "SPOT", "5m", null, null, List.of(), null,
                Map.of("RSI_14", 25.0, "ATR", 2.0), Map.of(), false, Instant.now());

        TradingSignal signal = provider.evaluate(context);

        assertThat(signal.action()).isEqualTo(SignalAction.BUY);
        assertThat(signal.confidence()).isEqualTo(0.7);
        assertThat(signal.weight()).isEqualTo(1.2);
    }
}
