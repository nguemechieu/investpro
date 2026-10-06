# Auto Strategy Lab

> Current documentation baseline (2026-10-05): JDK 27, JavaFX 27 and Maven 3.8.5+. Use the [documentation index](../docs/README.md) for current setup, trading-desk behavior, Telegram commands and release limits. Design examples below are not proof of broker support or deployment verification.

Auto Strategy Lab is InvestPro's safe strategy-improvement pipeline. It creates normal
`StrategyDefinition` objects, evaluates them with the existing backtesting engine, records
the results, and can update the strategy assignment registry only after strict checks.

It never submits live orders.

## Package Layout

- `org.investpro.strategy.auto`: candidate generation, validation, mutation, evaluation,
  ranking, assignment decisions, scheduling, and persistent strategy memory.
- `org.investpro.ai.strategy`: AI strategy-generation request/result contracts, prompt
  builder, and safe JSON parser.
- `org.investpro.ai`: shared AI model catalog, credits, providers, disclaimers, and
  trade-review AI support.

## Safety Defaults

The default configuration keeps automatic live assignment disabled:

```properties
autoStrategy.enabled=true
autoStrategy.allowLiveAutoAssignment=false
autoStrategy.requirePaperBeforeLive=true
ai.strategyGeneration.enabled=false
ai.enabled=false
```

The Strategy Builder UI can generate and evaluate candidates without trading. Assigning the
best strategy updates the assignment registry only; execution still flows through the
normal risk, tradability, and order-routing gates.

## Delivered Phases

- Phase 1: rule-based candidate generation, validation, evaluation, ranking, and manual
  "Assign Best".
- Phase 2: scheduled automatic improvement, controlled strategy mutation, and persistent
  memory in `data/auto-strategy-memory.json`.
- Phase 3 foundation: optional AI-assisted strategy generation, disabled by default.

## Persistent Memory

`FileStrategyMemoryRepository` stores Auto Strategy Lab history as JSON:

- generated candidates
- backtest/evaluation results
- assignment decisions
- rejected candidates
- winning candidates

This keeps bot-created strategies separate from user-saved strategies while preserving
compatibility with `StrategyCatalog`, `StrategyRegistry`, `StrategyBacktestRunner`, and
Strategy Builder.
