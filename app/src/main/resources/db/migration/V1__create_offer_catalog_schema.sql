CREATE TABLE markets (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(120) NOT NULL,
    base_url TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_scraped_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT uk_markets_slug UNIQUE (slug)
);

CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    parent_id BIGINT,
    name VARCHAR(160) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT fk_categories_parent_id
        FOREIGN KEY (parent_id)
        REFERENCES categories(id),
    CONSTRAINT uk_categories_name_parent_id UNIQUE NULLS NOT DISTINCT (name, parent_id)
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
    source_category VARCHAR(255),
    source_subcategory VARCHAR(255),
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

CREATE TABLE scraper_configs (
    id BIGSERIAL PRIMARY KEY,
    market_id BIGINT NOT NULL,
    name VARCHAR(160) NOT NULL,
    slug VARCHAR(180) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    project_key VARCHAR(160) NOT NULL,
    spider_name VARCHAR(160) NOT NULL,
    start_urls TEXT[] NOT NULL DEFAULT '{}',
    arguments JSONB NOT NULL DEFAULT '{}',
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
    CONSTRAINT chk_scraper_configs_interval_positive CHECK (interval_minutes > 0),
    CONSTRAINT chk_scraper_configs_timeout_positive CHECK (timeout_seconds > 0),
    CONSTRAINT chk_scraper_configs_last_finished_after_started CHECK (
        last_finished_at IS NULL
        OR last_started_at IS NULL
        OR last_finished_at >= last_started_at
    )
);

