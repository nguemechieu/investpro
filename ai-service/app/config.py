from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class RuntimeConfig:
    host: str = os.getenv("AI_LOCAL_HOST", "127.0.0.1")
    port: int = int(os.getenv("AI_LOCAL_PORT", "8010"))
    workers: int = int(os.getenv("AI_LOCAL_WORKERS", "8"))
    max_message_mb: int = int(os.getenv("AI_LOCAL_MAX_MESSAGE_MB", "8"))
    log_level: str = os.getenv("AI_LOCAL_LOG_LEVEL", "INFO")


def load_config() -> RuntimeConfig:
    return RuntimeConfig()
