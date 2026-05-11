# InvestPro Docker Usage Guide

## Overview

InvestPro is containerized with Docker Compose, providing a complete desktop environment with a JavaFX GUI accessible via web-based VNC (Virtual Network Computing). This guide covers setup, deployment, and usage.

## Prerequisites

- **Docker Desktop** (Windows/Mac) or **Docker Engine + Docker Compose** (Linux)
- **VNC Client** (optional, for native client access) - web interface included
- **Browser** for web-based VNC access
- Minimum **2GB free RAM** and **10GB disk space**

## Quick Start

### 1. Clone and Navigate

```bash
git clone <repository-url>
cd investpro
```

### 2. Start Services

```bash
docker-compose up -d
```

This command:
- Builds the application image (multi-stage Maven build)
- Launches PostgreSQL 16 database
- Starts the InvestPro JavaFX application with X11 desktop environment
- Exposes VNC and web services

### 3. Access the Application

**Web Browser (Recommended):**
```
http://localhost:6080/vnc.html?autoconnect=1&resize=scale
```

**Native VNC Client:**
```
localhost:5900
Password: investpro
```

### 4. Stop Services

```bash
docker-compose down
```

To remove persistent data (database):
```bash
docker-compose down -v
```

## VNC Access Details

### Web-Based VNC (noVNC)

- **URL:** http://localhost:6080/vnc.html
- **Port:** 6080 (websockify bridge)
- **Features:**
  - No installation required
  - Auto-connect available
  - Responsive scaling
  - Works in all modern browsers

**Example Screen:**

![InvestPro Docker VNC Screen](src/main/resources/images/investpro_docker_vnc_screen.png)

### Native VNC Client

- **Host:** localhost
- **Port:** 5900
- **Password:** investpro
- **Recommended Clients:**
  - RealVNC Viewer
  - TightVNC
  - UltraVNC (Windows)
  - Remmina (Linux)
  - Screen Sharing (macOS)

## Service Architecture

### Docker Compose Services

#### 1. **investpro-postgres** (Database)
- **Image:** postgres:16-alpine
- **Port:** 5432
- **Credentials:**
  - Username: investpro
  - Password: investpro123
  - Database: investpro
- **Health Check:** pg_isready every 10 seconds
- **Volumes:** postgres_data (persistent storage)

#### 2. **investpro-app** (Application)
- **Image:** investpro-investpro-app:latest (built from Dockerfile)
- **Ports:**
  - 8080: Application server
  - 5900: VNC server
  - 6080: Web VNC (noVNC)
  - 5432: PostgreSQL client access
- **Desktop Environment:**
  - Xvfb (X11 virtual display)
  - Fluxbox (lightweight window manager)
  - x11vnc (VNC server)
  - websockify + noVNC (web bridge)

## Configuration

### Environment Variables (docker-compose.yml)

Key variables you can customize:

```yaml
environment:
  # Database
  DB_HOST: investpro-postgres
  DB_PORT: 5432
  DB_NAME: investpro
  DB_USER: investpro
  DB_PASSWORD: investpro123
  
  # Display
  DISPLAY: :0
  GEOMETRY: 1530x840        # Resolution: width x height
  DEPTH: 24                 # Color depth in bits
  
  # VNC
  VNC_PASSWORD: investpro   # Set at runtime (not in image)
  
  # Java
  JAVA_OPTS: "-Xmx2g -Xms512m -Djava.awt.headless=false -Dprism.order=sw -Dprism.verbose=false"
  
  # Exchange APIs
  OANDA_API_KEY: <your-key>
  COINBASE_API_KEY: <your-key>
  COINBASE_API_SECRET: <your-secret>
  DUKASCOPY_USERNAME: <your-username>
  DUKASCOPY_PASSWORD: <your-password>
```

### Custom VNC Password

Change the VNC password in `docker-compose.yml`:

```yaml
investpro-app:
  environment:
    VNC_PASSWORD: your-custom-password
```

Then restart:
```bash
docker-compose down
docker-compose up -d
```

### Display Resolution

Adjust resolution in `docker-compose.yml`:

```yaml
environment:
  GEOMETRY: "1920x1080"     # Change to desired resolution
```

## Building Custom Image

To rebuild the Docker image (e.g., after code changes):

```bash
docker-compose down
./mvnw clean package -DskipTests    # Optional: rebuild locally first
docker-compose build --no-cache
docker-compose up -d
```

## Troubleshooting

### 1. VNC Connection Refused

**Issue:** Cannot connect to localhost:5900

**Solution:**
```bash
docker-compose logs investpro-app | grep "x11vnc"
docker-compose restart investpro-app
```

### 2. Application Won't Start

Check logs:
```bash
docker-compose logs investpro-app -f
```

Common causes:
- Database not ready: Check postgres health with `docker-compose ps`
- Insufficient memory: Increase Docker Desktop memory allocation
- Port conflicts: Check if ports 5900, 6080, 8080 are available

### 3. Database Connection Error

