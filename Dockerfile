# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /investpro

COPY . .

RUN mvn -B -DskipTests clean package

# Runtime stage with GUI support via noVNC and PostgreSQL
FROM eclipse-temurin:21-jdk

WORKDIR /app

# Install noVNC, TigerVNC, and required dependencies for GUI
RUN apt-get update && apt-get install -y \
    xvfb \
    x11vnc \
    novnc \
    supervisor \
    wget \
    curl \
    ca-certificates \
    fonts-liberation \
    xfonts-75dpi \
    xfonts-100dpi \
    xfonts-scalable \
    libxrender1 \
    && rm -rf /var/lib/apt/lists/*

# Copy the built JAR from build stage
COPY --from=build /investpro/target/investpro-1.0.0-SNAPSHOT.jar ./investpro.jar

# PostgreSQL JDBC driver will be loaded at runtime
# Ensure PostgreSQL driver is available in the classpath
ENV DB_URL=jdbc:postgresql://postgres:5432/investpro
ENV DB_USER=investpro
ENV DB_PASSWORD=investpro123
ENV DB_DRIVER=org.postgresql.Driver

# Display and VNC settings
ENV DISPLAY=:0
ENV GEOMETRY=1280x1024
ENV DEPTH=24
ENV VNC_PASSWORD=investpro

# Supervisor configuration directory
RUN mkdir -p /etc/supervisor/conf.d

# Copy supervisor configuration
COPY supervisord.conf /etc/supervisor/supervisord.conf

# Expose application and noVNC ports
EXPOSE 8080 5900 6080

# Start with supervisor (manages both X11/VNC and the app)
CMD ["/usr/bin/supervisord", "-c", "/etc/supervisor/supervisord.conf"]

