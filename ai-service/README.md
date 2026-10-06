# InvestPro local AI service

Updated: 2026-10-05. Python 3.11 is the container baseline; the desktop uses JDK 27 and JavaFX 27.

This Python gRPC service provides rule-based advisory reviews. It does not place
orders, run an OpenAI chat session, or replace Java risk and execution decisions.
Telegram's OpenAI integration is separate: see [remote desk](../docs/telegram-remote-desk.md).

## Run locally

Run these commands from `ai-service/`. Create the generated directory before protoc.

```bash
python -m venv .venv
# Linux/macOS: source .venv/bin/activate
# Windows PowerShell: .\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
python -c "from pathlib import Path; Path('app/generated').mkdir(parents=True, exist_ok=True)"
python -m grpc_tools.protoc -I proto --python_out=app/generated --grpc_python_out=app/generated proto/investpro_ai.proto
python app/server.py
```

Regenerate stubs with the installed grpcio-tools version rather than relying on
checked-in generated files built by a different gRPC/protobuf version. The server
adds the project, app and generated directories to its import path.

## Service configuration

| Environment variable | Default | Meaning |
|---|---|---|
| `AI_LOCAL_HOST` | `127.0.0.1` | Server bind address |
| `AI_LOCAL_PORT` | `8010` | Preferred server port |
| `AI_LOCAL_PORT_SCAN_MAX` | `20` | Additional ports to try after the preferred port |
| `AI_LOCAL_WORKERS` | `8` | gRPC worker pool size |
| `AI_LOCAL_MAX_MESSAGE_MB` | `8` | Send and receive message limits |
| `AI_LOCAL_LOG_LEVEL` | `INFO` | Logging level |

If 8010 is occupied, the server tries 8011 through 8030, then an OS-assigned port.
Read the startup log for the actual address. Java does not automatically discover
that changed port. Even with scan count zero, an ephemeral fallback is possible.

Configure the Java process separately:

```text
AI_LOCAL_GRPC_ENABLED=true
AI_LOCAL_GRPC_HOST=127.0.0.1
AI_LOCAL_GRPC_PORT=8010
AI_LOCAL_GRPC_TIMEOUT_MS=1500
```

Java's AppConfig reads JVM properties, OS environment, then `.env`. These client
keys differ from the Python server's `AI_LOCAL_HOST` and `AI_LOCAL_PORT` keys.
The Java launcher does not automatically start this Python process.

## RPCs and limitations

Implemented unary RPCs: `Health`, `AnalyzeSignal`, `DetectRegime`, `ReviewStrategy`,
`RankStrategies`, `ReviewBacktest`, `ScoreRisk`, and `DetectAnomaly`.

`StreamSignals` and `StreamMarketState` are declared extension points in the proto
but are not implemented by the Python servicer. They return UNIMPLEMENTED.
The model registry describes seven rule-based baselines, not trained models.
`Health.avg_latency_ms` is currently a placeholder zero, not a measured latency.
The transport is insecure gRPC without service authentication; use loopback or a
trusted private network, not a public listening endpoint.

## Docker

From the repository root:

```bash
docker build -t investpro-ai ./ai-service
docker run --rm -e AI_LOCAL_HOST=0.0.0.0 -p 127.0.0.1:8010:8010 --entrypoint /bin/sh investpro-ai -c 'python -m grpc_tools.protoc -I proto --python_out=app/generated --grpc_python_out=app/generated proto/investpro_ai.proto && python app/server.py'
```

The explicit entrypoint is a workaround for the current Dockerfile's mixed JSON
CMD and shell `&&` syntax. Its default loopback bind also prevents published-port
access, hence the explicit bind override above. This guide does not claim the
image's default command works unchanged. Docker execution has not been verified
in this documentation update. If the server falls back to a different port, the
8010 port mapping will not reach it.

The root Compose file does not include this AI service. If Java runs in another
container, localhost refers to that container; provide a reachable service address
and configure `AI_LOCAL_GRPC_HOST` accordingly.

## Tests and contracts

```bash
# From ai-service/
python -m pytest -q tests
```

Python and Java maintain separate proto copies:
`ai-service/proto/investpro_ai.proto` and `src/main/proto/investpro_ai.proto`.
Keep both synchronized and regenerate both sets of stubs when changing the contract.
Maven generates the Java sources during the normal build lifecycle.

See [local AI integration](../LOCAL_AI_RUNTIME.md), [developer guide](../DEVELOPER_GUIDE.md),
and the [documentation index](../docs/README.md).
