from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class BaselineModelInfo:
    name: str
    version: str
    kind: str


BASELINE_MODELS = {
    "signal_review": BaselineModelInfo("rule-signal-review", "1.0.0", "rule-based"),
    "regime_detection": BaselineModelInfo("rule-regime-detection", "1.0.0", "rule-based"),
    "strategy_review": BaselineModelInfo("rule-strategy-review", "1.0.0", "rule-based"),
    "strategy_ranking": BaselineModelInfo("rule-strategy-ranking", "1.0.0", "rule-based"),
    "backtest_review": BaselineModelInfo("rule-backtest-review", "1.0.0", "rule-based"),
    "risk_score": BaselineModelInfo("rule-risk-score", "1.0.0", "rule-based"),
    "anomaly_detection": BaselineModelInfo("rule-anomaly-detection", "1.0.0", "rule-based"),
}
