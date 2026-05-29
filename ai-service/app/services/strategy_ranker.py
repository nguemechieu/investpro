from __future__ import annotations

from app.utils.validation import clamp


class StrategyRanker:
    def rank(self, req) -> dict:
        ranked = []
        warnings: list[str] = []

        for c in req.candidates:
            score = c.score
            score += clamp(c.win_rate, 0.0, 1.0) * 15.0
            score += clamp(c.profit_factor / 3.0, 0.0, 1.0) * 20.0
            score += clamp(1.0 - c.max_drawdown, 0.0, 1.0) * 10.0
            if req.market_regime and c.preferred_regime and req.market_regime.upper() == c.preferred_regime.upper():
                score += 10.0

            ranked.append({
                "strategy_id": c.strategy_id,
                "strategy_name": c.strategy_name,
                "ai_score": score,
                "rank_score": score,
                "reasons": [f"Rank score={score:.2f}"],
            })

        ranked.sort(key=lambda x: x["rank_score"], reverse=True)
        recommendation = ranked[0]["strategy_name"] if ranked else "NONE"
        if not ranked:
            warnings.append("No strategy candidates provided")

        return {
            "ranked": ranked,
            "recommendation": recommendation,
            "warnings": warnings,
        }
