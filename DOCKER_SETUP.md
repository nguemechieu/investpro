# Docker setup

Updated: 2026-10-05. The desktop image builds and runs with JDK 27 and Maven-managed
JavaFX 27 artifacts. Docker provides browser access to the desktop, not a web rewrite.

## Start from the repository root

```bash
docker compose up -d --build
docker compose ps
docker compose logs -f investpro-app
```

Use Docker with the Compose v2 plugin. The Compose file uses optional `env_file`
configuration; older Compose installations may not accept that syntax.

Open `http://localhost:6080/vnc.html?autoconnect=1&resize=scale` for noVNC.
Direct VNC is exposed on 5900. The file also publishes PostgreSQL on 5432 and port
8080; this does not establish that an application HTTP API is running on 8080.

## Actual services and storage

- `postgres`: PostgreSQL 16, `postgres_data` named volume, readiness health check.
- `investpro-app`: JavaFX desktop, Xvfb/Fluxbox, x11vnc/noVNC and supervisor in one container.
- `./data`, `./logs`, and `./output` are bind-mounted into the desktop container.
- There is no separate `novnc` Compose service and no Python AI service in this file.

The build-stage Maven image supplies Maven binaries; compilation uses the JDK 27
build stage. The runtime stage also uses JDK 27. Do not replace Maven-managed
JavaFX dependencies with an older OS JavaFX SDK.

The committed Compose file includes development database and VNC passwords and
publishes ports on the host. Review passwords, port bindings and access controls
before using it outside a local development machine. Database environment values
in the Compose `environment` block override values loaded from `.env`.

## Configure integrations

Compose optionally loads a root `.env`. The actual connector must recognize the
key you set; see [configuration](README.md#configuration) and
[Telegram remote desk](docs/telegram-remote-desk.md). Telegram and OpenAI settings
must be available to the Java process. PAPER remains local simulation even when
market data or broker authentication is available.

For the optional Python service, follow [AI service Docker instructions](ai-service/README.md#docker).
Its current Dockerfile needs an explicit entrypoint and bind-address override.
A Java container cannot reach a host or sibling service through its own localhost.

## Diagnose and stop

```bash
docker compose logs --tail=100 postgres
docker compose logs --tail=100 investpro-app
docker compose exec investpro-app java -version
docker compose down
```

Check service health, X11/noVNC startup and the desktop application logs separately.
`docker compose down` preserves the named database volume. `down -v` removes it
and should be used only when intentional data deletion is acceptable.

Docker runtime behavior was not tested during this documentation update. See
[release readiness](PRODUCTION_READY.md) and [release checklist](PRODUCTION_RELEASE_CHECKLIST.md).
