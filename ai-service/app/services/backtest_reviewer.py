from __future__ import annotations

from app.utils.validation import clamp


class BacktestReviewer:
    def review(self, req) -> dict:
        warnings: list[str] = []
        rejection_reasons: list[str] = []

        overfit_risk = 0.0
        if req.sample_size < 50:
            overfit_risk += 0.35
            warnings.append("Low sample size increases overfit risk")
        if req.total_trades < 20:
            overfit_risk += 0.25
            warnings.append("Low trade count limits statistical confidence")
        if req.max_drawdown > 0.30:
            overfit_risk += 0.20
            warnings.append("Large drawdown profile")
        if req.profit_factor > 4.0 and req.sample_size < 100:
            overfit_risk += 0.25
            warnings.append("Unusually high profit factor on small sample")

        overfit_risk = clamp(overfit_risk, 0.0, 1.0)

        quality = 0.0
        quality += clamp(req.win_rate, 0.0, 1.0) * 30.0
        quality += clamp(req.profit_factor / 3.0, 0.0, 1.0) * 25.0
        quality += clamp(1.0 - req.max_drawdown, 0.0, 1.0) * 20.0
        quality += clamp((req.sharpe_ratio + 1.0) / 3.0, 0.0, 1.0) * 15.0
        quality += clamp(req.sample_size / 300.0, 0.0, 1.0) * 10.0

        ai_score = clamp(quality * (1.0 - 0.5 * overfit_risk), 0.0, 100.0)
        accepted = ai_score >= 55.0 and overfit_risk < 0.7

        if not accepted:
            rejection_reasons.append("Backtest quality below acceptance threshold")
        if overfit_risk >= 0.7:
            rejection_reasons.append("Overfitting risk too high")

        return {
            "accepted": accepted,
            "ai_score": ai_score,
            "overfit_risk": overfit_risk,
            "warnings": warnings,
            "rejection_reasons": rejection_reasons,
        }
