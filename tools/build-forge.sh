#!/usr/bin/env bash
#
# cfx-compat-epicfight — Build and Deploy (Forge)
#
# 用法:
#   chmod +x tools/build-forge.sh
#   ./tools/build-forge.sh
#   ./tools/build-forge.sh --release
#   ./tools/build-forge.sh --clean
#   ./tools/build-forge.sh --clean-cfg
#
# 功能: 递增 build_id → 构建 → 清理实例 → 部署
#       版本: mod_version (人工维护) + build_id (自动递增)
#       --release: 产物版本为 mod_version，忽略 build_id
#       --clean:    构建前先执行 gradle clean
#       --clean-cfg:同时清理 config 目录下的 cfx_compat_epicfight 配置文件
#       默认 (dev): 产物版本为 mod_version.build_id

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
            echo "❌ 未知参数: ${arg}"
            echo "用法: $0 [--release] [--clean] [--clean-cfg]"
            exit 1
            ;;
    esac
done

# ── 配置 ──────────────────────────────────────
TOOLS_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "${TOOLS_DIR}/.." && pwd)"
PROPS_FILE="${PROJECT_DIR}/gradle.properties"
BUILD_DIR="${PROJECT_DIR}/build/libs"
MC_INSTANCE="${HOME}/.minecraftx/instances/1.20.1-forge47.4.4"
MODS_DIR="${MC_INSTANCE}/mods"
LOGS_DIR="${MC_INSTANCE}/logs"
CONFIG_PREFIX="cfx_compat_epicfight"

# ── 辅助函数 ──────────────────────────────────
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

# ── Step 1: 读取当前版本号 ─────────────────────
log_step "1/5" "读取当前版本号"

MOD_VERSION=$(read_prop mod_version)
CURRENT_BUILD_ID=$(read_prop build_id)
log_info "mod_version: ${MOD_VERSION}"
log_info "build_id:    ${CURRENT_BUILD_ID}"
if [[ "${RELEASE_MODE}" == "true" ]]; then
    log_info "模式: release (版本 = mod_version)"
else
    log_info "模式: dev (版本 = mod_version.build_id)"
fi

# ── Step 2: 递增 build_id ─────────────────
log_step "2/5" "递增 build_id"

NEW_BUILD_ID=$((CURRENT_BUILD_ID + 1))
ARTIFACT_VERSION="$(compute_artifact_version "${MOD_VERSION}" "${NEW_BUILD_ID}" "${RELEASE_MODE}")"

log_info "build_id: ${CURRENT_BUILD_ID} → ${NEW_BUILD_ID}"
log_info "产物版本: ${ARTIFACT_VERSION}"

update_prop build_id "${CURRENT_BUILD_ID}" "${NEW_BUILD_ID}"
log_info "gradle.properties 已更新"

# ── Step 3: 构建 ────────────────
log_step "3/5" "编译 Forge Mod JAR"

cd "${PROJECT_DIR}"

# 可选: gradle clean
if [[ "${CLEAN}" == "true" ]]; then
    log_info "执行 gradle clean..."
    ./gradlew clean
fi

# 构建
if [[ "${RELEASE_MODE}" == "true" ]]; then
    if ! ./gradlew build -Prelease; then
        echo ""
        echo "❌ 编译失败！正在回滚 build_id..."
        update_prop build_id "${NEW_BUILD_ID}" "${CURRENT_BUILD_ID}"
        exit 1
    fi
else
    if ! ./gradlew build; then
        echo ""
        echo "❌ 编译失败！正在回滚 build_id..."
        update_prop build_id "${NEW_BUILD_ID}" "${CURRENT_BUILD_ID}"
        exit 1
    fi
fi

MC_VER=$(read_prop minecraft_version)
NEW_JAR="${BUILD_DIR}/cfx-compat-epicfight-${ARTIFACT_VERSION}-mc${MC_VER}-forge.jar"
if [ ! -f "${NEW_JAR}" ]; then
    echo "❌ JAR 文件未找到: ${NEW_JAR}"
    exit 1
fi

JAR_SIZE=$(du -h "${NEW_JAR}" | cut -f1 | tr -d ' ')
log_info "构建成功: cfx-compat-epicfight-${ARTIFACT_VERSION}-mc${MC_VER}-forge.jar (${JAR_SIZE})"

# ── Step 4: 清理实例 ──────────────────
log_step "4/5" "清理实例"

# 4a: 清空日志
if [ -d "${LOGS_DIR}" ]; then
    rm -rf "${LOGS_DIR}"/*
    log_info "已清空日志: ${LOGS_DIR}"
else
    log_info "日志目录不存在，跳过"
fi

# 4b: 清空崩溃报告
CRASH_DIR="${MC_INSTANCE}/crash-reports"
if [ -d "${CRASH_DIR}" ]; then
    rm -rf "${CRASH_DIR}"/*
    log_info "已清空崩溃报告: ${CRASH_DIR}"
else
    log_info "崩溃报告目录不存在，跳过"
fi

# 4c: 删除旧版本 Mod
if [ -d "${MODS_DIR}" ]; then
    OLD_JARS=$(find "${MODS_DIR}" -name 'cfx-compat-epicfight-*-mc*-forge.jar' 2>/dev/null || true)
    if [ -n "${OLD_JARS}" ]; then
        rm -f "${MODS_DIR}"/cfx-compat-epicfight-*-mc*-forge.jar
        log_info "已删除旧版本 Mod"
    else
        log_info "无旧版本 Mod 需要删除"
    fi
else
    log_info "Mods 目录不存在，跳过"
fi

# 4d: 删除配置文件（仅 --clean-cfg 时）
if [[ "${CLEAN_CFG}" == "true" ]]; then
    CONFIG_FILES=$(find "${MC_INSTANCE}" -maxdepth 2 -name "${CONFIG_PREFIX}*" -not -name "*.jar" 2>/dev/null || true)
    if [ -n "${CONFIG_FILES}" ]; then
        echo "${CONFIG_FILES}" | while read -r f; do
            if [ -d "${f}" ]; then
                rm -rf "${f}"
            else
                rm -f "${f}"
            fi
            log_info "已删除: ${f}"
        done
    else
        log_info "无配置文件需要删除"
    fi
else
    log_info "保留 config 配置 (使用 --clean-cfg 可清理)"
fi

# ── Step 5: 部署 ──────────────────
log_step "5/5" "部署到实例"

mkdir -p "${MODS_DIR}"
cp "${NEW_JAR}" "${MODS_DIR}/"

echo ""
echo "╔══════════════════════════════════════════════════╗"
echo "║  ✅ cfx-compat-epicfight 部署成功！              ║"
echo "╠══════════════════════════════════════════════════╣"
echo "║  版本:   ${ARTIFACT_VERSION}                            ║"
echo "║  JAR:    cfx-compat-epicfight-${ARTIFACT_VERSION}-mc${MC_VER}-forge.jar        ║"
echo "║  大小:   ${JAR_SIZE}                                 ║"
echo "║  路径:   ${MODS_DIR}  ║"
echo "╚══════════════════════════════════════════════════╝"
echo ""
echo "🚀 启动 Minecraft Forge 1.20.1 即可测试"
