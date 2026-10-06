# Docker usage guide

Updated: 2026-10-05. [Docker setup](DOCKER_SETUP.md) is the maintained service,
configuration, storage and troubleshooting reference.

```bash
# From the repository root
docker compose up -d --build
docker compose ps
docker compose logs -f investpro-app
# Stop without removing the database volume
docker compose down
```

Open `http://localhost:6080/vnc.html?autoconnect=1&resize=scale`.
The desktop uses JDK 27/JavaFX 27. Root Compose starts `postgres` and
`investpro-app`; noVNC runs inside the app container. The optional Python AI
service has separate [startup instructions](ai-service/README.md).

The committed passwords and published ports are development settings. Verify the
runtime and review configuration before deployment. See [release readiness](PRODUCTION_READY.md).
