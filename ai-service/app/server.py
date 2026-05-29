from __future__ import annotations

import logging
import sys
from concurrent import futures
from pathlib import Path

import grpc

# gRPC service method names must match proto RPC names (PascalCase).
# pylint: disable=invalid-name,unused-argument

PROJECT_ROOT = Path(__file__).resolve().parents[1]
APP_DIR = PROJECT_ROOT / "app"
GENERATED_DIR = PROJECT_ROOT / "app" / "generated"

for path in (PROJECT_ROOT, APP_DIR, GENERATED_DIR):
    if str(path) not in sys.path:
        sys.path.append(str(path))

from app.config import load_config
from app.models.model_registry import ModelRegistry
from app.services.anomaly_detector import AnomalyDetector
from app.services.backtest_reviewer import BacktestReviewer
from app.services.regime_detector import RegimeDetector
from app.services.risk_scorer import RiskScorer
from app.services.signal_analyzer import SignalAnalyzer
from app.services.strategy_ranker import StrategyRanker
from app.services.strategy_reviewer import StrategyReviewer
from app.utils.logging import configure_logging
from app.generated import investpro_ai_pb2 as pb2
from app.generated import investpro_ai_pb2_grpc as pb2_grpc


class InvestProAiServicer(pb2_grpc.InvestProAiServiceServicer):
    """Local advisory AI runtime. Never executes orders; only returns recommendations."""

    def __init__(self) :
        self.registry = ModelRegistry.create()
        self.signal_analyzer = SignalAnalyzer()
        self.regime_detector = RegimeDetector()
        self.strategy_reviewer = StrategyReviewer()
        self.strategy_ranker = StrategyRanker()
        self.backtest_reviewer = BacktestReviewer()
        self.risk_scorer = RiskScorer()
        self.anomaly_detector = AnomalyDetector()

    def Health(self, _request: pb2.HealthRequest, _context: grpc.ServicerContext) -> pb2.HealthResponse:  # noqa: N802
        return pb2.HealthResponse(
            ok=True,
            status="SERVING",
            version="1.0.0",
            timestamp_ms=self.registry.started_at_ms + self.registry.uptime_ms(),
            service_name="investpro-local-ai-grpc",
            uptime_ms=self.registry.uptime_ms(),
            active_models=self.registry.active_model_count(),
            avg_latency_ms=0.0,
            circuit_hint="ADVISORY_ONLY",
        )

    def AnalyzeSignal(self, request: pb2.SignalReviewRequest, _context: grpc.ServicerContext) -> pb2.SignalReviewResponse:  # noqa: N802
        result = self.signal_analyzer.analyze(request)
        return pb2.SignalReviewResponse(**result)

    def DetectRegime(self, request: pb2.RegimeDetectionRequest, _context: grpc.ServicerContext) -> pb2.RegimeDetectionResponse:  # noqa: N802
        result = self.regime_detector.detect(request)
        return pb2.RegimeDetectionResponse(**result)

    def ReviewStrategy(self, request: pb2.StrategyReviewRequest, _context: grpc.ServicerContext) -> pb2.StrategyReviewResponse:  # noqa: N802
        result = self.strategy_reviewer.review(request)
        return pb2.StrategyReviewResponse(**result)

    def RankStrategies(self, request: pb2.StrategyRankingRequest, _context: grpc.ServicerContext) -> pb2.StrategyRankingResponse:  # noqa: N802
        result = self.strategy_ranker.rank(request)
        ranked = [pb2.RankedStrategy(**item) for item in result["ranked"]]
        return pb2.StrategyRankingResponse(
            ranked=ranked,
            recommendation=result["recommendation"],
            warnings=result["warnings"],
        )

    def ReviewBacktest(self, request: pb2.BacktestReviewRequest, _context: grpc.ServicerContext) -> pb2.BacktestReviewResponse:  # noqa: N802
        result = self.backtest_reviewer.review(request)
        return pb2.BacktestReviewResponse(**result)

    def ScoreRisk(self, request: pb2.RiskScoreRequest, _context: grpc.ServicerContext) -> pb2.RiskScoreResponse:  # noqa: N802
        result = self.risk_scorer.score(request)
        return pb2.RiskScoreResponse(**result)

    def DetectAnomaly(self, request: pb2.AnomalyDetectionRequest, _context: grpc.ServicerContext) -> pb2.AnomalyDetectionResponse:  # noqa: N802
        result = self.anomaly_detector.detect(request)
        return pb2.AnomalyDetectionResponse(**result)


def serve() -> None:
    cfg = load_config()
    configure_logging(cfg.log_level)

    server = grpc.server(
        futures.ThreadPoolExecutor(max_workers=cfg.workers),
        options=[
            ("grpc.max_send_message_length", cfg.max_message_mb * 1024 * 1024),
            ("grpc.max_receive_message_length", cfg.max_message_mb * 1024 * 1024),
        ],
    )
    pb2_grpc.add_InvestProAiServiceServicer_to_server(InvestProAiServicer(), server)
    bound_port = _bind_with_fallback(server, cfg.host, cfg.port, cfg.port_scan_max)
    bind_addr = f"{cfg.host}:{bound_port}"
    server.start()

    logging.getLogger(__name__).info("InvestPro local AI gRPC server started on %s", bind_addr)
    server.wait_for_termination()


def _bind_with_fallback(server: grpc.Server, host: str, preferred_port: int, port_scan_max: int) -> int:
    logger = logging.getLogger(__name__)
    last_error: RuntimeError | None = None

    candidate_ports: list[int] = []
    if preferred_port > 0:
        scan = max(0, port_scan_max)
        candidate_ports.extend(preferred_port + offset for offset in range(scan + 1))
        candidate_ports.append(0)
    else:
        candidate_ports.append(0)

    for candidate in candidate_ports:
        bind_addr = f"{host}:{candidate}"
        try:
            bound_port = server.add_insecure_port(bind_addr)
        except RuntimeError as error:
            last_error = error
            continue

        if bound_port > 0:
            if preferred_port > 0 and bound_port != preferred_port:
                logger.warning(
                    "Preferred port %s unavailable, auto-adjusted to %s",
                    preferred_port,
                    bound_port,
                )
            return bound_port

    if last_error is not None:
        raise RuntimeError(
            f"Failed to bind gRPC server near preferred port {preferred_port} on host {host}"
        ) from last_error
    raise RuntimeError(f"Failed to bind gRPC server on host {host}")


if __name__ == "__main__":
    serve()
