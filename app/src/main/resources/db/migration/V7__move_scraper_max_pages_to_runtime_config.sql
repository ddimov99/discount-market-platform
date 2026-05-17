UPDATE scraper_configs
SET
    arguments = arguments - 'max_pages',
    updated_at = now()
WHERE arguments ? 'max_pages';
