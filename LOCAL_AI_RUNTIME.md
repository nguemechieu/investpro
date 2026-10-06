# Local AI integration

Updated: 2026-10-05. Detailed server setup is maintained in the
[AI service README](ai-service/README.md).

Python supplies rule-based advisory reviews. Java owns risk decisions and execution.
This service is independent of Telegram's OpenAI conversation integration.

## Contract and ownership

- Java client: `src/main/java/org/investpro/ai/local/grpc/PythonAiGrpcClient.java`.
- Java advisory service: `LocalAiRuntimeService.java` in the same package.
- Java proto: `src/main/proto/investpro_ai.proto`; Maven generates client sources.
- Python server: `ai-service/app/server.py`.
- Python proto: `ai-service/proto/investpro_ai.proto`; regenerate Python stubs after changes.

Start Python separately; the Java launcher does not start it automatically.
Set Java environment/JVM configuration to `AI_LOCAL_GRPC_ENABLED=true`,
`AI_LOCAL_GRPC_HOST=127.0.0.1`, `AI_LOCAL_GRPC_PORT=8010`, and
`AI_LOCAL_GRPC_TIMEOUT_MS=1500`. Do not use the obsolete dotted `ai.local.grpc.*`
examples for these AppConfig keys.

Python uses `AI_LOCAL_HOST` and `AI_LOCAL_PORT` instead. If its preferred port is
busy, it scans nearby ports then falls back to an ephemeral port. Read its startup
log and update the Java client's port; no discovery mechanism synchronizes them.

## Behavior and limits

Eight unary RPCs are implemented: Health, AnalyzeSignal, DetectRegime,
ReviewStrategy, RankStrategies, ReviewBacktest, ScoreRisk and DetectAnomaly.
Streaming proto methods are extension points and are not implemented in Python.

The Java adapter uses a circuit breaker and a local reasoning fallback when a
request fails. A fallback is not proof of a healthy Python service or an approval
to trade. Final trading gates still apply. The gRPC transport is plaintext and
unauthenticated; restrict it to loopback or a trusted private network.

The implementation's `isGrpcAdvisoryEnabled()` currently returns the negation of
its configuration flag. Do not infer enabled-state semantics from that method's
name; verify review/backtest behavior when testing the integration. This is an
identified implementation concern, not a documentation claim that it is fixed.

See [release checklist](PRODUCTION_RELEASE_CHECKLIST.md) and
[documentation index](docs/README.md).
