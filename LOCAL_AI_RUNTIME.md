# InvestPro Local AI Runtime

This document describes the local advisory runtime that runs alongside InvestPro and provides AI-assisted trade review through gRPC.

## Purpose

- Python is advisory only.
- Java remains the execution authority and final risk gate.
- The shared contract is protobuf-based so both runtimes evolve together.

## Runtime Layout

- Java client: `src/main/java/org/investpro/ai/local/grpc/LocalAiRuntimeService.java`
- Java proto generation: `src/main/proto/investpro_ai.proto`
- Python service: `ai-service/app/server.py`
- Python proto: `ai-service/proto/investpro_ai.proto`
- Default config: `src/main/resources/config.properties`

## Startup

1. Start the Python service from `ai-service/` or `ai-service/app/` alongside InvestPro launch.
2. Java connects to the configured gRPC host and port.
3. Java calls the advisory RPCs when it needs a signal review, strategy review, backtest review, or health check.
4. If the Python service is unavailable, Java switches to conservative fallback behavior.

## Supported Advisory RPCs

- `Health`
- `AnalyzeSignal`
- `DetectRegime`
- `ReviewStrategy`
- `RankStrategies`
- `ReviewBacktest`
- `ScoreRisk`
- `DetectAnomaly`

## Behavioral Rules

- The runtime can recommend approve, reject, wait, or manual review outcomes.
- The runtime does not place orders.
- The final execution decision stays in Java.
- Conservative mode is preferred over silent failure when the gRPC service is unhealthy.

## Related Docs

- [SEQUENCE_DIAGRAMS.md](SEQUENCE_DIAGRAMS.md)
- [README.md](README.md)
- [SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md)
- [ai-service/README.md](ai-service/README.md)
