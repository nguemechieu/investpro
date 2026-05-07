# Docker Setup Guide for InvestPro

## Overview

The corrected Docker configuration includes:

1. **PostgreSQL Database** - Replaces MySQL for better reliability and PostgreSQL-specific features
2. **noVNC Web Interface** - Access the application GUI through a web browser
3. **Supervisor** - Manages X11, VNC server, and Java application in a coordinated manner
4. **Multi-stage Build** - Efficient Docker image with Maven compilation in build stage

## Prerequisites

- Docker and Docker Compose installed (version 3.8+)
- At least 4GB RAM available for containers
- Port 6080 (noVNC web), 5900 (VNC), 8080 (app), and 5432 (PostgreSQL) available

## Quick Start

### Building and Starting Services

```bash
# Navigate to project directory
cd c:\Users\nguem\Documents\GitHub\investpro

# Build and start all services (PostgreSQL, noVNC, InvestPro app)
docker-compose up --build

# Or run in detached mode (background)
docker-compose up -d --build

# View logs
docker-compose logs -f investpro-app

# View logs for specific service
docker-compose logs -f postgres
docker-compose logs -f novnc
```

### Accessing the Application

**Option 1: Web-based noVNC Viewer (Recommended)**
- Open your browser and navigate to: `http://localhost:6080`
- The InvestPro application GUI will be displayed in the browser
- No VNC client software needed

**Option 2: Direct VNC Client**
- Use any VNC client (e.g., RealVNC, TightVNC, VNC Viewer)
- Connect to: `localhost:5900`
- Password: `investpro`

**Option 3: Application API (if applicable)**
- API endpoint: `http://localhost:8080`

## Services Architecture

### PostgreSQL Service
- **Container**: `investpro-postgres`
- **Port**: `5432`
- **Database**: `investpro`
- **User**: `investpro`
- **Password**: `investpro123`
- **Data Persistence**: `postgres_data` volume (survives container restart)
- **Healthcheck**: Automatically verifies database availability

### InvestPro App Service
- **Container**: `investpro-app`
- **Ports**: 
  - `8080` - Application API
  - `5900` - VNC server
  - `6080` - noVNC web interface
- **Display Server**: Xvfb (virtual X11 display :0)
- **VNC Server**: x11vnc (accessible on port 5900)
- **Supervisor**: Manages 4 processes:
  1. Xvfb - Virtual X11 display
  2. x11vnc - VNC server for the display
  3. noVNC - Web-based VNC viewer proxy
  4. Java App - InvestPro application

### noVNC Service
- **Container**: `investpro-novnc`
- **Port**: `6080`
- **Purpose**: Web-based VNC viewer to access application GUI
- **No password required** when accessing through this service (proxy handles it)

## Database Configuration

### PostgreSQL Connection String
```
jdbc:postgresql://postgres:5432/investpro
```

### Default Credentials
- **Host**: postgres (Docker service name)
- **Port**: 5432
- **Database**: investpro
- **User**: investpro
- **Password**: investpro123

### Modifying Database Settings

Edit `.env.docker` file before running `docker-compose up`:

```bash
POSTGRES_DB=investpro
POSTGRES_USER=investpro
POSTGRES_PASSWORD=investpro123
```

Then restart the services:
```bash
docker-compose down
docker-compose up -d --build
```

## Common Commands

### View Running Containers
```bash
docker-compose ps
```

### View Container Logs
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f investpro-app
docker-compose logs -f postgres
docker-compose logs -f novnc
```

### Enter Container Shell
```bash
# InvestPro app container
docker exec -it investpro-app bash

# PostgreSQL container
docker exec -it investpro-postgres psql -U investpro -d investpro
```

### Stop Services
```bash
# Stop all services (but keep data)
docker-compose stop

# Stop and remove containers (keeps volumes)
docker-compose down

# Stop, remove, and delete all data
docker-compose down -v
```

### Restart Services
```bash
docker-compose restart

