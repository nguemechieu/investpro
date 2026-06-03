package org.investpro.exchange.blockchain.execution;

public class ArbitrumExecutionProvider extends AbstractUnsupportedEvmExecutionProvider {
    @Override
    public String networkId() {
        return "ARBITRUM";
    }
}
