from __future__ import annotations

import os
import re
from datetime import UTC, datetime
from typing import Iterable

import scrapy

from ozone_discount_scraper.items import OzoneDiscountItem


TRUE_VALUES = {"1", "true", "yes", "y", "on"}
LAST_UNITS_LABEL = "\u041f\u043e\u0441\u043b\u0435\u0434\u043d\u0438 \u0431\u0440\u043e\u0439\u043a\u0438"
NEXT_PAGE_LABEL = "\u0421\u043b\u0435\u0434\u0432\u0430\u0449\u0430"
OUT_OF_STOCK_MARKERS = (
    "\u0418\u0437\u0447\u0435\u0440\u043f\u0430\u043d",
    "\u041d\u044f\u043c\u0430 \u043d\u0430\u043b\u0438\u0447\u043d\u043e\u0441\u0442",
    "\u041d\u0435 \u0435 \u043d\u0430\u043b\u0438\u0447\u0435\u043d",
)


class TechnomarketProductsSpider(scrapy.Spider):
    name = "technomarket_products"
    allowed_domains = ["technomarket.bg", "www.technomarket.bg"]
    default_start_urls = [
        "https://www.technomarket.bg/produkti/telefoni-i-tableti",
        "https://www.technomarket.bg/produkti/tv-audio-elektronika",
        "https://www.technomarket.bg/produkti/laptopi-kompuitri-i-periferiya",
        "https://www.technomarket.bg/produkti/domakinski-elektrouredi",
        "https://www.technomarket.bg/produkti/malki-elektrouredi",
        "https://www.technomarket.bg/produkti/uredi-za-zdrave-i-krasota",
        "https://www.technomarket.bg/produkti/klimatici-uredi-za-otoplenie-i-vyzduva",
        "https://www.technomarket.bg/produkti/moda",
        "https://www.technomarket.bg/produkti/zdrave-i-krasota",
        "https://www.technomarket.bg/produkti/dom-i-gradina",
        "https://www.technomarket.bg/produkti/igrachki-i-detski-artikuli",
        "https://www.technomarket.bg/produkti/sport-i-svobodno-vreme",
        "https://www.technomarket.bg/produkti/avto-i-napravi-si-sam",
        "https://www.technomarket.bg/produkti/hobi-i-sport",
        "https://www.technomarket.bg/produkti/knijarnica-i-ofis-konsumativi",
        "https://www.technomarket.bg/produkti/foto-i-video",
        "https://www.technomarket.bg/produkti/avto",
        "https://www.technomarket.bg/produkti/home",
        "https://www.technomarket.bg/produkti/zoomagazin",
    ]

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
        configured_urls = start_urls or start_url or os.getenv("TECHNOMARKET_START_URLS")
        self.start_urls = self._split_urls(configured_urls) or self.default_start_urls
        self.min_discount = float(min_discount)
        self.max_pages = int(max_pages)
        self.discounted_only = self._as_bool(discounted_only)
        self.only_in_stock = self._as_bool(only_in_stock)

    def start_requests(self) -> Iterable[scrapy.Request]:
        for url in self.start_urls:
            yield scrapy.Request(url, callback=self.parse, meta={"page_number": 1})

    def parse(self, response: scrapy.http.Response) -> Iterable[OzoneDiscountItem | scrapy.Request]:
        for card in response.css("tm-product-item"):
            item = self._parse_card(card, response)
            if item is not None:
                yield item

        page_number = int(response.meta.get("page_number", 1))
        if self.max_pages and page_number >= self.max_pages:
            return

        next_url = response.xpath(
            "//div[contains(concat(' ', normalize-space(@class), ' '), ' pages ')]"
            f"/a[.//span[contains(normalize-space(.), '{NEXT_PAGE_LABEL}')]]/@href"
        ).get()
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
        sale_price = self._price_from_selector(card.css(".price-block .price tm-price"))
        if sale_price is None:
            return None

        old_price = self._price_from_selector(card.css(".price-block .old-price tm-price"))
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

        title_link = card.css("a.title")
        product_url = (
            title_link.attrib.get("href")
            or card.css("a.product-image::attr(href)").get()
        )
        title = self._title(card)
        if not title or not product_url:
            return None

        category_path = self._category_path(title_link.attrib.get("data-category"))
        labels = self._labels(card)
        discount_label = f"-{discount_percent:g}%" if discount_percent > 0 else None
        if discount_label:
            labels = [discount_label, *labels]

        return OzoneDiscountItem(
            scraped_at=datetime.now(UTC).isoformat(timespec="seconds"),
            source_url=response.url,
            product_id=card.attrib.get("data-product") or self._code(card),
            title=title,
            product_url=response.urljoin(product_url),
            image_url=self._image_url(card, response),
            brand=self._brand(card),
            currency="EUR",
            old_price=old_price,
            sale_price=sale_price,
            discount_percent=discount_percent,
            discount_label=discount_label,
            labels=labels,
            stock=1 if stock_status == "in_stock" else None,
            stock_status=stock_status,
            is_last_units=any(LAST_UNITS_LABEL in label for label in labels),
            category=category_path[0] if category_path else self._page_category(response),
            subcategory=category_path[-1] if len(category_path) > 1 else None,
            sales_type=self._clean(card.css("a.title .type::text").get()),
            attribute_set=" > ".join(category_path) if category_path else None,
        )

    @classmethod
    def _title(cls, card: scrapy.Selector) -> str | None:
        title = cls._clean(" ".join(card.css("a.title ::text").getall()))
        return title or cls._clean(card.css("a.product-image::attr(aria-label)").get())

    @classmethod
    def _brand(cls, card: scrapy.Selector) -> str | None:
        return (
            cls._clean(card.css("a.title::attr(data-brand)").get())
            or cls._clean(card.css("a.title .brand::text").get())
        )

    @classmethod
    def _labels(cls, card: scrapy.Selector) -> list[str]:
        labels: list[str] = []
        seen: set[str] = set()
        for badge in card.css(".badges .badge"):
            label = cls._clean(" ".join(badge.xpath(".//text()").getall()))
            if not label:
                continue
            normalized = label.casefold()
            if normalized not in seen:
                labels.append(label)
                seen.add(normalized)
        return labels

    @classmethod
    def _category_path(cls, value: str | None) -> list[str]:
        if not value:
            return []
        return [cleaned for part in value.split("|") if (cleaned := cls._clean(part))]

    @classmethod
    def _page_category(cls, response: scrapy.http.Response) -> str | None:
        heading = " ".join(response.css("h1 ::text, h1::text").getall())
        return cls._clean(re.sub(r"\(\d+\)", "", heading))

    @classmethod
    def _code(cls, card: scrapy.Selector) -> str | None:
        return cls._clean(card.css(".code span:last-child::text").get())

    @classmethod
    def _image_url(cls, card: scrapy.Selector, response: scrapy.http.Response) -> str | None:
        image_url = (
            card.css("a.product-image picture img::attr(src)").get()
            or card.css("a.product-image picture img::attr(data-src)").get()
            or card.css("a.product-image > img::attr(src)").get()
            or card.css("a.product-image > img::attr(data-src)").get()
        )
        return response.urljoin(image_url) if image_url else None

    @classmethod
    def _price_from_selector(cls, selector: scrapy.SelectorList) -> float | None:
        text = " ".join(selector.css(".euro_price::text").getall())
        if not text:
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
            separator_index = value.rfind(",")
            decimal_digits = len(value) - separator_index - 1
            if decimal_digits <= 2:
                value = value.replace(",", ".")
            else:
                value = value.replace(",", "")

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
        if card.css('button[data-action="addCart"]'):
            return "in_stock"
        return "unknown"

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
