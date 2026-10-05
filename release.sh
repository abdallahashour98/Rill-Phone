#!/usr/bin/env bash
# Rill-Phone Fast Release & GitHub Update Script for Linux / macOS
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

python3 tools/release_update.py "$@"
