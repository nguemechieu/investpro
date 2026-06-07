#!/usr/bin/env bash
set -e

echo "============================================================"
echo "Starting InvestPro Desktop Runtime"
echo "============================================================"

export DISPLAY="${DISPLAY:-:0}"
export GEOMETRY="${GEOMETRY:-1530x840}"
export DEPTH="${DEPTH:-24}"
export VNC_PASSWORD="${VNC_PASSWORD:-investpro}"
export JAVA_OPTS="${JAVA_OPTS:--Xmx2g -Xms512m -Djava.awt.headless=false -Dglass.platform=gtk -Dprism.order=sw}"

echo "DISPLAY=$DISPLAY"
echo "GEOMETRY=$GEOMETRY"
echo "DEPTH=$DEPTH"

mkdir -p /app/logs

# Clean up stale X11 lock files from previous runs
rm -f /tmp/.X*-lock 2>/dev/null || true
rm -f /tmp/.X11-unix/X* 2>/dev/null || true

echo "Starting Xvfb..."
Xvfb "$DISPLAY" -screen 0 "${GEOMETRY}x${DEPTH}" -ac +extension GLX +render -noreset \
  >/app/logs/xvfb.log 2>/app/logs/xvfb.err.log &
XVFB_PID=$!

sleep 2

echo "Starting Fluxbox..."
fluxbox >/app/logs/fluxbox.log 2>/app/logs/fluxbox.err.log &
FLUXBOX_PID=$!

sleep 1

echo "Starting autocutsel (clipboard sync)..."

# Standard clipboard used by Ctrl+C / Ctrl+V
autocutsel -selection CLIPBOARD -fork \
  >/app/logs/autocutsel-clipboard.log 2>/app/logs/autocutsel-clipboard.err.log || true

# X11 primary selection
autocutsel -selection PRIMARY -fork \
  >/app/logs/autocutsel-primary.log 2>/app/logs/autocutsel-primary.err.log || true

sleep 1

echo "Starting x11vnc..."
x11vnc \
  -display "$DISPLAY" \
  -forever \
  -shared \
  -rfbport 5900 \
  -passwd "$VNC_PASSWORD" \
  -xkb \
  -noxdamage \
  -repeat \
  >/app/logs/x11vnc.log 2>/app/logs/x11vnc.err.log &
X11VNC_PID=$!

sleep 1

echo "Starting noVNC..."
websockify \
  --web=/usr/share/novnc/ \
  6080 \
  localhost:5900 \
  >/app/logs/novnc.log 2>/app/logs/novnc.err.log &
NOVNC_PID=$!

sleep 2

echo "============================================================"
echo "InvestPro noVNC is available at:"
echo "http://localhost:6080/vnc.html?autoconnect=1&resize=scale"
echo "============================================================"

echo "Starting InvestPro JavaFX application..."

JAVAFX_MODULE_PATH="$(find /app/lib -maxdepth 1 -name 'javafx-*.jar' -printf '%p:' | sed 's/:$//')"

java $JAVA_OPTS \
  -cp "/app/investpro.jar:/app/lib/*" \
  --module-path "$JAVAFX_MODULE_PATH" \
  --add-modules javafx.controls,javafx.fxml,javafx.swing,javafx.base,javafx.graphics \
  org.investpro.InvestPro \
  2>&1 | tee /app/logs/investpro.log

APP_EXIT_CODE=${PIPESTATUS[0]}

echo "InvestPro exited with code $APP_EXIT_CODE"

echo "Stopping background services..."
kill "$NOVNC_PID" "$X11VNC_PID" "$FLUXBOX_PID" "$XVFB_PID" 2>/dev/null || true
pkill autocutsel 2>/dev/null || true

exit "$APP_EXIT_CODE"