Verify database is healthy:
```bash
docker-compose ps
```

Wait for postgres to be healthy, then restart app:
```bash
docker-compose restart investpro-app
```

### 4. Text Input Not Working in VNC

**Solution:** Click on input field, then try typing. If still not working:
- Try different VNC client
- Refresh browser and reconnect
- Check container logs for JavaFX errors

### 5. Display/Resolution Issues

Adjust geometry in docker-compose.yml and restart:
```bash
docker-compose down
# Edit docker-compose.yml - change GEOMETRY
docker-compose up -d
```

## Volume Management

### Persistent Data

- **postgres_data:** PostgreSQL database files (created automatically)
- **./logs:** Application logs mounted to `/app/logs` in container
- **./data:** Historical market data mounted to `/app/data` in container

### Backup Database

```bash
docker-compose exec investpro-postgres pg_dump -U investpro investpro > backup.sql
```

### Restore Database

```bash
docker-compose exec -T investpro-postgres psql -U investpro investpro < backup.sql
```

## Performance Tuning

### Memory Configuration

In `docker-compose.yml`, increase Java heap:

```yaml
JAVA_OPTS: "-Xmx4g -Xms1g"    # Increase -Xmx value for more memory
```

### CPU Limits

Add resource limits in `docker-compose.yml`:

```yaml
services:
  investpro-app:
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 4G
        reservations:
          cpus: '1'
          memory: 2G
```

## Security Best Practices

### 1. Change VNC Password

Don't use default "investpro" in production. Set unique password:

```bash
# Edit docker-compose.yml
VNC_PASSWORD: your-strong-password-here
```

### 2. Secure API Credentials

Store exchange API keys securely:
- Use Docker secrets (production) or
- Set environment variables at runtime:

```bash
docker-compose -f docker-compose.yml up -e OANDA_API_KEY=your_key
```

### 3. Network Security

For production deployments:
- Don't expose VNC/noVNC ports publicly
- Use VPN or SSH tunnel for remote access
- Implement reverse proxy with authentication

Example SSH tunnel (Linux/Mac):
```bash
ssh -L 6080:localhost:6080 user@remote-server
```

### 4. Database Access

PostgreSQL only accessible from app container on internal network. External access requires explicit port mapping (not recommended).

## Deployment Examples

### Development (Local)

```bash
docker-compose up -d
```

### Production (with Resource Limits)

```bash
docker-compose -f docker-compose.yml up -d --scale investpro-app=1
```

### Remote Access (via SSH Tunnel)

```bash
# On remote server
docker-compose up -d

# On local machine
ssh -L 6080:localhost:6080 -L 8080:localhost:8080 user@remote-server
# Then access: http://localhost:6080/vnc.html
```

## File Structure

```
investpro/
├── Dockerfile              # Multi-stage Java/JavaFX build
├── docker-compose.yml      # Container orchestration
├── docker-start.sh         # Container entry script
├── pom.xml                 # Maven configuration
├── src/
│   └── main/
│       ├── java/           # Java source code
│       └── resources/       # JavaFX FXML, CSS, images
├── logs/                   # Application logs (volume mounted)
└── data/                   # Historical market data (volume mounted)
```

## Application Access

Once containers are running and VNC is connected:

### 1. **Login**
   - Default credentials for testing
   - Create new account if needed

### 2. **Configure Exchanges**
   - OANDA (Forex)
   - Coinbase (Crypto)
   - Dukascopy (Forex)
   - Binance (Crypto)

### 3. **Select Market**
   - Choose symbol and timeframe
   - Configure strategy parameters

### 4. **Start Trading**
   - View live market data
   - Execute trades via configured exchange

## Logs and Debugging

View application logs:
```bash
docker-compose logs investpro-app -f
```

View database logs:
```bash
docker-compose logs investpro-postgres
```

View X11 startup logs:
```bash
docker-compose exec investpro-app tail -f /app/logs/investpro.log
```

## Updates and Maintenance

### Rebuild After Code Changes

```bash
git pull origin main
docker-compose down
./mvnw clean package -DskipTests
docker-compose build --no-cache
docker-compose up -d
```

### Check Versions

```bash
docker-compose exec investpro-app java -version
docker-compose exec investpro-postgres postgres --version
```

## Support and Issues

For problems with:
- **Docker setup:** See Troubleshooting section above
- **Application functionality:** Check logs with `docker-compose logs`
- **Database issues:** Verify postgres health and connectivity
- **VNC/display:** Try different VNC client or browser

## Additional Resources

- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [VNC Documentation](https://en.wikipedia.org/wiki/Virtual_Network_Computing)
- [JavaFX Desktop Deployment](https://openjfx.io/openjfx-docs/)
- [PostgreSQL Docker Hub](https://hub.docker.com/_/postgres)

---

**Last Updated:** May 11, 2026
**Docker Image:** investpro-investpro-app:latest
**Java Version:** 21 (Eclipse Temurin)
**JavaFX Version:** 21.0.6
