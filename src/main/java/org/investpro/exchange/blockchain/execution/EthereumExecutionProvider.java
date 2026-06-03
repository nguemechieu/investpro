package org.investpro.exchange.blockchain.execution;

public class EthereumExecutionProvider extends AbstractUnsupportedEvmExecutionProvider {
    @Override
    public String networkId() {
        return "ETHEREUM";
    }
}
