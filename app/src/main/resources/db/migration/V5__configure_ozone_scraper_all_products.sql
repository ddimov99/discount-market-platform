UPDATE scraper_configs
SET
    arguments = '{
        "min_discount": 0,
        "max_pages": 1000,
        "discounted_only": false,
        "only_in_stock": false,
        "include_code_discounts": true
    }'::jsonb,
    last_success_at = NULL,
    last_error_message = NULL,
    updated_at = now()
WHERE slug = 'ozone-discounts';
