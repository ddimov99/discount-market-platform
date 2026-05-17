INSERT INTO markets (
    name,
    slug,
    base_url
)
VALUES (
    'Ardes',
    'ardes',
    'https://ardes.bg/'
)
ON CONFLICT (slug) DO NOTHING;

INSERT INTO scraper_configs (
    market_id,
    name,
    slug,
    enabled,
    runner_type,
    project_key,
    spider_name,
    start_urls,
    arguments,
    output_format,
    interval_minutes,
    timeout_seconds
)
SELECT
    market.id,
    'Ardes products',
    'ardes-products',
    TRUE,
    'SCRAPY',
    'ozone_discount_scraper',
    'ardes_products',
    ARRAY[
        'https://ardes.bg/smartfoni/smartfoni',
        'https://ardes.bg/tableti/tableti'
    ],
    '{
        "min_discount": 0,
        "discounted_only": false,
        "only_in_stock": true
    }'::jsonb,
    'JSONL',
    720,
    7200
FROM markets market
WHERE market.slug = 'ardes'
ON CONFLICT (slug) DO UPDATE
SET
    market_id = EXCLUDED.market_id,
    name = EXCLUDED.name,
    enabled = EXCLUDED.enabled,
    runner_type = EXCLUDED.runner_type,
    project_key = EXCLUDED.project_key,
    spider_name = EXCLUDED.spider_name,
    start_urls = EXCLUDED.start_urls,
    arguments = EXCLUDED.arguments,
    output_format = EXCLUDED.output_format,
    interval_minutes = EXCLUDED.interval_minutes,
    timeout_seconds = EXCLUDED.timeout_seconds,
    updated_at = now();
