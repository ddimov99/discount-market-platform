UPDATE scraper_configs
SET
    arguments = jsonb_set(arguments, '{only_in_stock}', 'true'::jsonb, true),
    last_success_at = NULL,
    last_error_message = NULL,
    updated_at = now()
WHERE slug = 'ozone-discounts';
