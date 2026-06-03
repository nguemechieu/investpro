package org.investpro.exchange.blockchain.execution;

public class PolygonExecutionProvider extends AbstractUnsupportedEvmExecutionProvider {
    @Override
    public String networkId() {
        return "POLYGON";
    }
}
