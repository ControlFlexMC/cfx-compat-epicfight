#!/usr/bin/env bash
#
# cfx-compat-epicfight — Build and Deploy (Forge)
#
# Usage:
#   chmod +x tools/build-forge.sh
#   ./tools/build-forge.sh
#   ./tools/build-forge.sh --release
#   ./tools/build-forge.sh --clean
#   ./tools/build-forge.sh --clean-cfg
#
# Function: increment build_id → build → clean instance → deploy
#       Version: mod_version (manual) + build_id (auto-increment)
#       --release: artifact version = mod_version, ignores build_id
#       --clean:    run gradle clean before building
#       --clean-cfg:also clean cfx_compat_epicfight config files in config dir
#       Default (dev): artifact version = mod_version.build_id

set -euo pipefail

RELEASE_MODE=false
CLEAN=false
CLEAN_CFG=false
for arg in "$@"; do
    case "${arg}" in
        --release)
            RELEASE_MODE=true
            ;;
        --clean)
            CLEAN=true
            ;;
        --clean-cfg)
            CLEAN_CFG=true
            ;;
        *)
            echo "❌ Unknown argument: ${arg}"
            echo "Usage: $0 [--release] [--clean] [--clean-cfg]"
            exit 1
            ;;
    esac
done

# ── Config ──────────────────────────────────────
TOOLS_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "${TOOLS_DIR}/.." && pwd)"
PROPS_FILE="${PROJECT_DIR}/gradle.properties"
BUILD_DIR="${PROJECT_DIR}/build/libs"
MC_INSTANCE="${HOME}/.minecraftx/instances/1.20.1-forge47.4.4"
MODS_DIR="${MC_INSTANCE}/mods"
LOGS_DIR="${MC_INSTANCE}/logs"
CONFIG_PREFIX="cfx_compat_epicfight"

# ── Helper Functions ────────────────────────────
log_step() {
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "  [$1] $2"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
}

log_info() {
    echo "  $1"
}

# shellcheck source=build-version-lib.sh
source "${TOOLS_DIR}/build-version-lib.sh"

# ── Step 1: Read current version ────────────────
log_step "1/5" "Read current version"

MOD_VERSION=$(read_prop mod_version)
CURRENT_BUILD_ID=$(read_prop build_id)
log_info "mod_version: ${MOD_VERSION}"
log_info "build_id:    ${CURRENT_BUILD_ID}"
if [[ "${RELEASE_MODE}" == "true" ]]; then
    log_info "Mode: release (version = mod_version)"
else
    log_info "Mode: dev (version = mod_version.build_id)"
fi

# ── Step 2: Increment build_id ──────────────────
log_step "2/5" "Increment build_id"

NEW_BUILD_ID=$((CURRENT_BUILD_ID + 1))
ARTIFACT_VERSION="$(compute_artifact_version "${MOD_VERSION}" "${NEW_BUILD_ID}" "${RELEASE_MODE}")"

log_info "build_id: ${CURRENT_BUILD_ID} → ${NEW_BUILD_ID}"
log_info "Artifact version: ${ARTIFACT_VERSION}"

update_prop build_id "${CURRENT_BUILD_ID}" "${NEW_BUILD_ID}"
log_info "gradle.properties updated"

# ── Step 3: Build ───────────────────────────────
log_step "3/5" "Build Forge Mod JAR"

cd "${PROJECT_DIR}"

# Optional: gradle clean
if [[ "${CLEAN}" == "true" ]]; then
    log_info "Running gradle clean..."
    ./gradlew clean
fi

# Build
if [[ "${RELEASE_MODE}" == "true" ]]; then
    if ! ./gradlew build -Prelease; then
        echo ""
        echo "❌ Build failed! Rolling back build_id..."
        update_prop build_id "${NEW_BUILD_ID}" "${CURRENT_BUILD_ID}"
        exit 1
    fi
else
    if ! ./gradlew build; then
        echo ""
        echo "❌ Build failed! Rolling back build_id..."
        update_prop build_id "${NEW_BUILD_ID}" "${CURRENT_BUILD_ID}"
        exit 1
    fi
fi

MC_VER=$(read_prop minecraft_version)
NEW_JAR="${BUILD_DIR}/cfx-compat-epicfight-${ARTIFACT_VERSION}-mc${MC_VER}-forge.jar"
if [ ! -f "${NEW_JAR}" ]; then
    echo "❌ JAR file not found: ${NEW_JAR}"
    exit 1
fi

JAR_SIZE=$(du -h "${NEW_JAR}" | cut -f1 | tr -d ' ')
log_info "Build successful: cfx-compat-epicfight-${ARTIFACT_VERSION}-mc${MC_VER}-forge.jar (${JAR_SIZE})"

# ── Step 4: Clean instance ──────────────────────
log_step "4/5" "Clean instance"

# 4a: Clear logs
if [ -d "${LOGS_DIR}" ]; then
    rm -rf "${LOGS_DIR}"/*
    log_info "Logs cleared: ${LOGS_DIR}"
else
    log_info "Logs directory does not exist, skipping"
fi

# 4b: Clear crash reports
CRASH_DIR="${MC_INSTANCE}/crash-reports"
if [ -d "${CRASH_DIR}" ]; then
    rm -rf "${CRASH_DIR}"/*
    log_info "Crash reports cleared: ${CRASH_DIR}"
else
    log_info "Crash reports directory does not exist, skipping"
fi

# 4c: Remove old Mod versions
if [ -d "${MODS_DIR}" ]; then
    OLD_JARS=$(find "${MODS_DIR}" -name 'cfx-compat-epicfight-*-mc*-forge.jar' 2>/dev/null || true)
    if [ -n "${OLD_JARS}" ]; then
        rm -f "${MODS_DIR}"/cfx-compat-epicfight-*-mc*-forge.jar
        log_info "Old Mod versions removed"
    else
        log_info "No old Mod versions to remove"
    fi
else
    log_info "Mods directory does not exist, skipping"
fi

# 4d: Remove config files (only with --clean-cfg)
if [[ "${CLEAN_CFG}" == "true" ]]; then
    CONFIG_FILES=$(find "${MC_INSTANCE}" -maxdepth 2 -name "${CONFIG_PREFIX}*" -not -name "*.jar" 2>/dev/null || true)
    if [ -n "${CONFIG_FILES}" ]; then
        echo "${CONFIG_FILES}" | while read -r f; do
            if [ -d "${f}" ]; then
                rm -rf "${f}"
            else
                rm -f "${f}"
            fi
            log_info "Deleted: ${f}"
        done
    else
        log_info "No config files to remove"
    fi
else
    log_info "Keeping config files (use --clean-cfg to clean)"
fi

# ── Step 5: Deploy ──────────────────────────────
log_step "5/5" "Deploy to instance"

mkdir -p "${MODS_DIR}"
cp "${NEW_JAR}" "${MODS_DIR}/"

echo ""
echo "╔══════════════════════════════════════════════════╗"
echo "║  ✅ cfx-compat-epicfight deployed successfully!  ║"
echo "╠══════════════════════════════════════════════════╣"
echo "║  Version: ${ARTIFACT_VERSION}                            ║"
echo "║  JAR:     cfx-compat-epicfight-${ARTIFACT_VERSION}-mc${MC_VER}-forge.jar        ║"
echo "║  Size:    ${JAR_SIZE}                                 ║"
echo "║  Path:    ${MODS_DIR}  ║"
echo "╚══════════════════════════════════════════════════╝"
echo ""
echo "🚀 Launch Minecraft Forge 1.20.1 to test"
