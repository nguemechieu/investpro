from __future__ import annotations

from app.utils.validation import clamp


class RiskScorer:
    def score(self, req) -> dict:
        reasons: list[str] = []

        risk = 0.0
        risk += clamp(req.current_exposure / max(req.account_equity, 1e-9), 0.0, 1.0) * 35.0
        risk += clamp(req.volatility * 10.0, 0.0, 1.0) * 25.0
        risk += clamp(req.spread * 50.0, 0.0, 1.0) * 15.0
        risk += clamp(req.drawdown / 0.4, 0.0, 1.0) * 15.0
        risk += clamp((1.0 - req.confidence), 0.0, 1.0) * 10.0

        risk_score = clamp(risk, 0.0, 100.0)
        max_position_size = clamp(1.0 - risk_score / 100.0, 0.05, 1.0)

        if risk_score > 75:
            recommendation = "REDUCE_SIZE_AND_WAIT"
        elif risk_score > 55:
            recommendation = "REDUCE_SIZE"
        else:
            recommendation = "NORMAL"

        reasons.append(f"Computed risk score={risk_score:.2f}")
        return {
            "risk_score": risk_score,
            "recommendation": recommendation,
            "max_position_size": max_position_size,
            "reasons": reasons,
        }
