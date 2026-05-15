from __future__ import annotations

import os
import re
from datetime import UTC, datetime
from typing import Iterable

import scrapy

from ozone_discount_scraper.items import OzoneDiscountItem


TRUE_VALUES = {"1", "true", "yes", "y", "on"}
LAST_UNITS_LABEL = "\u041f\u043e\u0441\u043b\u0435\u0434\u043d\u0438 \u0431\u0440\u043e\u0439\u043a\u0438"
CODE_LABEL_FRAGMENT = "\u043a\u043e\u0434"


class OzoneDiscountsSpider(scrapy.Spider):
    name = "ozone_discounts"
    allowed_domains = ["ozone.bg", "www.ozone.bg"]
    default_start_urls = ["https://www.ozone.bg/"]

    def __init__(
        self,
        start_url: str | None = None,
        start_urls: str | None = None,
        min_discount: str | int | float = 1,
        max_pages: str | int = 1,
        only_in_stock: str | bool = True,
        only_last_units: str | bool = False,
        include_code_discounts: str | bool = False,
        **kwargs,
    ) -> None:
        super().__init__(**kwargs)
        configured_urls = start_urls or start_url or os.getenv("OZONE_START_URLS")
        self.start_urls = self._split_urls(configured_urls) or self.default_start_urls
        self.min_discount = float(min_discount)
        self.max_pages = int(max_pages)
        self.only_in_stock = self._as_bool(only_in_stock)
        self.only_last_units = self._as_bool(only_last_units)
        self.include_code_discounts = self._as_bool(include_code_discounts)

    def start_requests(self) -> Iterable[scrapy.Request]:
        for url in self.start_urls:
            yield scrapy.Request(url, callback=self.parse, meta={"page_number": 1})

    def parse(self, response: scrapy.http.Response) -> Iterable[OzoneDiscountItem | scrapy.Request]:
        for article in response.css("article.product-item"):
            item = self._parse_article(article, response.url)
            if item is not None:
                yield item

        page_number = int(response.meta.get("page_number", 1))
        if self.max_pages and page_number >= self.max_pages:
            return

        next_url = response.css(
            'a[rel="next"]::attr(href), '
            "a.next::attr(href), "
            ".pages a.next::attr(href), "
            ".pagination a.next::attr(href)"
        ).get()
        if next_url:
            yield response.follow(
                next_url,
                callback=self.parse,
                meta={"page_number": page_number + 1},
            )

    def _parse_article(
        self,
        article: scrapy.Selector,
        source_url: str,
    ) -> OzoneDiscountItem | None:
        script = article.xpath("preceding-sibling::script[1]/text()").get() or ""
        labels = self._clean_list(article.css(".label::text").getall())
        discount_label = self._first_discount_label(labels)
        discount_percent = self._parse_discount_percent(discount_label)
        code_discount = bool(discount_label and CODE_LABEL_FRAGMENT in discount_label.casefold())

        old_price = self._extract_float(script, "unit_price")
        sale_price = self._extract_float(script, "unit_sale_price")
        if old_price is None:
            old_price = self._price_from_selector(article.css(".old-price"))
        if sale_price is None:
            sale_price = self._price_from_selector(article.css(".cur-price"))

        if discount_percent is None and old_price and sale_price and old_price > sale_price:
            discount_percent = round((old_price - sale_price) / old_price * 100, 2)

        if discount_percent is None or discount_percent < self.min_discount:
            return None
        if code_discount and not self.include_code_discounts:
            return None

        stock = self._extract_int(script, "stock")
        if self.only_in_stock and stock is not None and stock <= 0:
            return None

        taxonomy = self._extract_array(script, "taxonomy")
        product_url = article.css("a::attr(href)").get() or self._extract_string(script, "url")
        is_last_units = any(LAST_UNITS_LABEL in label for label in labels)
        if self.only_last_units and not is_last_units:
            return None

        return OzoneDiscountItem(
            scraped_at=datetime.now(UTC).isoformat(timespec="seconds"),
            source_url=source_url,
            product_id=self._extract_string(script, "id"),
            title=self._clean(article.css(".product-ttl::text").get())
            or self._extract_string(script, "name"),
            product_url=product_url,
            image_url=article.css("img.product-img::attr(data-original)").get()
            or article.css("img.product-img::attr(src)").get()
            or self._extract_string(script, "product_image_url"),
            currency=self._extract_string(script, "currency") or "EUR",
            old_price=old_price,
            sale_price=sale_price,
            discount_percent=discount_percent,
            discount_label=discount_label,
            labels=labels,
            stock=stock,
            stock_status=self._stock_status(stock),
            is_last_units=is_last_units,
            category=taxonomy[0] if len(taxonomy) > 0 else None,
            subcategory=taxonomy[1] if len(taxonomy) > 1 else None,
            sales_type=self._extract_custom_string(script, "sales_type"),
            attribute_set=self._extract_custom_string(script, "attribute_set"),
        )

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

    @classmethod
    def _clean_list(cls, values: Iterable[str]) -> list[str]:
        return [cleaned for value in values if (cleaned := cls._clean(value))]

    @staticmethod
    def _first_discount_label(labels: Iterable[str]) -> str | None:
        return next((label for label in labels if "%" in label and "-" in label), None)

    @staticmethod
    def _parse_discount_percent(label: str | None) -> float | None:
        if not label:
            return None
        match = re.search(r"-\s*(\d+(?:[.,]\d+)?)\s*%", label)
        if not match:
            return None
        return float(match.group(1).replace(",", "."))

    @staticmethod
    def _extract_float(script: str, key: str) -> float | None:
        match = re.search(rf"'{re.escape(key)}'\s*:\s*parseFloat\('([^']+)'\)", script)
        if not match:
            return None
        return float(match.group(1))

    @staticmethod
    def _extract_int(script: str, key: str) -> int | None:
        match = re.search(rf"'{re.escape(key)}'\s*:\s*(-?\d+)", script)
        if not match:
            return None
        return int(match.group(1))

    @staticmethod
    def _extract_string(script: str, key: str) -> str | None:
        match = re.search(rf"'{re.escape(key)}'\s*:\s*'((?:\\'|[^'])*)'", script)
        if not match:
            return None
        return match.group(1).replace("\\'", "'")

    @staticmethod
    def _extract_custom_string(script: str, key: str) -> str | None:
        match = re.search(rf"'{re.escape(key)}'\s*:\s*'((?:\\'|[^'])*)'", script)
        if not match:
            return None
        return match.group(1).replace("\\'", "'")

    @staticmethod
    def _extract_array(script: str, key: str) -> list[str]:
        match = re.search(rf"'{re.escape(key)}'\s*:\s*\[([^\]]*)\]", script)
        if not match:
            return []
        return [value.replace("\\'", "'") for value in re.findall(r"'((?:\\'|[^'])*)'", match.group(1))]

    @staticmethod
    def _price_from_selector(selector: scrapy.SelectorList) -> float | None:
        text = " ".join(part.strip() for part in selector.xpath(".//text()").getall() if part.strip())
        match = re.search(r"(\d+)\s*[.,]\s*(\d{2})\s*€", text)
        if not match:
            return None
        return float(f"{match.group(1)}.{match.group(2)}")

    @staticmethod
    def _stock_status(stock: int | None) -> str:
        if stock is None:
            return "unknown"
        if stock > 0:
            return "in_stock"
        return "out_of_stock"
