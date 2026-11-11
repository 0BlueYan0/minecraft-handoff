#!/bin/bash
set -eu
if [ "$(uname -s)" != Darwin ]; then
    echo 'This launcher is for macOS.'
    exit 2
fi
unset JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS

usable_java() {
    [ -x "$1" ] || return 1
    local version_text major modules
    version_text="$("$1" -version 2>&1)" || return 1
    major="$(printf '%s\n' "$version_text" | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -n 1)"
    case "$major" in ''|*[!0-9]*) return 1 ;; esac
    [ "$major" -ge 21 ] || return 1
    modules="$("$1" --list-modules 2>/dev/null)" || return 1
    printf '%s\n' "$modules" | grep -q '^jdk.attach@'
}

script_dir="$(cd "$(dirname "$0")" && pwd -P)"
data_root="$HOME/Library/Application Support/Triggers Client"
game_dir="$data_root/instances/Sword Art Online"
agent="$script_dir/handoff-runtime-agent-0.1.2.jar"
if [ ! -f "$agent" ]; then
    echo 'Extract the whole package; the agent JAR must be next to this script.'
    exit 2
fi
java_bin=''
for candidate in "$data_root"/runtime/*/Contents/Home/bin/java \
                 "$data_root"/runtime/*/bin/java \
                 "$data_root"/runtime/*/*/Contents/Home/bin/java; do
    if usable_java "$candidate"; then java_bin="$candidate"; break; fi
done
if [ -z "$java_bin" ] && [ -n "${JAVA_HOME-}" ]; then
    if usable_java "$JAVA_HOME/bin/java"; then java_bin="$JAVA_HOME/bin/java"; fi
fi
if [ -z "$java_bin" ]; then
    registered_home="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
    if [ -n "$registered_home" ] && usable_java "$registered_home/bin/java"; then java_bin="$registered_home/bin/java"; fi
fi
if [ -z "$java_bin" ]; then
    echo 'JAVA_WITH_ATTACH_NOT_FOUND: a Java 21+ runtime containing jdk.attach is required.'
    exit 2
fi
echo 'Keep the original launcher and SAO game open. This tool does not restart the game.'
exec "$java_bin" -Xmx64m --add-modules jdk.attach -jar "$agent" --game-dir "$game_dir"
