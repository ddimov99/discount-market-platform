INSERT INTO markets (
    name,
    slug,
    base_url
)
VALUES (
    'Technomarket',
    'technomarket',
    'https://www.technomarket.bg/'
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
    'Technomarket products',
    'technomarket-products',
    TRUE,
    'SCRAPY',
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
WHERE market.slug = 'technomarket'
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
