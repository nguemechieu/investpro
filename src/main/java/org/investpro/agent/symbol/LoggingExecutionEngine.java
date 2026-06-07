package org.investpro.agent.symbol;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LoggingExecutionEngine implements SymbolExecutionEngine {

    @Override
    public void submitTradeIntent(TradeIntent intent, RiskDecision decision) {
        log.info("TradeIntent accepted by safe logging execution engine only. id={} symbol={} side={}",
                intent == null ? "" : intent.id(),
                intent == null || intent.pair() == null ? "" : intent.pair().toString('/'),
                intent == null ? "" : intent.side());
    }
}
