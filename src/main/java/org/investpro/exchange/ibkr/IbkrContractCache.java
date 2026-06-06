package org.investpro.exchange.ibkr;

import org.investpro.models.trading.TradePair;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class IbkrContractCache {

    private final IbkrContractRepository repository;
    private final ConcurrentHashMap<String, IbkrResolvedContract> byUniqueKey = new ConcurrentHashMap<>();

    public IbkrContractCache(IbkrContractRepository repository) {
        this.repository = repository;
        load();
    }

    public void load() {
        byUniqueKey.clear();
        for (IbkrResolvedContract contract : repository.findAll()) {
            byUniqueKey.put(contract.uniqueKey(), contract);
        }
    }

    public List<IbkrResolvedContract> all() {
        return List.copyOf(byUniqueKey.values());
    }

    public IbkrResolvedContract put(IbkrResolvedContract contract) {
        IbkrResolvedContract saved = repository.save(contract);
        byUniqueKey.put(saved.uniqueKey(), saved);
        return saved;
    }

    public Optional<IbkrResolvedContract> findByTradePair(TradePair pair) {
        return repository.findByTradePair(pair);
    }

    public Optional<IbkrResolvedContract> findByDisplaySymbol(String symbol) {
        return repository.findByDisplaySymbol(symbol);
    }
}
