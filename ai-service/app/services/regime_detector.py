from __future__ import annotations

from app.utils.validation import clamp, mean, safe_div


class RegimeDetector:
    def detect(self, req) -> dict:
        closes = [c.close for c in req.candles if c.close > 0]
        highs = [c.high for c in req.candles if c.high > 0]
        lows = [c.low for c in req.candles if c.low > 0]

        if len(closes) < 10:
            return {
                "regime": "UNKNOWN",
                "confidence": 0.2,
                "volatility_state": "UNKNOWN",
                "trend_strength": 0.0,
                "reasons": ["Not enough candles"],
            }

        first = closes[0]
        last = closes[-1]
        trend_strength = safe_div(abs(last - first), max(first, 1e-9), 0.0)

        avg_range = mean((h - l for h, l in zip(highs, lows)), 0.0)
        avg_close = mean(closes, 1.0)
        rel_vol = safe_div(avg_range, avg_close, 0.0)

        if rel_vol > 0.03:
            volatility_state = "HIGH"
        elif rel_vol > 0.015:
            volatility_state = "MEDIUM"
        else:
            volatility_state = "LOW"

        if trend_strength > 0.04:
            regime = "TRENDING"
        elif rel_vol > 0.03:
            regime = "VOLATILE_RANGE"
        else:
            regime = "RANGING"

        confidence = clamp(0.5 + trend_strength * 4.0, 0.0, 0.95)
        return {
            "regime": regime,
            "confidence": confidence,
            "volatility_state": volatility_state,
            "trend_strength": clamp(trend_strength, 0.0, 1.0),
            "reasons": [
                f"Trend strength={trend_strength:.4f}",
                f"Relative volatility={rel_vol:.4f}",
            ],
        }
