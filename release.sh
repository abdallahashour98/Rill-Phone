#!/usr/bin/env bash
# Rill-Phone Fast Release & GitHub Update Script for Linux / macOS
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [ -d "/usr/lib/jvm/java-25-openjdk" ]; then
    export JAVA_HOME="/usr/lib/jvm/java-25-openjdk"
    export PATH="$JAVA_HOME/bin:$PATH"
fi

python3 tools/release_update.py "$@"
