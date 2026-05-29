from __future__ import annotations

import logging
import sys
from concurrent import futures
from pathlib import Path

import grpc

PROJECT_ROOT = Path(__file__).resolve().parents[1]
GENERATED_DIR = PROJECT_ROOT / "app" / "generated"

for path in (PROJECT_ROOT, GENERATED_DIR):
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

    def Health(self, request: pb2.HealthRequest, context: grpc.ServicerContext) -> pb2.HealthResponse:
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

    def AnalyzeSignal(self, request: pb2.SignalReviewRequest, context: grpc.ServicerContext) -> pb2.SignalReviewResponse:
        result = self.signal_analyzer.analyze(request)
        return pb2.SignalReviewResponse(**result)

    def DetectRegime(self, request: pb2.RegimeDetectionRequest, context: grpc.ServicerContext) -> pb2.RegimeDetectionResponse:
        result = self.regime_detector.detect(request)
        return pb2.RegimeDetectionResponse(**result)

    def ReviewStrategy(self, request: pb2.StrategyReviewRequest, context: grpc.ServicerContext) -> pb2.StrategyReviewResponse:
        result = self.strategy_reviewer.review(request)
        return pb2.StrategyReviewResponse(**result)

    def RankStrategies(self, request: pb2.StrategyRankingRequest, context: grpc.ServicerContext) -> pb2.StrategyRankingResponse:
        result = self.strategy_ranker.rank(request)
        ranked = [pb2.RankedStrategy(**item) for item in result["ranked"]]
        return pb2.StrategyRankingResponse(
            ranked=ranked,
            recommendation=result["recommendation"],
            warnings=result["warnings"],
        )

    def ReviewBacktest(self, request: pb2.BacktestReviewRequest, context: grpc.ServicerContext) -> pb2.BacktestReviewResponse:
        result = self.backtest_reviewer.review(request)
        return pb2.BacktestReviewResponse(**result)

    def ScoreRisk(self, request: pb2.RiskScoreRequest, context: grpc.ServicerContext) -> pb2.RiskScoreResponse:
        result = self.risk_scorer.score(request)
        return pb2.RiskScoreResponse(**result)

    def DetectAnomaly(self, request: pb2.AnomalyDetectionRequest, context: grpc.ServicerContext) -> pb2.AnomalyDetectionResponse:
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
    bind_addr = f"{cfg.host}:{cfg.port}"
    server.add_insecure_port(bind_addr)
    server.start()

    logging.getLogger(__name__).info("InvestPro local AI gRPC server started on %s", bind_addr)
    server.wait_for_termination()


if __name__ == "__main__":
    serve()