CREATE TABLE scrape_runs (
    id BIGSERIAL PRIMARY KEY,
    market_id BIGINT NOT NULL,
    scraper_config_id BIGINT,
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
    CONSTRAINT fk_scrape_runs_scraper_config_id
        FOREIGN KEY (scraper_config_id)
        REFERENCES scraper_configs(id),
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
CREATE INDEX idx_offers_source_category ON offers(source_category);
CREATE INDEX idx_offers_source_subcategory ON offers(source_subcategory);
CREATE INDEX idx_offers_market_source_subcategory ON offers(market_id, source_subcategory);
CREATE INDEX idx_offers_brand_lower
    ON offers (lower(brand))
    WHERE brand IS NOT NULL;
CREATE INDEX idx_offers_category_stale ON offers(category_id, stale);

CREATE INDEX idx_offer_snapshots_offer_id_scraped_at
    ON offer_snapshots(offer_id, scraped_at DESC);

CREATE INDEX idx_scraper_configs_market_id ON scraper_configs(market_id);
CREATE INDEX idx_scraper_configs_enabled ON scraper_configs(enabled);

CREATE INDEX idx_scrape_runs_market_id_started_at
    ON scrape_runs(market_id, started_at DESC);
CREATE INDEX idx_scrape_runs_scraper_config_id_started_at
    ON scrape_runs(scraper_config_id, started_at DESC);

INSERT INTO markets (
    name,
    slug,
    base_url
)
VALUES
    ('Ozone', 'ozone', 'https://www.ozone.bg/'),
    ('Technomarket', 'technomarket', 'https://www.technomarket.bg/'),
    ('Technopolis', 'technopolis', 'https://www.technopolis.bg/'),
    ('Ardes', 'ardes', 'https://ardes.bg/');

INSERT INTO scraper_configs (
    market_id,
    name,
    slug,
    enabled,
    project_key,
    spider_name,
    start_urls,
    arguments,
    interval_minutes,
    timeout_seconds
)
SELECT
    market.id,
    config.name,
    config.slug,
    config.enabled,
    config.project_key,
    config.spider_name,
    config.start_urls,
    config.arguments,
    config.interval_minutes,
    config.timeout_seconds
FROM (
    VALUES
        (
            'ozone',
            'Ozone discounts',
            'ozone-discounts',
            TRUE,
            'ozone_discount_scraper',
            'ozone_discounts',
            ARRAY[
                'https://www.ozone.bg/gaming/',
                'https://www.ozone.bg/pazeli-2d-3d/',
                'https://www.ozone.bg/filmi/',
                'https://www.ozone.bg/drehi-merchandise/',
                'https://www.ozone.bg/knijarnica/',
                'https://www.ozone.bg/posobiya-i-podaratsi/',
                'https://www.ozone.bg/idei-za-podaratsi/',
                'https://www.ozone.bg/laptopi-monitori-i-kompyutri/',
                'https://www.ozone.bg/mobilni-ustroistva/',
                'https://www.ozone.bg/tv-foto-i-video/',
                'https://www.ozone.bg/igrachki-i-pazeli/',
                'https://www.ozone.bg/mama-i-bebe/',
                'https://www.ozone.bg/kozmetika/',
                'https://www.ozone.bg/apteka-i-hranitelni-dobavki/',
                'https://www.ozone.bg/sport-i-autdor/',
                'https://www.ozone.bg/dvor-i-gradina/',
                'https://www.ozone.bg/instrumenti/',
                'https://www.ozone.bg/dom-i-gradina/',
                'https://www.ozone.bg/malki-elektrouredi/'
            ]::text[],
            '{
                "min_discount": 0,
                "discounted_only": false,
                "only_in_stock": true,
                "include_code_discounts": true
            }'::jsonb,
            720,
            7200
        ),
        (
            'technomarket',
            'Technomarket products',
            'technomarket-products',
            TRUE,
            'ozone_discount_scraper',
            'technomarket_products',
            ARRAY[
                'https://www.technomarket.bg/produkti/telefoni-i-tableti',
                'https://www.technomarket.bg/produkti/tv-audio-elektronika',
                'https://www.technomarket.bg/produkti/laptopi-kompuitri-i-periferiya',
                'https://www.technomarket.bg/produkti/domakinski-elektrouredi',
                'https://www.technomarket.bg/produkti/malki-elektrouredi',
                'https://www.technomarket.bg/produkti/uredi-za-zdrave-i-krasota',
                'https://www.technomarket.bg/produkti/klimatici-uredi-za-otoplenie-i-vyzduva',
                'https://www.technomarket.bg/produkti/moda',
                'https://www.technomarket.bg/produkti/zdrave-i-krasota',
                'https://www.technomarket.bg/produkti/dom-i-gradina',
                'https://www.technomarket.bg/produkti/igrachki-i-detski-artikuli',
                'https://www.technomarket.bg/produkti/sport-i-svobodno-vreme',
                'https://www.technomarket.bg/produkti/avto-i-napravi-si-sam',
                'https://www.technomarket.bg/produkti/hobi-i-sport',
                'https://www.technomarket.bg/produkti/knijarnica-i-ofis-konsumativi',
                'https://www.technomarket.bg/produkti/foto-i-video',
                'https://www.technomarket.bg/produkti/avto',
                'https://www.technomarket.bg/produkti/home',
                'https://www.technomarket.bg/produkti/zoomagazin'
            ]::text[],
            '{
                "min_discount": 0,
                "discounted_only": false,
                "only_in_stock": true
            }'::jsonb,
            720,
            7200
        ),
        (
            'technopolis',
            'Technopolis products',
            'technopolis-products',
            FALSE,
            'ozone_discount_scraper',
            'technopolis_products',
            ARRAY[
                'https://www.technopolis.bg/bg/Smartfoni-mobilni-telefoni-i-tableti/Smartfoni-i-mobilni-telefoni/c/P11040101'
            ]::text[],
            '{
                "min_discount": 0,
                "discounted_only": false,
                "only_in_stock": true
            }'::jsonb,
            720,
            3600
        ),
        (
            'ardes',
            'Ardes products',
            'ardes-products',
            TRUE,
            'ozone_discount_scraper',
            'ardes_products',
            ARRAY[
                'https://ardes.bg/smartfoni/smartfoni',
                'https://ardes.bg/tableti/tableti'
            ]::text[],
            '{
                "min_discount": 0,
                "discounted_only": false,
                "only_in_stock": true
            }'::jsonb,
            720,
            7200
        )
) AS config(
    market_slug,
    name,
    slug,
    enabled,
    project_key,
    spider_name,
    start_urls,
    arguments,
    interval_minutes,
    timeout_seconds
)
JOIN markets market ON market.slug = config.market_slug;
