# InvestPro Local AI Runtime (gRPC)

This service provides advisory AI capabilities for InvestPro over local gRPC.

Safety principle:
- Python AI recommends
- Java RiskEngine decides
- Java ExecutionEngine executes

The Python service does **not** place orders and cannot bypass Java execution flow.

## Features
- Signal review
- Regime detection
- Strategy review
- Strategy ranking
- Backtest review / overfit risk
- Risk scoring
- Anomaly detection
- Health reporting

## Run locally

1. Install dependencies:

```bash
pip install -r requirements.txt
```

2. Generate Python stubs:

```bash
python -m grpc_tools.protoc \
  -I proto \
  --python_out=app/generated \
  --grpc_python_out=app/generated \
  proto/investpro_ai.proto
```

3. Start server:

```bash
python app/server.py
```

The server adds the project root and generated stub directory to `sys.path`, so it can be run from `ai-service/app` in IDE consoles as well as from `ai-service/`.

Server binds to `127.0.0.1:8010` by default.

## Docker

```bash
docker build -t investpro-ai ./ai-service
docker run --rm -p 127.0.0.1:8010:8010 investpro-ai
```

## Test

```bash
pytest -q tests
```
