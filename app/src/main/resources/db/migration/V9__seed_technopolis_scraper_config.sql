INSERT INTO markets (
    name,
    slug,
    base_url
)
VALUES (
    'Technopolis',
    'technopolis',
    'https://www.technopolis.bg/'
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
    'Technopolis products',
    'technopolis-products',
    FALSE,
    'SCRAPY',
    'ozone_discount_scraper',
    'technopolis_products',
    ARRAY[
        'https://www.technopolis.bg/bg/Smartfoni-mobilni-telefoni-i-tableti/Smartfoni-i-mobilni-telefoni/c/P11040101'
    ],
    '{
        "min_discount": 0,
        "discounted_only": false,
        "only_in_stock": true
    }'::jsonb,
    'JSONL',
    720,
    3600
FROM markets market
WHERE market.slug = 'technopolis'
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
