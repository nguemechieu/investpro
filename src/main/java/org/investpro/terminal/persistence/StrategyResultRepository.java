package org.investpro.terminal.persistence;

import org.investpro.terminal.domain.BacktestResult;

import java.util.List;
import java.util.Optional;

public interface StrategyResultRepository {
    BacktestResult save(BacktestResult result);
    Optional<BacktestResult> findById(String resultId);
    List<BacktestResult> findByStrategyId(String strategyId);
}
