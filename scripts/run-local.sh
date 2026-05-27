#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_DIR="${ROOT_DIR}/app"
SCRAPER_PROJECT_DIR="${SCRAPER_PROJECT_DIR:-${ROOT_DIR}/ozone_discount_scraper}"
SCRAPER_VENV_DIR="${SCRAPER_VENV_DIR:-${SCRAPER_PROJECT_DIR}/.venv}"
SCRAPY_EXECUTABLE="${SCRAPY_EXECUTABLE:-${SCRAPER_VENV_DIR}/bin/scrapy}"
SCRAPERS_OUTPUT_DIR="${SCRAPERS_OUTPUT_DIR:-/tmp/discount-market-scrapes}"
SCRAPERS_FIXED_DELAY_MS="${SCRAPERS_FIXED_DELAY_MS:-60000}"
SCRAPERS_SCRAPY_MAX_PAGES="${SCRAPERS_SCRAPY_MAX_PAGES:-1}"
SCRAPERS_SCHEDULER_MAX_PARALLEL_RUNS="${SCRAPERS_SCHEDULER_MAX_PARALLEL_RUNS:-3}"
SPRING_PROFILE="${SPRING_PROFILE:-local}"
# Local test default. Use scrapers.scrapy.max-pages=1000 for deploy/full crawls.
SKIP_DOCKER=false
SKIP_VENV=false
SMOKE_SCRAPER=false
RESET_DB=false

usage() {
    cat <<EOF
Usage: scripts/run-local.sh [options]

Starts the local discount-market app with the Ozone Scrapy runner wired in.

Options:
  --skip-docker      Do not start/wait for Postgres via docker compose.
  --skip-venv        Do not create/install the Scrapy Python virtualenv.
  --smoke-scraper    Run a one-page Scrapy JSONL smoke test before the app.
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
        --skip-docker)
            SKIP_DOCKER=true
            shift
            ;;
        --skip-venv)
            SKIP_VENV=true
            shift
            ;;
        --smoke-scraper)
            SMOKE_SCRAPER=true
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
    if [[ "${SKIP_DOCKER}" == true ]]; then
        if [[ "${RESET_DB}" == true ]]; then
            echo "--reset-db cannot be used together with --skip-docker" >&2
            exit 2
        fi
        log "Skipping docker compose Postgres startup"
        return
    fi

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

run_scraper_smoke_test() {
    if [[ "${SMOKE_SCRAPER}" != true ]]; then
        return
    fi

    mkdir -p "${SCRAPERS_OUTPUT_DIR}"
    local output_file="${SCRAPERS_OUTPUT_DIR}/manual-smoke-$(date '+%Y%m%d%H%M%S').jsonl"

    log "Running Scrapy smoke test"
    (
        cd "${SCRAPER_PROJECT_DIR}"
        "${SCRAPY_EXECUTABLE}" crawl ozone_discounts \
            -a start_urls=https://www.ozone.bg/ \
            -a min_discount=1 \
            -a max_pages=1 \
            -a only_in_stock=true \
            -O "${output_file}"
    )

    log "Scrapy smoke output: ${output_file}"
}

run_app() {
    require_command java
    mkdir -p "${SCRAPERS_OUTPUT_DIR}"

    log "Launching Spring Boot app"
    log "Scrapy executable: ${SCRAPY_EXECUTABLE}"
    log "Scrape output dir: ${SCRAPERS_OUTPUT_DIR}"
    log "Scrapy max pages per start URL: ${SCRAPERS_SCRAPY_MAX_PAGES}"
    log "Max parallel scraper runs: ${SCRAPERS_SCHEDULER_MAX_PARALLEL_RUNS}"
    log "Open API after startup: http://localhost:8080/api/offers"

    local app_args
    app_args="--spring.profiles.active=${SPRING_PROFILE}"
    app_args="${app_args} --scrapers.base-dir=${ROOT_DIR}"
    app_args="${app_args} --scrapers.output-dir=${SCRAPERS_OUTPUT_DIR}"
    app_args="${app_args} --scrapers.scrapy.executable=${SCRAPY_EXECUTABLE}"
    app_args="${app_args} --scrapers.scrapy.max-pages=${SCRAPERS_SCRAPY_MAX_PAGES}"
    app_args="${app_args} --scrapers.scheduler.fixed-delay-ms=${SCRAPERS_FIXED_DELAY_MS}"
    app_args="${app_args} --scrapers.scheduler.max-parallel-runs=${SCRAPERS_SCHEDULER_MAX_PARALLEL_RUNS}"

    (cd "${APP_DIR}" && ./gradlew bootRun --args="${app_args}")
}

setup_scrapy_venv
start_postgres
run_scraper_smoke_test
run_app
