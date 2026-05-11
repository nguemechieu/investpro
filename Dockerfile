# ============================================================
# Build stage
# ============================================================
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /investpro

# Copy pom first for better Docker layer caching
COPY pom.xml .

# Optional: pre-download dependencies
RUN mvn -B -DskipTests dependency:go-offline || true

# Copy source after dependencies
COPY src ./src

# Copy any resources/config needed by Maven
COPY . .

RUN mvn -B -DskipTests clean package


# ============================================================
# Runtime stage with JavaFX GUI support via noVNC
# ============================================================
FROM eclipse-temurin:21-jdk

WORKDIR /app

ENV DEBIAN_FRONTEND=noninteractive

# ============================================================
# Install GUI, JavaFX/native graphics, VNC, noVNC, supervisor
# ============================================================
RUN apt-get update && apt-get install -y --no-install-recommends \
    xvfb \
    x11vnc \
    novnc \
    websockify \
    supervisor \
    fluxbox \
    wget \
    curl \
    ca-certificates \
    fonts-liberation \
    xfonts-75dpi \
    xfonts-100dpi \
    xfonts-scalable \
    libxrender1 \
    libxtst6 \
    libxi6 \
    libxrandr2 \
    libxinerama1 \
    libxcursor1 \
    libxcomposite1 \
    libxdamage1 \
    libxfixes3 \
    libxext6 \
    libx11-6 \
    libgtk-3-0 \
    libglib2.0-0 \
    libgl1 \
    libegl1 \
    libasound2 \
    && rm -rf /var/lib/apt/lists/*

# ============================================================
# Copy built application
# ============================================================

# Safer than hardcoding investpro-1.0.0-SNAPSHOT.jar
COPY --from=build /investpro/target/*.jar /app/investpro.jar

# Optional dependency folder if your build creates target/lib
COPY --from=build /investpro/target/lib /app/lib

# ============================================================
# App configuration
# Do not store real secrets here. Override from docker-compose.
# ============================================================

ENV APP_NAME=InvestPro
ENV APP_ENV=docker
ENV APP_DEBUG=false
ENV APP_TIMEZONE=America/New_York

ENV DB_DIALECT=postgresql
ENV DB_HOST=postgres
ENV DB_PORT=5432
ENV DB_NAME=investpro
ENV DB_USERNAME=investpro
ENV DB_PASSWORD=investpro123
ENV DB_DRIVER=org.postgresql.Driver
ENV DB_URL=jdbc:postgresql://postgres:5432/investpro

ENV AUTO_TRADING_ENABLED=false
ENV AI_REASONING_ENABLED=true
ENV MAX_RISK_PER_TRADE=0.01
ENV MAX_DAILY_LOSS=0.03

# ============================================================
# Display / noVNC configuration
# ============================================================

ENV DISPLAY=:0
ENV GEOMETRY=1530x840
ENV DEPTH=24
ENV VNC_PASSWORD=investpro

# JavaFX rendering safety
ENV JAVA_TOOL_OPTIONS="-Dprism.order=sw -Dprism.verbose=false -Djava.awt.headless=false"

# ============================================================
# Supervisor
# ============================================================

RUN mkdir -p /etc/supervisor/conf.d /var/log/supervisor

COPY supervisord.conf /etc/supervisor/supervisord.conf

# noVNC: 6080
# VNC: 5900
# Optional app/server: 8080
EXPOSE 8080 5900 6080

CMD ["/usr/bin/supervisord", "-c", "/etc/supervisor/supervisord.conf"]