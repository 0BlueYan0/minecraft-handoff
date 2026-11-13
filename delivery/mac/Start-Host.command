#!/bin/bash
set -euo pipefail
# Everything below is inside functions and the script ends with one call. bash reads a script
# while it runs, so replacing this file during the update must not change what is read next.
update_note() { echo "Update skipped: $1. Continuing with ${LOCAL_VERSION:-0.0.0}."; }

# True when $1 is a version number: exactly x.y.z with up to 9 digits per part.
version_ok() {
  case "$1" in ''|*[!0-9.]*) return 1 ;; esac
  printf '%s\n' "$1" | grep -Eq '^[0-9]{1,9}\.[0-9]{1,9}\.[0-9]{1,9}$'
}

# True when version $1 is newer than version $2 (both already checked with version_ok).
version_newer() {
  local a1 a2 a3 b1 b2 b3
  IFS=. read -r a1 a2 a3 <<<"$1"
  IFS=. read -r b1 b2 b3 <<<"$2"
  if [ $((10#$a1)) -ne $((10#$b1)) ]; then [ $((10#$a1)) -gt $((10#$b1)) ]; return; fi
  if [ $((10#$a2)) -ne $((10#$b2)) ]; then [ $((10#$a2)) -gt $((10#$b2)) ]; return; fi
  [ $((10#$a3)) -gt $((10#$b3)) ]
}

first_line() { head -n 1 "$1" 2>/dev/null | tr -d '\r' || true; }

# Replaces lib and mac together. If any step fails both old folders are put back, so the package
# never runs a new lib with an old mac script or the other way round.
swap_both() {
  local d
  for d in lib mac; do rm -rf "$ROOT/$d.old-update"; done
  for d in lib mac; do
    if [ -e "$ROOT/$d" ]; then
      mv "$ROOT/$d" "$ROOT/$d.old-update" || { swap_rollback; return 1; }
    fi
  done
  for d in lib mac; do
    mv "$WORK/x/$d" "$ROOT/$d" || { swap_rollback; return 1; }
  done
  for d in lib mac; do rm -rf "$ROOT/$d.old-update"; done
}

swap_rollback() {
  local d
  for d in lib mac; do
    [ -e "$ROOT/$d.old-update" ] || continue
    if [ -e "$ROOT/$d" ] && [ ! -e "$WORK/x/$d" ]; then mv "$ROOT/$d" "$WORK/x/$d" || true; fi
    if [ ! -e "$ROOT/$d" ]; then mv "$ROOT/$d.old-update" "$ROOT/$d" || true; fi
  done
}

# Replaces ROOT/$1 with WORK/x/$1, putting the old folder back when the move fails.
swap_dir() {
  rm -rf "$ROOT/$1.old-update"
  if [ -e "$ROOT/$1" ]; then mv "$ROOT/$1" "$ROOT/$1.old-update" || return 1; fi
  if ! mv "$WORK/x/$1" "$ROOT/$1"; then
    if [ -e "$ROOT/$1.old-update" ]; then mv "$ROOT/$1.old-update" "$ROOT/$1" || true; fi
    return 1
  fi
  rm -rf "$ROOT/$1.old-update"
}

# Returns 0 when nothing changed or the update failed (the installed version keeps running),
# 10 when the package was replaced and the start script has to run again.
update_package() {
  if [ "${HANDOFF_NO_UPDATE:-0}" = "1" ]; then return 0; fi
  local base="${HANDOFF_UPDATE_BASE:-https://github.com/0BlueYan0/minecraft-handoff/releases/latest/download}"
  base="${base%/}"
  case "$base" in
    https://*|http://127.0.0.1:*|http://127.0.0.1/*) ;;
    *) echo "Update skipped: the update address must start with https://."; return 0 ;;
  esac
  command -v curl >/dev/null 2>&1 || { echo "Update skipped: curl is not available."; return 0; }
  LOCAL_VERSION="$(first_line "$ROOT/VERSION")"
  version_ok "$LOCAL_VERSION" || LOCAL_VERSION="0.0.0"
  WORK="$(mktemp -d "${TMPDIR:-/tmp}/handoff-update.XXXXXX")" || { update_note "could not create a temporary folder"; return 0; }
  local rc=0
  apply_update "$base" || rc=$?
  rm -rf "$WORK"
  return "$rc"
}

apply_update() {
  local base="$1" remote got want have d
  curl -fsSL --max-redirs 5 --proto-redir =https --connect-timeout 15 --max-time 15 -o "$WORK/VERSION" "$base/VERSION" 2>/dev/null \
    || { update_note "could not read the latest version"; return 0; }
  remote="$(first_line "$WORK/VERSION")"
  version_ok "$remote" || { update_note "the latest version is not a version number"; return 0; }
  version_newer "$remote" "$LOCAL_VERSION" || return 0
  echo "Updating Minecraft Handoff $LOCAL_VERSION to $remote"
  echo "[1/5] Downloading the checksum list ..."
  curl -fsSL --max-redirs 5 --proto-redir =https --connect-timeout 15 --max-time 15 -o "$WORK/SHA256SUMS" "$base/SHA256SUMS" 2>/dev/null \
    || { update_note "could not download SHA256SUMS"; return 0; }
  echo "[2/5] Downloading minecraft-handoff.zip ..."
  # No total time limit for the large zip; the download stops after 15 seconds without progress. -# draws a progress bar.
  curl -fS -# -L --max-redirs 5 --proto-redir =https --connect-timeout 15 --speed-limit 1024 --speed-time 15 -o "$WORK/minecraft-handoff.zip" "$base/minecraft-handoff.zip" \
    || { update_note "could not download minecraft-handoff.zip"; return 0; }
  echo "[3/5] Checking the SHA-256 checksum ..."
  want="$(awk '$2=="minecraft-handoff.zip"{print tolower($1); exit}' "$WORK/SHA256SUMS" 2>/dev/null || true)"
  printf '%s\n' "$want" | grep -Eq '^[0-9a-f]{64}$' || { update_note "SHA256SUMS has no valid entry for the zip"; return 0; }
  got="$(shasum -a 256 "$WORK/minecraft-handoff.zip" 2>/dev/null | awk '{print tolower($1)}' || true)"
  [ -n "$got" ] || { update_note "could not compute the checksum"; return 0; }
  [ "$got" = "$want" ] || { update_note "checksum mismatch, download discarded"; return 0; }
  echo "[4/5] Unpacking ..."
  mkdir "$WORK/x" && unzip -q "$WORK/minecraft-handoff.zip" -d "$WORK/x" 2>/dev/null \
    || { update_note "could not unpack the download"; return 0; }
  have="$(first_line "$WORK/x/VERSION")"
  [ "$have" = "$remote" ] || { update_note "the download is version ${have:-unknown}, not $remote"; return 0; }
  [ -d "$WORK/x/lib" ] && [ -d "$WORK/x/mac" ] && [ -d "$WORK/x/windows" ] && [ -f "$WORK/x/mac/Start-Host.command" ] \
    || { update_note "the download is missing folders"; return 0; }
  echo "[5/5] Replacing the installed files ..."
  # lib and mac together, windows, guide, then VERSION last so a half-finished update is retried next start.
  swap_both || { update_note "could not replace lib and mac"; return 0; }
  swap_dir windows || { update_note "could not replace windows"; return 0; }
  cp "$WORK"/x/README-*.md "$ROOT/" || { update_note "could not replace the guide"; return 0; }
  chmod +x "$ROOT"/mac/*.command
  cp "$WORK/x/VERSION" "$ROOT/VERSION" || { update_note "could not write VERSION"; return 0; }
  echo "Updated to $remote."
  return 10
}

main() {
HERE="$(cd "$(dirname "$0")/.." && pwd)"
ROOT="$HERE"
UPDATE_RC=0
if [ -z "${HANDOFF_UPDATED:-}" ]; then update_package || UPDATE_RC=$?; fi
if [ "$UPDATE_RC" = "10" ]; then HANDOFF_UPDATED=1 exec "$HERE/mac/Start-Host.command" "$@"; fi
INSTALL="${HANDOFF_INSTALL:-$HOME/Library/Application Support/Triggers Client}"
GAME="${HANDOFF_GAME_DIR:-$INSTALL/instances/Sword Art Online}"
STATE="${HANDOFF_STATE:-$HOME/Library/Application Support/Minecraft Handoff/host}"
unset JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS
# java_home -v 21 prints the default JVM (for example Java 17) when no Java 21 is
# registered, so every candidate is checked for version 21+ and jdk.attach.
usable_java() {
  [ -x "$1" ] || return 1
  local version_text major modules
  version_text="$("$1" -version 2>&1)" || return 1
  major="$(printf '%s\n' "$version_text" | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -n 1)"
  case "$major" in ''|*[!0-9]*) return 1 ;; esac
  [ "$major" -ge 21 ] || return 1
  modules="$("$1" --list-modules 2>/dev/null)" || return 1
  # No pipe into grep -q: with pipefail an early grep exit makes printf fail on SIGPIPE.
  case "$modules" in jdk.attach@*|*$'\n'jdk.attach@*) return 0 ;; *) return 1 ;; esac
}
JAVA=""
if [ -n "${HANDOFF_JAVA:-}" ]; then
  if usable_java "$HANDOFF_JAVA"; then JAVA="$HANDOFF_JAVA"
  else echo "HANDOFF_JAVA is not a Java 21+ runtime with jdk.attach: $HANDOFF_JAVA"; fi
fi
if [ -z "$JAVA" ]; then
  for candidate in "$INSTALL"/runtime/*/Contents/Home/bin/java \
                   "$INSTALL"/runtime/*/bin/java \
                   "$INSTALL"/runtime/*/*/Contents/Home/bin/java; do
    if usable_java "$candidate"; then JAVA="$candidate"; break; fi
  done
fi
if [ -z "$JAVA" ] && [ -n "${JAVA_HOME:-}" ] && usable_java "$JAVA_HOME/bin/java"; then JAVA="$JAVA_HOME/bin/java"; fi
if [ -z "$JAVA" ]; then
  JDK="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
  if [ -n "$JDK" ] && usable_java "$JDK/bin/java"; then JAVA="$JDK/bin/java"; fi
fi
if [ -z "$JAVA" ]; then
  echo "Java 21 JDK is required. Install an ARM64 Java 21 JDK, or set HANDOFF_JAVA to its bin/java."
  read -r -p "Press Enter to close. " _
  exit 1
fi
echo "Using Java: $JAVA"
if [ ! -d "$GAME" ]; then echo "Game directory missing: $GAME";exit 1;fi
echo "First use the original Triggers launcher to join SAO. Leave that launcher and game open."
"$JAVA" --add-modules jdk.attach -jar "$HERE/lib/handoff-service.jar" list
GAME_PID="${HANDOFF_PID:-}"
if [ -z "$GAME_PID" ]; then read -r -p "Original Minecraft PID from the list above: " GAME_PID;fi
PAIR_HOST="${HANDOFF_HOST:-}"
if [ -z "$PAIR_HOST" ]; then
  SAVED_HOST=""
  if [ -f "$STATE/pair-host" ]; then SAVED_HOST="$(head -n 1 "$STATE/pair-host")"; fi
  if [ -n "$SAVED_HOST" ]; then
    read -r -p "Mac LAN/VPN address reachable from Windows [$SAVED_HOST] (Enter keeps it): " PAIR_HOST
    PAIR_HOST="${PAIR_HOST:-$SAVED_HOST}"
  else
    read -r -p "Mac LAN/VPN address reachable from Windows (for example 192.168.1.20): " PAIR_HOST
  fi
  if [ -n "$PAIR_HOST" ]; then mkdir -p "$STATE"; printf '%s\n' "$PAIR_HOST" > "$STATE/pair-host"; fi
fi
# The pairing is reused on every start; run with HANDOFF_NEW_PAIRING=1 to create a new one.
NEW_PAIRING=""
if [ "${HANDOFF_NEW_PAIRING:-0}" = "1" ]; then NEW_PAIRING="--new-pairing"; fi
# Packet forwarding is on by default; run with HANDOFF_GENERIC=0 to turn it off.
GENERIC="--generic"
if [ "${HANDOFF_GENERIC:-1}" = "0" ]; then GENERIC=""; fi
# The timed idle walk is on by default; run with HANDOFF_IDLE_WALK=0 to turn it off.
IDLE_WALK="--idle-walk"
if [ "${HANDOFF_IDLE_WALK:-1}" = "0" ]; then IDLE_WALK=""; fi
echo "Host requires an awake Mac and the original game engine. Ctrl+C ends remote access only."
caffeinate -i "$JAVA" --add-modules jdk.attach -jar "$HERE/lib/handoff-service.jar" host \
  --pid "$GAME_PID" --game-dir "$GAME" --relay-jar "$HERE/lib/relay-bridge.jar" \
  --state "$STATE" --pair-host "$PAIR_HOST" --port "${HANDOFF_PORT:-25575}" $GENERIC $IDLE_WALK $NEW_PAIRING
}

main "$@"; exit $?
