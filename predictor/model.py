from __future__ import annotations

import json
import math
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any


@dataclass(frozen=True)
class Prediction:
    label: str
    confidence: float
    probability_up: float
    probability_down: float


class HeuristicMarketPredictor:
    """Lightweight predictor used until a trained Python model is supplied."""

    name = "investpro-heuristic-predictor"
    version = "0.1.0"
    framework = "python-heuristic"

    def __init__(self, model_path: str | None = None) -> None:
        self.loaded_at = int(time.time())
        self.model_path: str | None = None
        self.weights: dict[str, float] = {
            "candle": 0.95,
            "rsi": 0.9,
            "macd": 0.75,
            "bollinger": 0.65,
            "stochastic": 0.45,
            "atr": 0.2,
        }
        if model_path:
            self.reload(model_path)

    def predict(self, market_data: Any) -> Prediction:
        open_price = _get_number(market_data, "open", 0.0)
        close_price = _get_number(market_data, "close", 0.0)
        high_price = _get_number(market_data, "high", max(open_price, close_price))
        low_price = _get_number(market_data, "low", min(open_price, close_price))
        volume = max(_get_number(market_data, "volume", 0.0), 0.0)

        rsi = _get_number(market_data, "rsi", 50.0)
        atr = max(_get_number(market_data, "atr", 0.0), 0.0)
        macd = _get_number(market_data, "macd", 0.0)

        stochastic = _get_number(market_data, "stoch", None)
        if stochastic is None:
            stochastic = _get_number(market_data, "stochastic", 50.0)

        bb_upper = _get_number(market_data, "bb_upper", 0.0)
        bb_lower = _get_number(market_data, "bb_lower", 0.0)

        price_range = max(high_price - low_price, abs(close_price) * 0.0001, 1e-9)
        candle_momentum = (close_price - open_price) / price_range
        candle_momentum = _clamp(candle_momentum, -1.0, 1.0)

        score = candle_momentum * self.weights["candle"]
        score += _bounded_indicator((50.0 - rsi) / 50.0) * self.weights["rsi"]
        score += math.tanh(macd) * self.weights["macd"]
        score += _bollinger_signal(close_price, bb_lower, bb_upper) * self.weights["bollinger"]
        score += _stochastic_signal(stochastic) * self.weights["stochastic"]

        if atr > 0 and close_price > 0:
            atr_ratio = min(atr / close_price, 0.2)
            score *= 1.0 - (atr_ratio * self.weights["atr"])

        if volume <= 0:
            score *= 0.85

        probability_up = 1.0 / (1.0 + math.exp(-score))
        probability_down = 1.0 - probability_up
        confidence = max(probability_up, probability_down)
        label = "BUY" if probability_up >= probability_down else "SELL"

        return Prediction(
            label=label,
            confidence=round(confidence, 4),
            probability_up=round(probability_up, 4),
            probability_down=round(probability_down, 4),
        )

    def reload(self, model_path: str) -> str:
        path = Path(model_path).expanduser().resolve()
        if not path.exists():
            raise FileNotFoundError(f"Model config not found: {path}")

        if path.suffix.lower() == ".json":
            data = json.loads(path.read_text(encoding="utf-8"))
            weights = data.get("weights", data)
            if not isinstance(weights, dict):
                raise ValueError("Model JSON must contain a weights object.")

            updated = self.weights.copy()
            for key, value in weights.items():
                if key in updated:
                    updated[key] = float(value)
            self.weights = updated

        self.model_path = str(path)
        self.loaded_at = int(time.time())
        return f"Loaded predictor configuration from {path}"


def _get_number(data: Any, key: str, default: float | None) -> float:
    value = _get_value(data, key, default)
    if value is None:
        if default is None:
            return 0.0
        return float(default)
    try:
        return float(value)
    except (TypeError, ValueError):
        if default is None:
            return 0.0
        return float(default)


def _get_value(data: Any, key: str, default: Any = None) -> Any:
    if isinstance(data, dict):
        return data.get(key, default)
    return getattr(data, key, default)


def _bounded_indicator(value: float) -> float:
    return _clamp(value, -1.0, 1.0)


def _bollinger_signal(close_price: float, bb_lower: float, bb_upper: float) -> float:
    if bb_lower <= 0 or bb_upper <= 0 or bb_upper <= bb_lower:
        return 0.0
    if close_price <= bb_lower:
        return 1.0
    if close_price >= bb_upper:
        return -1.0
    midpoint = (bb_upper + bb_lower) / 2.0
    half_width = max((bb_upper - bb_lower) / 2.0, 1e-9)
    return _clamp((midpoint - close_price) / half_width, -1.0, 1.0)


def _stochastic_signal(stochastic: float) -> float:
    if stochastic <= 20.0:
        return 1.0
    if stochastic >= 80.0:
        return -1.0
    return 0.0


def _clamp(value: float, low: float, high: float) -> float:
    return max(low, min(high, value))