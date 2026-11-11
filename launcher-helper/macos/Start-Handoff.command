#!/bin/bash
set -eu

if [ "$(uname -s)" != Darwin ]; then
    echo 'This script is for macOS.'
    exit 2
fi

# These are scoped to this script and its children, including runtime probes.
unset JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS

compatible_java() {
    [ -x "$1" ] || return 1
    local version_text major
    version_text="$("$1" -version 2>&1)" || return 1
    major="$(printf '%s\n' "$version_text" | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -n 1)"
    case "$major" in ''|*[!0-9]*) return 1 ;; esac
    [ "$major" -ge 21 ]
}

script_dir="$(cd "$(dirname "$0")" && pwd -P)"
data_root="$HOME/Library/Application Support/Triggers Client"
game_dir="$data_root/instances/Sword Art Online"
mod="$script_dir/minecraft-handoff-0.1.0.jar"
helper="$script_dir/handoff-launcher-helper-0.1.1.jar"

if [ ! -f "$mod" ] || [ ! -f "$helper" ]; then
    echo 'Extract the entire package first; both JAR files must be next to this script.'
    exit 2
fi

java_bin=''
for candidate in "$data_root"/runtime/*/Contents/Home/bin/java \
                 "$data_root"/runtime/*/bin/java \
                 "$data_root"/runtime/*/*/Contents/Home/bin/java; do
    if compatible_java "$candidate"; then
        java_bin="$candidate"
        break
    fi
done

if [ -z "$java_bin" ] && [ -n "${JAVA_HOME-}" ]; then
    if compatible_java "$JAVA_HOME/bin/java"; then
        java_bin="$JAVA_HOME/bin/java"
    fi
fi

if [ -z "$java_bin" ]; then
    java_home="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
    if [ -n "$java_home" ] && compatible_java "$java_home/bin/java"; then
        java_bin="$java_home/bin/java"
    fi
fi

if [ -z "$java_bin" ]; then
    echo 'Java 21 could not be found. Supply the launcher runtime directory layout; no login information is needed.'
    exit 2
fi

exec "$java_bin" -Xmx64m -jar "$helper" --game-dir "$game_dir" --mod "$mod"
