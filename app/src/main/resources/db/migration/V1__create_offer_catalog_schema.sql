CREATE TABLE markets (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(120) NOT NULL,
    base_url TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    scrape_interval_minutes INTEGER NOT NULL DEFAULT 720,
    crawl_delay_seconds INTEGER NOT NULL DEFAULT 1,
    last_scraped_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT uk_markets_slug UNIQUE (slug),
    CONSTRAINT chk_markets_scrape_interval_positive CHECK (scrape_interval_minutes > 0),
    CONSTRAINT chk_markets_crawl_delay_non_negative CHECK (crawl_delay_seconds >= 0)
);

CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    parent_id BIGINT,
    name VARCHAR(160) NOT NULL,
    slug VARCHAR(180) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT uk_categories_slug UNIQUE (slug),
    CONSTRAINT fk_categories_parent_id
        FOREIGN KEY (parent_id)
        REFERENCES categories(id)
);

CREATE TABLE offers (
    id BIGSERIAL PRIMARY KEY,
    market_id BIGINT NOT NULL,
    category_id BIGINT,
    source_product_id VARCHAR(160),
    source_product_key VARCHAR(255) NOT NULL,
    title TEXT NOT NULL,
    product_url TEXT NOT NULL,
    image_url TEXT,
    brand VARCHAR(160),
    currency VARCHAR(3) NOT NULL DEFAULT 'EUR',
    old_price NUMERIC(12, 2),
    sale_price NUMERIC(12, 2) NOT NULL,
    discount_percent NUMERIC(5, 2) NOT NULL,
    stock_status VARCHAR(32) NOT NULL,
    labels TEXT[] NOT NULL DEFAULT '{}',
    metadata JSONB NOT NULL DEFAULT '{}',
    first_seen_at TIMESTAMP NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMP NOT NULL DEFAULT now(),
    last_scraped_at TIMESTAMP NOT NULL DEFAULT now(),
    stale BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT uk_offers_market_source_key UNIQUE (market_id, source_product_key),
    CONSTRAINT fk_offers_market_id
        FOREIGN KEY (market_id)
        REFERENCES markets(id),
    CONSTRAINT fk_offers_category_id
        FOREIGN KEY (category_id)
        REFERENCES categories(id),
    CONSTRAINT chk_offers_stock_status CHECK (
        stock_status IN ('IN_STOCK', 'OUT_OF_STOCK', 'UNKNOWN')
    ),
    CONSTRAINT chk_offers_old_price_non_negative CHECK (old_price IS NULL OR old_price >= 0),
    CONSTRAINT chk_offers_sale_price_non_negative CHECK (sale_price >= 0),
    CONSTRAINT chk_offers_discount_non_negative CHECK (discount_percent >= 0)
);

CREATE TABLE offer_snapshots (
    id BIGSERIAL PRIMARY KEY,
    offer_id BIGINT NOT NULL,
    scraped_at TIMESTAMP NOT NULL DEFAULT now(),
    old_price NUMERIC(12, 2),
    sale_price NUMERIC(12, 2) NOT NULL,
    discount_percent NUMERIC(5, 2) NOT NULL,
    stock_status VARCHAR(32) NOT NULL,

    CONSTRAINT fk_offer_snapshots_offer_id
        FOREIGN KEY (offer_id)
        REFERENCES offers(id)
        ON DELETE CASCADE,
    CONSTRAINT chk_offer_snapshots_stock_status CHECK (
        stock_status IN ('IN_STOCK', 'OUT_OF_STOCK', 'UNKNOWN')
    ),
    CONSTRAINT chk_offer_snapshots_old_price_non_negative CHECK (old_price IS NULL OR old_price >= 0),
    CONSTRAINT chk_offer_snapshots_sale_price_non_negative CHECK (sale_price >= 0),
    CONSTRAINT chk_offer_snapshots_discount_non_negative CHECK (discount_percent >= 0)
);

CREATE TABLE scrape_runs (
    id BIGSERIAL PRIMARY KEY,
    market_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT now(),
    finished_at TIMESTAMP,
    offers_seen INTEGER NOT NULL DEFAULT 0,
    offers_created INTEGER NOT NULL DEFAULT 0,
    offers_updated INTEGER NOT NULL DEFAULT 0,
    offers_marked_stale INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,

    CONSTRAINT fk_scrape_runs_market_id
        FOREIGN KEY (market_id)
        REFERENCES markets(id),
    CONSTRAINT chk_scrape_runs_status CHECK (
        status IN ('RUNNING', 'SUCCESS', 'FAILED')
    ),
    CONSTRAINT chk_scrape_runs_finished_after_started CHECK (
        finished_at IS NULL OR finished_at >= started_at
    ),
    CONSTRAINT chk_scrape_runs_offers_seen_non_negative CHECK (offers_seen >= 0),
    CONSTRAINT chk_scrape_runs_offers_created_non_negative CHECK (offers_created >= 0),
    CONSTRAINT chk_scrape_runs_offers_updated_non_negative CHECK (offers_updated >= 0),
    CONSTRAINT chk_scrape_runs_offers_marked_stale_non_negative CHECK (offers_marked_stale >= 0)
);

CREATE INDEX idx_categories_parent_id ON categories(parent_id);

CREATE INDEX idx_offers_market_id ON offers(market_id);
CREATE INDEX idx_offers_category_id ON offers(category_id);
CREATE INDEX idx_offers_stock_status ON offers(stock_status);
CREATE INDEX idx_offers_discount_percent ON offers(discount_percent DESC);
CREATE INDEX idx_offers_sale_price ON offers(sale_price);
CREATE INDEX idx_offers_last_seen_at ON offers(last_seen_at DESC);
CREATE INDEX idx_offers_stale ON offers(stale);

CREATE INDEX idx_offer_snapshots_offer_id_scraped_at
    ON offer_snapshots(offer_id, scraped_at DESC);

CREATE INDEX idx_scrape_runs_market_id_started_at
    ON scrape_runs(market_id, started_at DESC);
