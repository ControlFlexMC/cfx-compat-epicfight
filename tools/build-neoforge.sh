#!/usr/bin/env bash
# Build the NeoForge (MC 1.21.1) artifact for cfx-compat-epicfight.
# Mirrors build_forge.sh; this project targets NeoForge only.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"

# ── Defaults ──────────────────────────────────────────────────────────────────
RELEASE=false
CLEAN=false
EXTRA_ARGS=()

# ── Parse args ────────────────────────────────────────────────────────────────
for arg in "$@"; do
    case "$arg" in
        --release)
            RELEASE=true
            ;;
        --clean)
            CLEAN=true
            ;;
        *)
            EXTRA_ARGS+=("$arg")
            ;;
    esac
done

# ── Determine task and version label ──────────────────────────────────────────
mod_version=$(grep -E '^mod_version=' gradle.properties | cut -d= -f2)

if $RELEASE; then
    gradle_args=(-Prelease)
    label="${mod_version}"
else
    gradle_args=()
    build_id=$(grep -E '^build_id=' gradle.properties | cut -d= -f2)
    label="${mod_version}.${build_id}"
fi

if $RELEASE; then build_type="Release"; else build_type="Debug"; fi
echo "==> ${build_type} NeoForge build: version ${label}"

if $CLEAN; then
    echo "==> Cleaning first..."
    ./gradlew clean
fi

gradle_cmd=(./gradlew build)
if [[ ${#gradle_args[@]} -gt 0 ]]; then
    gradle_cmd+=("${gradle_args[@]}")
fi
if [[ ${#EXTRA_ARGS[@]} -gt 0 ]]; then
    gradle_cmd+=("${EXTRA_ARGS[@]}")
fi
echo "==> Running: ${gradle_cmd[*]}"
"${gradle_cmd[@]}"

# ── Summary ───────────────────────────────────────────────────────────────────
artifact=$(ls -1t build/libs/*.jar 2>/dev/null | head -1 || true)
if [[ -n "$artifact" ]]; then
    echo ""
    echo "✓ Build complete: $(basename "$artifact")"
else
    echo ""
    echo "✓ Build complete (no jar artifact found)."
fi
