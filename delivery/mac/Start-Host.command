#!/bin/bash
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
INSTALL="${HANDOFF_INSTALL:-$HOME/Library/Application Support/Triggers Client}"
GAME="${HANDOFF_GAME_DIR:-$INSTALL/instances/Sword Art Online}"
STATE="${HANDOFF_STATE:-$HOME/Library/Application Support/Minecraft Handoff/host}"
JAVA="${HANDOFF_JAVA:-}"
if [ -z "$JAVA" ]; then
  JDK="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
  [ -z "$JDK" ] || JAVA="$JDK/bin/java"
fi
if [ -z "$JAVA" ] || [ ! -x "$JAVA" ]; then
  echo "Java 21 JDK is required. Install an ARM64 Java 21 JDK, or set HANDOFF_JAVA to its bin/java."
  read -r -p "Press Enter to close. " _
  exit 1
fi
if [ ! -d "$GAME" ]; then echo "Game directory missing: $GAME";exit 1;fi
unset JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS
echo "First use the original Triggers launcher to join SAO. Leave that launcher and game open."
"$JAVA" --add-modules jdk.attach -jar "$HERE/lib/handoff-service-0.2.0.jar" list
GAME_PID="${HANDOFF_PID:-}"
if [ -z "$GAME_PID" ]; then read -r -p "Original Minecraft PID from the list above: " GAME_PID;fi
PAIR_HOST="${HANDOFF_HOST:-}"
if [ -z "$PAIR_HOST" ]; then read -r -p "Mac LAN/VPN address reachable from Windows (for example 192.168.1.20): " PAIR_HOST;fi
# Packet forwarding is on by default; run with HANDOFF_GENERIC=0 to turn it off.
GENERIC="--generic"
if [ "${HANDOFF_GENERIC:-1}" = "0" ]; then GENERIC=""; fi
echo "Host requires an awake Mac and the original game engine. Ctrl+C ends remote access only."
caffeinate -i "$JAVA" --add-modules jdk.attach -jar "$HERE/lib/handoff-service-0.2.0.jar" host \
  --pid "$GAME_PID" --game-dir "$GAME" --relay-jar "$HERE/lib/relay-bridge-0.2.0.jar" \
  --state "$STATE" --pair-host "$PAIR_HOST" --port "${HANDOFF_PORT:-25575}" $GENERIC
