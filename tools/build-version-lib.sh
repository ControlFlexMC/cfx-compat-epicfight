# Shared version helpers for build-and-deploy scripts.
# Expects PROPS_FILE to be set by the caller.

read_prop() {
    grep "^${1}=" "${PROPS_FILE}" | cut -d'=' -f2 | tr -d ' '
}

update_prop() {
    local key="$1"
    local from="$2"
    local to="$3"
    if [[ "$(uname)" == "Darwin" ]]; then
        sed -i '' "s/^${key}=${from}$/${key}=${to}/" "${PROPS_FILE}"
    else
        sed -i "s/^${key}=${from}$/${key}=${to}/" "${PROPS_FILE}"
    fi
}

# release: mod_version only; dev: mod_version.build_id
compute_artifact_version() {
    local mod_version="$1"
    local build_id="$2"
    local release_mode="$3"
    if [[ "${release_mode}" == "true" ]]; then
        echo "${mod_version}"
    else
        echo "${mod_version}.${build_id}"
    fi
}
