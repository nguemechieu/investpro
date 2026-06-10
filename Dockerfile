# ============================================================
# Build stage
# ============================================================
FROM maven:3.9.7-eclipse-temurin-21 AS build

WORKDIR /build

COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline || true

COPY . .
RUN mvn -B -Dmaven.test.skip=true clean package


# ============================================================
# Runtime stage
# Java 21 + JavaFX desktop runtime + noVNC
# ============================================================
FROM eclipse-temurin:21-jdk

ENV DEBIAN_FRONTEND=noninteractive \
    APP_HOME=/app \
    DISPLAY=:0 \
    GEOMETRY=1530x840 \
    DEPTH=24 \
    JAVA_OPTS="-Xmx2g -Xms512m -Djava.awt.headless=false -Dprism.order=sw -Dprism.verbose=false"

WORKDIR /app

# ============================================================
# Install system dependencies needed by the desktop runtime
# ============================================================
RUN apt-get update && apt-get install -y --no-install-recommends \
    autocutsel \
    bash \
    ca-certificates \
    curl \
    fluxbox \
    libasound2t64 \
    libdbus-1-3 \
    libegl1 \
    libfontconfig1 \
    libfreetype6 \
    libgl1 \
    libglib2.0-0 \
    libgtk-3-0 \
    libnss3 \
    libopengl0 \
    libpulse0 \
    libx11-6 \
    libx11-xcb1 \
    libxcb-cursor0 \
    libxcb-icccm4 \
    libxcb-image0 \
    libxcb-keysyms1 \
    libxcb-randr0 \
    libxcb-render-util0 \
    libxcb-shape0 \
    libxcb-sync1 \
    libxcb-xfixes0 \
    libxcb-xinerama0 \
    libxcb-xkb1 \
    libxcomposite1 \
    libxdamage1 \
    libxext6 \
    libxi6 \
    libxinerama1 \
    libxrandr2 \
    libxrender1 \
    libxkbcommon0 \
    libxkbcommon-x11-0 \
    libxtst6 \
    novnc \
    openssl \
    postgresql-client \
    websockify \
    x11-utils \
    x11vnc \
    xauth \
    xclip \
    xsel \
    xvfb \
    && rm -rf /var/lib/apt/lists/*

# ============================================================
# Copy built application
# ============================================================

COPY --from=build /build/target /tmp/target
RUN cp /tmp/target/*.jar /app/investpro.jar && \
    if [ -d /tmp/target/lib ]; then cp -r /tmp/target/lib /app/lib; else mkdir -p /app/lib; fi && \
    rm -rf /tmp/target

# ============================================================
# App directories
# ============================================================

RUN mkdir -p /app/data /app/logs /app/output /app/scripts

# ============================================================
# Startup script
# ============================================================

COPY docker-start.sh /app/scripts/docker-start.sh

RUN sed -i 's/\r$//' /app/scripts/docker-start.sh && \
    chmod +x /app/scripts/docker-start.sh

EXPOSE 5900 6080 8080

CMD ["/app/scripts/docker-start.sh"]
