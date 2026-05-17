INSERT INTO markets (
    name,
    slug,
    base_url
)
VALUES (
    'Ozone',
    'ozone',
    'https://www.ozone.bg/'
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
    'Ozone discounts',
    'ozone-discounts',
    TRUE,
    'SCRAPY',
    'ozone_discount_scraper',
    'ozone_discounts',
    ARRAY['https://www.ozone.bg/'],
    '{"min_discount": 1, "max_pages": 1, "only_in_stock": true}'::jsonb,
    'JSONL',
    720,
    900
FROM markets market
WHERE market.slug = 'ozone'
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
