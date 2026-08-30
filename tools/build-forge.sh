#!/usr/bin/env bash
# Build the Forge (MC 1.20.1) artifact for cfx-compat-epicfight.
#
# Usage:
#   ./tools/build-forge.sh
#   ./tools/build-forge.sh --release
#   ./tools/build-forge.sh --clean
#
# Environment:
#   MC_1_20_1_FORGE_PATH  instance dir; unset → skip instance deploy (WARNING)
#   RELEASE_DEPLOY_PATH   extra copy dest on --release; unset → WARNING
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
mc_ver=$(grep -E '^minecraft_version=' gradle.properties | cut -d= -f2)

if $RELEASE; then
    label="${mod_version}"
else
    build_id=$(grep -E '^build_id=' gradle.properties | cut -d= -f2)
    label="${mod_version}.${build_id}"
fi

if $RELEASE; then build_type="Release"; else build_type="Debug"; fi
echo "==> ${build_type} Forge build: version ${label}"

if $CLEAN; then
    echo "==> Cleaning first..."
    ./gradlew clean
fi

gradle_cmd=(./gradlew build)
if $RELEASE; then
    gradle_cmd+=(-Prelease)
fi
if [[ ${#EXTRA_ARGS[@]} -gt 0 ]]; then
    gradle_cmd+=("${EXTRA_ARGS[@]}")
fi
echo "==> Running: ${gradle_cmd[*]}"
"${gradle_cmd[@]}"

# ── Artifact ──────────────────────────────────────────────────────────────────
NEW_JAR="${PROJECT_DIR}/build/libs/cfx-compat-epicfight-${label}-mc${mc_ver}-forge.jar"
if [[ ! -f "${NEW_JAR}" ]]; then
    echo "❌ JAR not found: ${NEW_JAR}"
    exit 1
fi

echo ""
echo "✓ Build complete: $(basename "${NEW_JAR}")"

# ── Instance deploy ───────────────────────────────────────────────────────────
MC_INSTANCE="${MC_1_20_1_FORGE_PATH:-}"
MODS_DIR="${MC_INSTANCE}/mods"

if [[ -z "${MC_INSTANCE}" ]]; then
    echo "⚠️  MC_1_20_1_FORGE_PATH is not set; skipping instance deploy"
    echo "   artifact: ${NEW_JAR}"
else
    LOGS_DIR="${MC_INSTANCE}/logs"
    if [[ -d "${LOGS_DIR}" ]]; then
        rm -rf "${LOGS_DIR:?}"/*
        echo "  cleared logs: ${LOGS_DIR}"
    else
        echo "  logs dir missing, skip"
    fi

    CRASH_DIR="${MC_INSTANCE}/crash-reports"
    if [[ -d "${CRASH_DIR}" ]]; then
        rm -rf "${CRASH_DIR:?}"/*
        echo "  cleared crash-reports: ${CRASH_DIR}"
    else
        echo "  crash-reports dir missing, skip"
    fi

    if [[ -d "${MODS_DIR}" ]]; then
        rm -f "${MODS_DIR}"/cfx-compat-epicfight-*-mc*-forge.jar
        echo "  removed old cfx-compat-epicfight Forge jars"
    else
        echo "  mods dir missing, will create"
    fi

    mkdir -p "${MODS_DIR}"
    cp "${NEW_JAR}" "${MODS_DIR}/"
    echo "  deployed: ${MODS_DIR}/$(basename "${NEW_JAR}")"
fi

# ── Release copy ──────────────────────────────────────────────────────────────
if $RELEASE; then
    if [[ -n "${RELEASE_DEPLOY_PATH:-}" ]]; then
        mkdir -p "${RELEASE_DEPLOY_PATH}"
        cp "${NEW_JAR}" "${RELEASE_DEPLOY_PATH}/"
        echo "  release copy: ${RELEASE_DEPLOY_PATH}/$(basename "${NEW_JAR}")"
    else
        echo "⚠️  release build: RELEASE_DEPLOY_PATH is not set; skipping release copy"
    fi
elif [[ -n "${RELEASE_DEPLOY_PATH:-}" ]]; then
    echo "  dev build: ignoring RELEASE_DEPLOY_PATH (only used with --release)"
fi
