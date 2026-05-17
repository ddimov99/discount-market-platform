# Product Scrapers

Small Scrapy project for collecting product cards from Bulgarian online stores.

The spiders export a shared offer schema with fields such as title, URL, brand,
old price, sale price, discount percent, stock flag, labels, category, and image
URL.

## Setup

Run this from the project directory:

```bash
cd /Users/dimitardimov/Desktop/scraping/ozone_discount_scraper
python3 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
```

## Run

Export discounted Ozone products to CSV:

```bash
scrapy crawl ozone_discounts -O ozone_discounts.csv
```

Export to JSON Lines instead:

```bash
scrapy crawl ozone_discounts -O ozone_discounts.jsonl
```

Export Technomarket products to JSON Lines:

```bash
scrapy crawl technomarket_products -O technomarket_products.jsonl
```

Export Technopolis products to JSON Lines:

```bash
scrapy crawl technopolis_products -O technopolis_products.jsonl
```

Export Ardes products to JSON Lines:

```bash
scrapy crawl ardes_products -O ardes_products.jsonl
```

## Configure

Use spider arguments for common filters:

```bash
scrapy crawl ozone_discounts \
  -a start_url=https://www.ozone.bg/ \
  -a min_discount=20 \
  -a max_pages=1 \
  -O ozone_discounts_20_plus.csv
```

Options:

- `start_url`: one URL to scrape.
- `start_urls`: comma-separated URLs to scrape.
- `min_discount`: minimum percentage discount, default `1` for Ozone and `0` for Technomarket.
- `max_pages`: maximum listing pages to follow per start URL, default `1`.
- `discounted_only`: keep only products with a discount, default `true` for Ozone and `false` for Technomarket.
- `only_in_stock`: skip products with an explicit zero stock flag, default `true`.
- `only_last_units`: Ozone only, keep only products marked as last units, default `false`.
- `include_code_discounts`: Ozone only, include labels like `-20% with code`, default `false`.

When the spider is launched by the Spring app, `max_pages` is supplied from
`scrapers.scrapy.max-pages` instead of the database scraper arguments.

You can also set `OZONE_START_URLS` instead of passing `start_url`:

```bash
OZONE_START_URLS=https://www.ozone.bg/ scrapy crawl ozone_discounts -O ozone.csv
```

Only discounted last-units products:

```bash
scrapy crawl ozone_discounts -a only_last_units=true -O ozone_last_units.csv
```

Technomarket uses the same common filters:

```bash
scrapy crawl technomarket_products \
  -a start_url=https://www.technomarket.bg/produkti/telefoni-i-tableti \
  -a discounted_only=false \
  -a max_pages=1 \
  -O technomarket_products.jsonl
```

Technopolis is seeded with only the smartphones and mobile phones category for
now. Its `robots.txt` disallows generic crawlers, so the database config is
created disabled until you have permission or intentionally change crawler
policy.

Ardes allows generic crawlers in `robots.txt`, but rejects Scrapy's default
user agent. The Ardes spider uses a transparent `OffersScraper/1.0` user agent.

The spiders obey `robots.txt`, use a small crawl delay, and keep concurrency low
by default.
