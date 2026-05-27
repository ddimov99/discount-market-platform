#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRAPER_PROJECT_DIR="${SCRAPER_PROJECT_DIR:-${ROOT_DIR}/ozone_discount_scraper}"
SCRAPER_VENV_DIR="${SCRAPER_VENV_DIR:-${SCRAPER_PROJECT_DIR}/.venv}"
SCRAPERS_OUTPUT_DIR="${SCRAPERS_OUTPUT_DIR:-/tmp/discount-market-scrapes}"

REMOVE_VOLUMES=false
REMOVE_SCRAPES=false
REMOVE_VENV=false
REMOVE_IMAGES=false

usage() {
    cat <<EOF
Usage: scripts/clean-local.sh [options]

Stops and removes the local Docker services used for development.
By default it keeps Postgres data, Scrapy output, and the Python venv.

Options:
  --volumes      Also delete Docker volumes: Postgres data.
  --scrapes      Also delete local scraper output files from SCRAPERS_OUTPUT_DIR.
  --venv         Also delete the Scrapy Python virtualenv.
  --images       Also remove Docker images built by docker compose.
  --all          Delete containers, Docker volumes, Scrapy output, Scrapy venv, and compose images.
  --help         Show this help.

Environment overrides:
  SCRAPER_PROJECT_DIR  Default: ${ROOT_DIR}/ozone_discount_scraper
  SCRAPER_VENV_DIR     Default: \$SCRAPER_PROJECT_DIR/.venv
  SCRAPERS_OUTPUT_DIR  Default: /tmp/discount-market-scrapes
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --volumes)
            REMOVE_VOLUMES=true
            shift
            ;;
        --scrapes)
            REMOVE_SCRAPES=true
            shift
            ;;
        --venv)
            REMOVE_VENV=true
            shift
            ;;
        --images)
            REMOVE_IMAGES=true
            shift
            ;;
        --all)
            REMOVE_VOLUMES=true
            REMOVE_SCRAPES=true
            REMOVE_VENV=true
            REMOVE_IMAGES=true
            shift
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1" >&2
            usage >&2
            exit 2
            ;;
    esac
done

log() {
    printf '\n[%s] %s\n' "$(date '+%H:%M:%S')" "$*"
}

require_command() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "Missing required command: $1" >&2
        exit 1
    fi
}

safe_remove_dir() {
    local dir="$1"
    local expected_parent="$2"

    if [[ ! -e "${dir}" ]]; then
        log "Nothing to remove at ${dir}"
        return
    fi

    if [[ ! -d "${dir}" ]]; then
        echo "Refusing to remove ${dir}: it is not a directory" >&2
        exit 1
    fi

    case "${dir}" in
        "${expected_parent}"/*)
            log "Removing ${dir}"
            rm -rf "${dir}"
            ;;
        *)
            echo "Refusing to remove ${dir}: expected it under ${expected_parent}" >&2
            exit 1
            ;;
    esac
}

stop_docker_services() {
    require_command docker

    local compose_args=(down --remove-orphans)
    if [[ "${REMOVE_VOLUMES}" == true ]]; then
        compose_args+=(--volumes)
    fi
    if [[ "${REMOVE_IMAGES}" == true ]]; then
        compose_args+=(--rmi local)
    fi

    log "Stopping local Docker services"
    (cd "${ROOT_DIR}" && docker compose "${compose_args[@]}")
}

remove_scraper_output() {
    if [[ "${REMOVE_SCRAPES}" != true ]]; then
        return
    fi

    safe_remove_dir "${SCRAPERS_OUTPUT_DIR}" "/tmp"
}

remove_scraper_venv() {
    if [[ "${REMOVE_VENV}" != true ]]; then
        return
    fi

    safe_remove_dir "${SCRAPER_VENV_DIR}" "${SCRAPER_PROJECT_DIR}"
}

stop_docker_services
remove_scraper_output
remove_scraper_venv

log "Local cleanup complete"
