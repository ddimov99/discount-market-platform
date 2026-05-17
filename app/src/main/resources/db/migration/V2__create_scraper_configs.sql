CREATE TABLE scraper_configs (
    id BIGSERIAL PRIMARY KEY,
    market_id BIGINT NOT NULL,
    name VARCHAR(160) NOT NULL,
    slug VARCHAR(180) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    runner_type VARCHAR(32) NOT NULL DEFAULT 'SCRAPY',
    project_key VARCHAR(160) NOT NULL,
    spider_name VARCHAR(160) NOT NULL,
    start_urls TEXT[] NOT NULL DEFAULT '{}',
    arguments JSONB NOT NULL DEFAULT '{}',
    output_format VARCHAR(32) NOT NULL DEFAULT 'JSONL',
    interval_minutes INTEGER NOT NULL,
    timeout_seconds INTEGER NOT NULL,
    last_started_at TIMESTAMP,
    last_finished_at TIMESTAMP,
    last_success_at TIMESTAMP,
    last_error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT uk_scraper_configs_slug UNIQUE (slug),
    CONSTRAINT fk_scraper_configs_market_id
        FOREIGN KEY (market_id)
        REFERENCES markets(id),
    CONSTRAINT chk_scraper_configs_runner_type CHECK (
        runner_type IN ('SCRAPY')
    ),
    CONSTRAINT chk_scraper_configs_output_format CHECK (
        output_format IN ('JSONL')
    ),
    CONSTRAINT chk_scraper_configs_interval_positive CHECK (interval_minutes > 0),
    CONSTRAINT chk_scraper_configs_timeout_positive CHECK (timeout_seconds > 0),
    CONSTRAINT chk_scraper_configs_last_finished_after_started CHECK (
        last_finished_at IS NULL
        OR last_started_at IS NULL
        OR last_finished_at >= last_started_at
    )
);

ALTER TABLE scrape_runs
ADD COLUMN scraper_config_id BIGINT;

ALTER TABLE scrape_runs
ADD CONSTRAINT fk_scrape_runs_scraper_config_id
    FOREIGN KEY (scraper_config_id)
    REFERENCES scraper_configs(id);

CREATE INDEX idx_scraper_configs_market_id ON scraper_configs(market_id);
CREATE INDEX idx_scraper_configs_enabled ON scraper_configs(enabled);

CREATE INDEX idx_scrape_runs_scraper_config_id_started_at
    ON scrape_runs(scraper_config_id, started_at DESC);
