UPDATE scraper_configs
SET
    start_urls = ARRAY[
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
    ],
    arguments = '{"min_discount": 1, "max_pages": 1000, "only_in_stock": true}'::jsonb,
    timeout_seconds = 7200,
    last_success_at = NULL,
    last_error_message = NULL,
    updated_at = now()
WHERE slug = 'ozone-discounts';
