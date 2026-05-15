BOT_NAME = "ozone_discount_scraper"

SPIDER_MODULES = ["ozone_discount_scraper.spiders"]
NEWSPIDER_MODULE = "ozone_discount_scraper.spiders"

ROBOTSTXT_OBEY = True

CONCURRENT_REQUESTS = 4
CONCURRENT_REQUESTS_PER_DOMAIN = 2
DOWNLOAD_DELAY = 1
RANDOMIZE_DOWNLOAD_DELAY = True
LOG_LEVEL = "INFO"

DEFAULT_REQUEST_HEADERS = {
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "bg-BG,bg;q=0.9,en;q=0.8",
}

FEED_EXPORT_ENCODING = "utf-8"
TELNETCONSOLE_ENABLED = False

DOWNLOAD_HANDLERS = {
    "http": "scrapy.core.downloader.handlers._httpx.HttpxDownloadHandler",
    "https": "scrapy.core.downloader.handlers._httpx.HttpxDownloadHandler",
}

REQUEST_FINGERPRINTER_IMPLEMENTATION = "2.7"
TWISTED_REACTOR = "twisted.internet.asyncioreactor.AsyncioSelectorReactor"
