from __future__ import annotations

from app.utils.validation import clamp


class StrategyReviewer:
    def review(self, req) -> dict:
        reasons: list[str] = []
        warnings: list[str] = []

        score = 0.0
        score += clamp(req.historical_win_rate, 0.0, 1.0) * 35.0
        score += clamp(req.historical_profit_factor / 3.0, 0.0, 1.0) * 30.0
        score += clamp(1.0 - req.historical_max_drawdown, 0.0, 1.0) * 20.0
        score += clamp(req.sample_size / 200.0, 0.0, 1.0) * 15.0

        accepted = score >= 55.0
        if req.sample_size < 30:
            warnings.append("Sample size is small for robust validation")
        if req.historical_max_drawdown > 0.3:
            warnings.append("Large historical drawdown")

        reasons.append(f"Composite strategy quality score={score:.2f}")
        recommendation = "PROMOTE" if accepted else "REVIEW_REQUIRED"
        return {
            "accepted": accepted,
            "ai_score": score,
            "recommendation": recommendation,
            "reasons": reasons,
            "warnings": warnings,
        }
