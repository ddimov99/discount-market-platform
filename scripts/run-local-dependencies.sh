#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRAPER_PROJECT_DIR="${SCRAPER_PROJECT_DIR:-${ROOT_DIR}/ozone_discount_scraper}"
SCRAPER_VENV_DIR="${SCRAPER_VENV_DIR:-${SCRAPER_PROJECT_DIR}/.venv}"
SCRAPY_EXECUTABLE="${SCRAPY_EXECUTABLE:-${SCRAPER_VENV_DIR}/bin/scrapy}"
SCRAPERS_OUTPUT_DIR="${SCRAPERS_OUTPUT_DIR:-/tmp/discount-market-scrapes}"
SCRAPERS_FIXED_DELAY_MS="${SCRAPERS_FIXED_DELAY_MS:-60000}"
SCRAPERS_SCRAPY_MAX_PAGES="${SCRAPERS_SCRAPY_MAX_PAGES:-1}"
SCRAPERS_SCHEDULER_MAX_PARALLEL_RUNS="${SCRAPERS_SCHEDULER_MAX_PARALLEL_RUNS:-3}"
SPRING_PROFILE="${SPRING_PROFILE:-local}"

SKIP_VENV=false
RESET_DB=false

usage() {
    cat <<EOF
Usage: scripts/run-local-dependencies.sh [options]

Starts only the local dependencies needed before launching the Spring app from IntelliJ IDEA.
The Spring Boot app itself is not started by this script.

Options:
  --skip-venv        Do not create/install the Scrapy Python virtualenv.
  --reset-db         Delete the local Docker Postgres volume before startup.
  --help             Show this help.

Environment overrides:
  SPRING_PROFILE             Default: local
  SCRAPER_PROJECT_DIR        Default: ${ROOT_DIR}/ozone_discount_scraper
  SCRAPER_VENV_DIR           Default: \$SCRAPER_PROJECT_DIR/.venv
  SCRAPY_EXECUTABLE          Default: \$SCRAPER_VENV_DIR/bin/scrapy
  SCRAPERS_OUTPUT_DIR        Default: /tmp/discount-market-scrapes
  SCRAPERS_FIXED_DELAY_MS    Default: 60000
  SCRAPERS_SCRAPY_MAX_PAGES  Default: 1
  SCRAPERS_SCHEDULER_MAX_PARALLEL_RUNS
                             Default: 3
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --skip-venv)
            SKIP_VENV=true
            shift
            ;;
        --reset-db)
            RESET_DB=true
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

setup_scrapy_venv() {
    if [[ "${SKIP_VENV}" == true ]]; then
        log "Skipping Scrapy virtualenv setup"
        return
    fi

    require_command python3

    if [[ ! -d "${SCRAPER_VENV_DIR}" ]]; then
        log "Creating Scrapy virtualenv at ${SCRAPER_VENV_DIR}"
        python3 -m venv "${SCRAPER_VENV_DIR}"
    fi

    if [[ ! -x "${SCRAPY_EXECUTABLE}" ]]; then
        log "Installing Scrapy dependencies"
        "${SCRAPER_VENV_DIR}/bin/python" -m pip install --upgrade pip
        "${SCRAPER_VENV_DIR}/bin/python" -m pip install -r "${SCRAPER_PROJECT_DIR}/requirements.txt"
    fi
}

start_postgres() {
    require_command docker

    if [[ "${RESET_DB}" == true ]]; then
        log "Resetting local Postgres data volume"
        (cd "${ROOT_DIR}" && docker compose stop postgres >/dev/null 2>&1 || true)
        (cd "${ROOT_DIR}" && docker compose rm -sfv postgres >/dev/null 2>&1 || true)
        docker volume rm -f discount-market-platform_postgres_data >/dev/null 2>&1 || true
    fi

    log "Starting Postgres"
    (cd "${ROOT_DIR}" && docker compose up -d postgres)

    log "Waiting for Postgres health check"
    for _ in {1..30}; do
        if (cd "${ROOT_DIR}" && docker compose exec -T postgres pg_isready -U discount_app -d discount_market >/dev/null 2>&1); then
            log "Postgres is ready"
            return
        fi
        sleep 1
    done

    echo "Postgres did not become ready within 30 seconds" >&2
    exit 1
}

print_idea_settings() {
    mkdir -p "${SCRAPERS_OUTPUT_DIR}"

    cat <<EOF

Local dependencies are ready.

Use this IntelliJ IDEA run/debug configuration for the app:

  Main class:
    com.offers.app.AppApplication

  Active profiles:
    ${SPRING_PROFILE}

  Program arguments:
    --spring.profiles.active=${SPRING_PROFILE} --scrapers.base-dir=${ROOT_DIR} --scrapers.output-dir=${SCRAPERS_OUTPUT_DIR} --scrapers.scrapy.executable=${SCRAPY_EXECUTABLE} --scrapers.scrapy.max-pages=${SCRAPERS_SCRAPY_MAX_PAGES} --scrapers.scheduler.fixed-delay-ms=${SCRAPERS_FIXED_DELAY_MS} --scrapers.scheduler.max-parallel-runs=${SCRAPERS_SCHEDULER_MAX_PARALLEL_RUNS}

  Useful local URLs:
    App after IDEA starts it: http://localhost:8080/api/offers
EOF
}

setup_scrapy_venv
start_postgres
print_idea_settings
