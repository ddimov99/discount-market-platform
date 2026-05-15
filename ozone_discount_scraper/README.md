# Ozone Discount Scraper

Small Scrapy project for collecting discounted, in-stock product cards from
`https://www.ozone.bg/`.

The default spider reads the Ozone landing/home page, extracts products with a
percentage discount, and exports fields such as title, URL, old price, sale
price, discount percent, stock flag, labels, category, and image URL.

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
- `min_discount`: minimum percentage discount, default `1`.
- `max_pages`: maximum listing pages to follow per start URL, default `1`.
- `only_in_stock`: skip products with an explicit zero stock flag, default `true`.
- `only_last_units`: keep only products marked as last units, default `false`.
- `include_code_discounts`: include labels like `-20% with code`, default `false`.

You can also set `OZONE_START_URLS` instead of passing `start_url`:

```bash
OZONE_START_URLS=https://www.ozone.bg/ scrapy crawl ozone_discounts -O ozone.csv
```

Only discounted last-units products:

```bash
scrapy crawl ozone_discounts -a only_last_units=true -O ozone_last_units.csv
```

The spider obeys `robots.txt`, uses a small crawl delay, and keeps concurrency
low by default.
