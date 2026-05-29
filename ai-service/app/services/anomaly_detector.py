from __future__ import annotations

from app.utils.validation import clamp, mean


class AnomalyDetector:
    def detect(self, req) -> dict:
        reasons: list[str] = []
        warnings: list[str] = []

        closes = [c.close for c in req.candles if c.close > 0]
        ranges = [(c.high - c.low) for c in req.candles if c.high > c.low]

        if len(closes) < 10:
            return {
                "anomalous": False,
                "anomaly_score": 0.1,
                "severity": "LOW",
                "reasons": ["Insufficient candles for anomaly evaluation"],
                "warnings": [],
            }

        mean_close = mean(closes, 1.0)
        mean_range = mean(ranges, 0.0)

        spread_component = clamp(req.spread * 80.0, 0.0, 1.0)
        volatility_component = clamp(req.volatility * 15.0, 0.0, 1.0)
        range_component = clamp((mean_range / max(mean_close, 1e-9)) * 20.0, 0.0, 1.0)
        volume_component = 0.0 if req.volume > 0 else 0.35

        score = clamp((spread_component + volatility_component + range_component + volume_component) / 3.0, 0.0, 1.0)
        anomalous = score >= 0.65

        severity = "LOW"
        if score >= 0.85:
            severity = "HIGH"
        elif score >= 0.65:
            severity = "MEDIUM"

        reasons.append(f"Anomaly score={score:.3f}")
        if anomalous:
            warnings.append("Market state appears abnormal")

        return {
            "anomalous": anomalous,
            "anomaly_score": score,
            "severity": severity,
            "reasons": reasons,
            "warnings": warnings,
        }
