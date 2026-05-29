from __future__ import annotations

from dataclasses import dataclass
from time import time

from app.models.baseline_models import BASELINE_MODELS


@dataclass
class ModelRegistry:
    started_at_ms: int

    @classmethod
    def create(cls) -> "ModelRegistry":
        return cls(started_at_ms=int(time() * 1000))

    def active_model_count(self) -> int:
        return len(BASELINE_MODELS)

    def uptime_ms(self) -> int:
        return int(time() * 1000) - self.started_at_ms

    def list_models(self) -> dict:
        return BASELINE_MODELS
