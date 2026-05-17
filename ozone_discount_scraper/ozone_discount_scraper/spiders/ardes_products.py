from __future__ import annotations

import os
import re
from datetime import UTC, datetime
from typing import Iterable

import scrapy

from ozone_discount_scraper.items import OzoneDiscountItem


TRUE_VALUES = {"1", "true", "yes", "y", "on"}
DEFAULT_START_URLS = [
    "https://ardes.bg/smartfoni/smartfoni",
    "https://ardes.bg/tableti/tableti",
]
BRAND_STOP_WORDS = {
    "GSM",
    "SMARTPHONE",
    "SMARTFON",
    "TABLET",
    "СМАРТФОН",
    "ТАБЛЕТ",
}
OUT_OF_STOCK_MARKERS = (
    "Изчерпан",
    "Няма наличност",
    "Не е наличен",
    "Очаквайте",
)


class ArdesProductsSpider(scrapy.Spider):
    name = "ardes_products"
    allowed_domains = ["ardes.bg", "www.ardes.bg"]
    default_start_urls = DEFAULT_START_URLS
    custom_settings = {
        "USER_AGENT": "OffersScraper/1.0",
    }

    def __init__(
        self,
        start_url: str | None = None,
        start_urls: str | None = None,
        min_discount: str | int | float = 0,
        max_pages: str | int = 1,
        discounted_only: str | bool = False,
        only_in_stock: str | bool = True,
        **kwargs,
    ) -> None:
        super().__init__(**kwargs)
        configured_urls = start_urls or start_url or os.getenv("ARDES_START_URLS")
        self.start_urls = self._split_urls(configured_urls) or self.default_start_urls
        self.min_discount = float(min_discount)
        self.max_pages = int(max_pages)
        self.discounted_only = self._as_bool(discounted_only)
        self.only_in_stock = self._as_bool(only_in_stock)

    def start_requests(self) -> Iterable[scrapy.Request]:
        for url in self.start_urls:
            yield scrapy.Request(url, callback=self.parse, meta={"page_number": 1})

    def parse(self, response: scrapy.http.Response) -> Iterable[OzoneDiscountItem | scrapy.Request]:
        for card in response.css(".products-grid .prod-col .product"):
            item = self._parse_card(card, response)
            if item is not None:
                yield item

        page_number = int(response.meta.get("page_number", 1))
        if self.max_pages and page_number >= self.max_pages:
            return

        next_url = response.css('link[rel="next"]::attr(href), span.next a::attr(href)').get()
        if next_url:
            yield response.follow(
                next_url,
                callback=self.parse,
                meta={"page_number": page_number + 1},
            )

    def _parse_card(
        self,
        card: scrapy.Selector,
        response: scrapy.http.Response,
    ) -> OzoneDiscountItem | None:
        sale_price = self._price_from_selector(card.css(".prices-eur .eur-price .price-num"))
        if sale_price is None:
            sale_price = self._price_from_selector(card.css(".prices-eur .price-num"))
        if sale_price is None:
            return None

        old_price = self._price_from_selector(card.css(".old-price"))
        discount_percent = self._discount_percent(old_price, sale_price)
        if discount_percent is None:
            if self.discounted_only:
                return None
            discount_percent = 0.0
        if discount_percent < self.min_discount:
            return None

        stock_status = self._stock_status(card)
        if self.only_in_stock and stock_status == "out_of_stock":
            return None

        product_url = card.css(".product-head > a::attr(href)").get()
        title = self._title(card)
        if not product_url or not title:
            return None

        category = self._clean(card.attrib.get("data-cat")) or self._page_category(response)
        labels = self._labels(card)
        discount_label = f"-{discount_percent:g}%" if discount_percent > 0 else None
        if discount_label:
            labels = [discount_label, *labels]

        return OzoneDiscountItem(
            scraped_at=datetime.now(UTC).isoformat(timespec="seconds"),
            source_url=response.url,
            product_id=card.css(".stars::attr(data-productid)").get() or self._product_id_from_url(product_url),
            title=title,
            product_url=response.urljoin(product_url),
            image_url=self._image_url(card, response),
            brand=self._brand_from_title(title),
            currency="EUR",
            old_price=old_price,
            sale_price=sale_price,
            discount_percent=discount_percent,
            discount_label=discount_label,
            labels=labels,
            stock=None,
            stock_status=stock_status,
            is_last_units=False,
            category=category,
            subcategory=None,
            sales_type=category,
            attribute_set=category,
        )

    @classmethod
    def _title(cls, card: scrapy.Selector) -> str | None:
        return cls._clean(
            card.css(".title .isTruncated span::text").get()
            or card.css(".product-head img::attr(alt)").get()
        )

    @classmethod
    def _labels(cls, card: scrapy.Selector) -> list[str]:
        labels = [
            cls._clean(label)
            for label in card.css(".badges::text, .badge-accs::text").getall()
        ]
        return cls._dedupe(label for label in labels if label)

    @classmethod
    def _image_url(cls, card: scrapy.Selector, response: scrapy.http.Response) -> str | None:
        image_url = (
            card.css(".product-head .image > img::attr(data-src)").get()
            or card.css(".product-head .image > img::attr(src)").get()
        )
        return response.urljoin(image_url) if image_url else None

    @classmethod
    def _price_from_selector(cls, selector: scrapy.SelectorList) -> float | None:
        text = " ".join(selector.xpath(".//text()").getall())
        return cls._price_from_text(text)

    @staticmethod
    def _price_from_text(text: str | None) -> float | None:
        if not text:
            return None
        match = re.search(r"(\d[\d\s\u00a0,.]*)", text)
        if not match:
            return None
        value = match.group(1).replace("\u00a0", "").replace(" ", "")
        if "," in value and "." in value:
            value = value.replace(",", "")
        elif "," in value:
            value = value.replace(",", ".")
        try:
            return float(value)
        except ValueError:
            return None

    @staticmethod
    def _discount_percent(old_price: float | None, sale_price: float) -> float | None:
        if old_price is None or old_price <= sale_price:
            return None
        return round((old_price - sale_price) / old_price * 100, 2)

    @classmethod
    def _stock_status(cls, card: scrapy.Selector) -> str:
        text = cls._clean(" ".join(card.xpath(".//text()").getall())) or ""
        if any(marker in text for marker in OUT_OF_STOCK_MARKERS):
            return "out_of_stock"
        return "unknown"

    @classmethod
    def _page_category(cls, response: scrapy.http.Response) -> str | None:
        return cls._clean(response.css("h1::text").get())

    @staticmethod
    def _product_id_from_url(product_url: str) -> str | None:
        match = re.search(r"-(\d+)(?:[/?#]|$)", product_url)
        return match.group(1) if match else None

    @staticmethod
    def _brand_from_title(title: str | None) -> str | None:
        if not title:
            return None
        for token in re.findall(r"[A-Za-z][A-Za-z0-9+.-]*", title):
            if token.upper() not in BRAND_STOP_WORDS:
                return token
        return None

    @staticmethod
    def _split_urls(value: str | None) -> list[str]:
        if not value:
            return []
        return [url.strip() for url in value.split(",") if url.strip()]

    @staticmethod
    def _as_bool(value: str | bool) -> bool:
        if isinstance(value, bool):
            return value
        return str(value).strip().casefold() in TRUE_VALUES

    @staticmethod
    def _clean(value: str | None) -> str | None:
        if value is None:
            return None
        cleaned = " ".join(value.split())
        return cleaned or None

    @staticmethod
    def _dedupe(values: Iterable[str]) -> list[str]:
        unique_values: list[str] = []
        seen: set[str] = set()
        for value in values:
            normalized = value.casefold()
            if normalized not in seen:
                unique_values.append(value)
                seen.add(normalized)
        return unique_values