# Or specific service
docker-compose restart investpro-app
docker-compose restart postgres
```

### Rebuild Docker Image
```bash
docker-compose build --no-cache
```

### View Supervisor Status (inside app container)
```bash
docker exec investpro-app supervisorctl status
```

## Troubleshooting

### Application won't start or crashes
```bash
# Check logs
docker-compose logs investpro-app

# Ensure PostgreSQL is healthy
docker-compose ps postgres

# Check if database is accessible
docker exec investpro-postgres psql -U investpro -d investpro -c "SELECT 1"
```

### Can't connect to noVNC web interface
- Verify port 6080 is accessible: `http://localhost:6080`
- Check container status: `docker-compose ps novnc`
- View logs: `docker-compose logs novnc`

### VNC client connection refused
- Ensure port 5900 is listening: `netstat -an | findstr 5900`
- Verify x11vnc is running: `docker-compose logs investpro-app | grep x11vnc`

### PostgreSQL connection errors
- Check database is healthy: `docker-compose ps postgres`
- Verify credentials in `.env.docker` match application settings
- Test connection from app container:
  ```bash
  docker exec investpro-app psql -h postgres -U investpro -d investpro -c "SELECT 1"
  ```

### Out of memory errors
- Increase Java heap: Edit `JAVA_OPTS` in `.env.docker`
- Example: `JAVA_OPTS=-Xmx4g -Xms1g`
- Rebuild and restart: `docker-compose up -d --build`

## Performance Tuning

### Java Heap Memory
Edit `.env.docker`:
```bash
JAVA_OPTS=-Xmx4g -Xms2g -Djava.awt.headless=false
```

### Display Resolution
Edit `.env.docker`:
```bash
GEOMETRY=1920x1080  # Default is 1280x1024
DEPTH=32            # Default is 24-bit color
```

### PostgreSQL Memory
Edit `docker-compose.yml` postgres service:
```yaml
environment:
  POSTGRES_INITDB_ARGS: "--encoding=UTF8 -c shared_buffers=256MB -c max_connections=100"
```

## Volumes and Persistence

### Application Logs
- Location inside container: `/app/logs`
- Mounted to host: `./logs` (project directory)
- Persists after container restart

### PostgreSQL Data
- Volume: `postgres_data`
- Automatically managed by Docker
- Persists after container restart
- To backup: `docker-compose exec postgres pg_dump -U investpro investpro > backup.sql`

## Files Modified/Created

1. **Dockerfile** - Updated to include:
   - PostgreSQL JDBC driver support
   - X11, VNC server, and noVNC packages
   - Supervisor for process management
   - Proper environment variables for PostgreSQL

2. **docker-compose.yml** - Complete rewrite with:
   - PostgreSQL service definition
   - InvestPro app service with GUI support
   - noVNC web interface service
   - Proper networking and volume management
   - Health checks for database

3. **supervisord.conf** - New file managing:
   - Xvfb (virtual X11 display)
   - x11vnc (VNC server)
   - noVNC (web VNC viewer)
   - Java application process

4. **.env.docker** - Environment configuration:
   - Database credentials
   - Display settings
   - Java options
   - Container names

## Migration from MySQL

If you were using MySQL before:
- Old connection: `jdbc:mysql://mysql-host:3306/db`
- New connection: `jdbc:postgresql://postgres:5432/investpro`

**Action required**: 
- Ensure application code uses PostgreSQL JDBC driver
- Migrate any existing MySQL data to PostgreSQL (see database migration guide)
- Update application configuration files if hardcoded MySQL connections exist

## Next Steps

1. Run: `docker-compose up --build`
2. Wait for all services to be healthy (check `docker-compose ps`)
3. Access GUI at: `http://localhost:6080`
4. Monitor logs: `docker-compose logs -f`
5. For production, consider:
   - Using environment files with secrets management
   - Setting up backup strategies for PostgreSQL
   - Configuring resource limits
   - Setting up monitoring and alerting
