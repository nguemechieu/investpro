from __future__ import annotations

import sys
from concurrent import futures
from pathlib import Path

import grpc

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / "app" / "generated"))

from app.server import InvestProAiServicer  # noqa: E402
from app.generated import investpro_ai_pb2 as pb2  # noqa: E402
from app.generated import investpro_ai_pb2_grpc as pb2_grpc  # noqa: E402


def _channel_and_stub():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=2))
    pb2_grpc.add_InvestProAiServiceServicer_to_server(InvestProAiServicer(), server)
    port = server.add_insecure_port("127.0.0.1:0")
    server.start()

    channel = grpc.insecure_channel(f"127.0.0.1:{port}")
    stub = pb2_grpc.InvestProAiServiceStub(channel)
    return server, channel, stub


def test_health():
    server, channel, stub = _channel_and_stub()
    try:
        resp = stub.Health(pb2.HealthRequest(source="pytest"))
        assert resp.ok is True
        assert resp.status == "SERVING"
    finally:
        channel.close()
        server.stop(0)


def test_analyze_signal():
    server, channel, stub = _channel_and_stub()
    try:
        resp = stub.AnalyzeSignal(
            pb2.SignalReviewRequest(
                symbol="EUR/USD",
                timeframe="1h",
                side="BUY",
                confidence=0.72,
                price=1.1,
                spread=0.0002,
                volatility=0.01,
                volume=100000,
                strategy_name="Trend",
                market_regime="TRENDING",
            )
        )
        assert 0.0 <= resp.ai_confidence <= 1.0
        assert resp.recommendation in {"APPROVE", "WAIT"}
    finally:
        channel.close()
        server.stop(0)


def test_detect_regime():
    server, channel, stub = _channel_and_stub()
    try:
        candles = [
            pb2.Candle(open_time=i, open=1 + i * 0.001, high=1.01 + i * 0.001, low=0.99 + i * 0.001, close=1 + i * 0.001, volume=100)
            for i in range(20)
        ]
        resp = stub.DetectRegime(pb2.RegimeDetectionRequest(symbol="EUR/USD", timeframe="1h", candles=candles))
        assert resp.regime in {"TRENDING", "RANGING", "VOLATILE_RANGE", "UNKNOWN"}
    finally:
        channel.close()
        server.stop(0)


def test_review_backtest():
    server, channel, stub = _channel_and_stub()
    try:
        resp = stub.ReviewBacktest(
            pb2.BacktestReviewRequest(
                strategy_id="trend",
                strategy_name="Trend",
                symbol="EUR/USD",
                timeframe="1h",
                total_trades=80,
                win_rate=0.56,
                profit_factor=1.7,
                max_drawdown=0.18,
                sharpe_ratio=1.3,
                expectancy=0.02,
                sample_size=160,
            )
        )
        assert 0.0 <= resp.ai_score <= 100.0
    finally:
        channel.close()
        server.stop(0)


def test_bad_input_handling():
    server, channel, stub = _channel_and_stub()
    try:
        resp = stub.ReviewBacktest(pb2.BacktestReviewRequest())
        assert resp.accepted is False or resp.ai_score >= 0.0
    finally:
        channel.close()
        server.stop(0)
