from __future__ import annotations

from typing import Iterable


def clamp(value: float, low: float, high: float) -> float:
    return max(low, min(high, value))


def safe_div(numerator: float, denominator: float, default: float = 0.0) -> float:
    return default if denominator == 0 else numerator / denominator


def non_empty(text: str) -> bool:
    return bool(text and text.strip())


def mean(values: Iterable[float], default: float = 0.0) -> float:
    vals = list(values)
    return default if not vals else sum(vals) / len(vals)
