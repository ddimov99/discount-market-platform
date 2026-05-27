from __future__ import annotations

import html
import json
import os
import re
from datetime import UTC, datetime
from typing import Any, Iterable
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

import scrapy

from ozone_discount_scraper.items import OzoneDiscountItem


TRUE_VALUES = {"1", "true", "yes", "y", "on"}
DEFAULT_START_URLS = [
    "https://www.technopolis.bg/bg/Smartfoni-mobilni-telefoni-i-tableti/"
    "Smartfoni-i-mobilni-telefoni/c/P11040101",
]
BRAND_STOP_WORDS = {
    "GSM",
    "SMARTFON",
    "SMARTPHONE",
    "MOBILEN",
    "МОБИЛЕН",
    "ТЕЛЕФОН",
    "СМАРТФОН",
}


class TechnopolisProductsSpider(scrapy.Spider):
    name = "technopolis_products"
    allowed_domains = ["technopolis.bg", "www.technopolis.bg", "api.technopolis.bg"]
    default_start_urls = DEFAULT_START_URLS

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
        configured_urls = start_urls or start_url or os.getenv("TECHNOPOLIS_START_URLS")
        self.start_urls = self._split_urls(configured_urls) or self.default_start_urls
        self.min_discount = float(min_discount)
        self.max_pages = int(max_pages)
        self.discounted_only = self._as_bool(discounted_only)
        self.only_in_stock = self._as_bool(only_in_stock)

    def start_requests(self) -> Iterable[scrapy.Request]:
        for url in self.start_urls:
            yield scrapy.Request(url, callback=self.parse, meta={"page_number": 1})

    def parse(self, response: scrapy.http.Response) -> Iterable[OzoneDiscountItem | scrapy.Request]:
        state_results = self._state_results(response)
        if state_results:
            for product in state_results.products:
                item = self._parse_product(product, state_results, response)
                if item is not None:
                    yield item
            yield from self._pagination_requests(response, state_results)
            return

        for card in response.css("te-product-box"):
            item = self._parse_card(card, response)
            if item is not None:
                yield item

    def _parse_product(
        self,
        product: dict[str, Any],
        state_results: StateResults,
        response: scrapy.http.Response,
    ) -> OzoneDiscountItem | None:
        sale_price = self._price_value(product.get("price"))
        if sale_price is None:
            return None

        old_price = self._old_price(product)
        discount_percent = self._discount_percent(old_price, sale_price)
        if discount_percent is None:
            if self.discounted_only:
                return None
            discount_percent = 0.0
        if discount_percent < self.min_discount:
            return None

        stock_status = self._product_stock_status(product)
        if self.only_in_stock and stock_status == "out_of_stock":
            return None

        category_path = self._category_path(product, state_results)
        labels = self._product_labels(product)
        discount_label = f"-{discount_percent:g}%" if discount_percent > 0 else None
        if discount_label:
            labels = [discount_label, *labels]

        return OzoneDiscountItem(
            scraped_at=datetime.now(UTC).isoformat(timespec="seconds"),
            source_url=response.url,
            product_id=self._string_value(product.get("code")),
            title=self._string_value(product.get("name")),
            product_url=self._product_url(product, response),
            image_url=self._product_image(product, response),
            brand=self._product_brand(product, state_results.selected_brand),
            currency=self._string_value(self._nested(product, "price", "currencyIso")) or "EUR",
            old_price=old_price,
            sale_price=sale_price,
            discount_percent=discount_percent,
            discount_label=discount_label,
            labels=labels,
            stock=1 if stock_status == "in_stock" else None,
            stock_status=stock_status,
            is_last_units=False,
            category=category_path[0] if category_path else None,
            subcategory=category_path[-1] if len(category_path) > 1 else None,
            sales_type=category_path[-1] if category_path else None,
            attribute_set=" > ".join(category_path) if category_path else None,
        )

    def _parse_card(
        self,
        card: scrapy.Selector,
        response: scrapy.http.Response,
    ) -> OzoneDiscountItem | None:
        sale_price = self._price_from_selector(card.css("te-price .product-box__price"))
        if sale_price is None:
            return None

        if self.discounted_only:
            return None
        if self.min_discount > 0:
            return None

        product_url = card.css(".product-box__title-link::attr(href), .product-box__top a::attr(href)").get()
        title = self._clean(
            card.css(".product-box__title-link::attr(title)").get()
            or " ".join(card.css(".product-box__title-link ::text").getall())
        )
        if not product_url or not title:
            return None

        category = self._page_category(response)
        return OzoneDiscountItem(
            scraped_at=datetime.now(UTC).isoformat(timespec="seconds"),
            source_url=response.url,
            product_id=card.attrib.get("data-product-id"),
            title=title,
            product_url=response.urljoin(product_url),
            image_url=self._card_image(card, response),
            brand=self._brand_from_title(title),
            currency="EUR",
            old_price=None,
            sale_price=sale_price,
            discount_percent=0.0,
            discount_label=None,
            labels=self._card_labels(card),
            stock=1 if card.css(".js-product__buy") else None,
            stock_status="in_stock" if card.css(".js-product__buy") else "unknown",
            is_last_units=False,
            category=category,
            subcategory=None,
            sales_type=category,
            attribute_set=category,
        )

    def _pagination_requests(
        self,
        response: scrapy.http.Response,
        state_results: StateResults,
    ) -> Iterable[scrapy.Request]:
        if self.max_pages and state_results.page_number + 1 >= self.max_pages:
            return
        if state_results.total_pages is None or state_results.page_number + 1 >= state_results.total_pages:
            return

        yield response.follow(
            self._page_url(response.url, state_results.page_number + 1),
            callback=self.parse,
            meta={"page_number": state_results.page_number + 2},
        )

    @classmethod
    def _state_results(cls, response: scrapy.http.Response) -> StateResults | None:
        raw_state = response.css("script#ng-state::text").get()
        if not raw_state:
            return None

        try:
            state = json.loads(html.unescape(raw_state))
        except json.JSONDecodeError:
            return None

        results = (
            state.get("cx-state", {})
            .get("product", {})
            .get("search", {})
            .get("results")
        )
        if not isinstance(results, dict):
            return None

        products = results.get("products")
        if not isinstance(products, list):
            return None

        pagination = results.get("pagination") if isinstance(results.get("pagination"), dict) else {}
        page_number = cls._as_int(pagination.get("currentPage")) or 0
        total_pages = cls._as_int(pagination.get("totalPages"))
        breadcrumbs = cls._breadcrumb_names(results.get("breadcrumbDatas"))
        selected_brand = cls._selected_brand(results.get("breadcrumbs"))
        return StateResults(products, breadcrumbs, selected_brand, page_number, total_pages)

    @staticmethod
    def _page_url(url: str, zero_based_page: int) -> str:
        parts = urlsplit(url)
        query = dict(parse_qsl(parts.query, keep_blank_values=True))
        query["currentPage"] = str(zero_based_page)
        return urlunsplit((parts.scheme, parts.netloc, parts.path, urlencode(query), parts.fragment))

    @classmethod
    def _product_url(cls, product: dict[str, Any], response: scrapy.http.Response) -> str | None:
        product_url = cls._string_value(product.get("url"))
        if not product_url:
            return None
        if product_url.startswith("/") and not product_url.startswith("/bg/") and urlsplit(response.url).path.startswith("/bg/"):
            product_url = "/bg" + product_url
        return response.urljoin(product_url)

    @classmethod
    def _product_image(cls, product: dict[str, Any], response: scrapy.http.Response) -> str | None:
        image_url = cls._nested(product, "images", "PRIMARY", "videoluxGrid", "url")
        if not isinstance(image_url, str):
            image_url = cls._nested(product, "images", "PRIMARY", "product", "url")
        return response.urljoin(image_url) if isinstance(image_url, str) else None

    @classmethod
    def _product_brand(cls, product: dict[str, Any], selected_brand: str | None) -> str | None:
        for value in (
            product.get("brand"),
            product.get("manufacturer"),
            product.get("vendor"),
            selected_brand,
        ):
            if cleaned := cls._string_value(value):
                return cleaned
        return cls._brand_from_title(cls._string_value(product.get("name")))

    @classmethod
    def _product_labels(cls, product: dict[str, Any]) -> list[str]:
        labels: list[str] = []
        for promotion in product.get("potentialPromotions", []):
            if isinstance(promotion, dict):
                labels.append(cls._string_value(promotion.get("title")) or cls._string_value(promotion.get("description")))
        if product.get("onlineExclusive"):
            labels.append("Online exclusive")
        if product.get("onlineOnly"):
            labels.append("Online only")
        if product.get("markNew"):
            labels.append("New")
        if cls._as_bool(product.get("expressDelivery")):
            labels.append("Express delivery")
        return cls._dedupe(label for label in labels if label)

    @classmethod
    def _category_path(cls, product: dict[str, Any], state_results: StateResults) -> list[str]:
        if state_results.breadcrumbs:
            return state_results.breadcrumbs
        categories = product.get("categories")
        if isinstance(categories, list):
            return [
                cleaned
                for category in categories
                if isinstance(category, dict) and (cleaned := cls._string_value(category.get("name")))
            ]
        return []

    @classmethod
    def _old_price(cls, product: dict[str, Any]) -> float | None:
        for key in ("oldPrice", "strikeThroughPrice", "wasPrice", "regularPrice", "listPrice"):
            if old_price := cls._price_value(product.get(key)):
                return old_price
        return None

    @classmethod
    def _price_value(cls, value: Any) -> float | None:
        if isinstance(value, dict):
            value = value.get("value") or value.get("formattedValue")
        if isinstance(value, bool) or value is None:
            return None
        if isinstance(value, (int, float)):
            return float(value)
        if isinstance(value, str):
            return cls._price_from_text(value)
        return None

    @classmethod
    def _price_from_selector(cls, selector: scrapy.SelectorList) -> float | None:
        euro_price = selector.xpath(".//*[contains(normalize-space(.), '€')]/text()").get()
        if euro_price:
            return cls._price_from_text(euro_price)
        return cls._price_from_text(" ".join(selector.xpath(".//text()").getall()))

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
    def _product_stock_status(cls, product: dict[str, Any]) -> str:
        if cls._as_bool(product.get("soldOut")):
            return "out_of_stock"
        stock_status = cls._string_value(cls._nested(product, "stock", "stockLevelStatus"))
        if stock_status:
            normalized = stock_status.replace(" ", "").casefold()
            if normalized in {"instock", "lowstock"}:
                return "in_stock"
            if normalized in {"outofstock", "nostock"}:
                return "out_of_stock"
        if cls._as_bool(product.get("purchasable")) or cls._as_bool(product.get("canBye")):
            return "in_stock"
        return "unknown"

    @classmethod
    def _card_labels(cls, card: scrapy.Selector) -> list[str]:
        labels = [
            cls._clean(" ".join(label.xpath(".//text()").getall()))
            for label in card.css(".item-label, .delivery-label__text")
        ]
        return cls._dedupe(label for label in labels if label)

    @classmethod
    def _card_image(cls, card: scrapy.Selector, response: scrapy.http.Response) -> str | None:
        image_url = (
            card.css(".product-box__top img::attr(src)").get()
            or card.css(".product-box__top img::attr(srcset)").get()
        )
        return response.urljoin(image_url) if image_url else None

    @classmethod
    def _page_category(cls, response: scrapy.http.Response) -> str | None:
        heading = " ".join(response.css("h1 ::text, h1::text").getall())
        heading = re.sub(r"\bНамерени:\s*\d+\b", "", heading)
        return cls._clean(heading)

    @staticmethod
    def _breadcrumb_names(value: Any) -> list[str]:
        if not isinstance(value, list):
            return []
        return [
            cleaned
            for item in value
            if isinstance(item, dict) and (cleaned := TechnopolisProductsSpider._string_value(item.get("name")))
        ]

    @staticmethod
    def _selected_brand(value: Any) -> str | None:
        if not isinstance(value, list):
            return None
        selected = [
            TechnopolisProductsSpider._string_value(item.get("facetValueName"))
            for item in value
            if isinstance(item, dict) and item.get("facetCode") == "brand"
        ]
        selected = [brand for brand in selected if brand]
        return selected[0] if len(selected) == 1 else None

    @staticmethod
    def _brand_from_title(title: str | None) -> str | None:
        if not title:
            return None
        for token in re.findall(r"[A-Za-z][A-Za-z0-9+.-]*", title):
            normalized = token.upper()
            if normalized not in BRAND_STOP_WORDS and len(normalized) > 1:
                return normalized
        return None

    @staticmethod
    def _nested(value: Any, *keys: str) -> Any:
        current = value
        for key in keys:
            if not isinstance(current, dict):
                return None
            current = current.get(key)
        return current

    @staticmethod
    def _split_urls(value: str | None) -> list[str]:
        if not value:
            return []
        return [url.strip() for url in value.split(",") if url.strip()]

    @staticmethod
    def _as_bool(value: Any) -> bool:
        if isinstance(value, bool):
            return value
        if value is None:
            return False
        return str(value).strip().casefold() in TRUE_VALUES

    @staticmethod
    def _as_int(value: Any) -> int | None:
        if isinstance(value, bool) or value is None:
            return None
        if isinstance(value, int):
            return value
        if isinstance(value, float):
            return int(value)
        if isinstance(value, str):
            try:
                return int(value)
            except ValueError:
                return None
        return None

    @classmethod
    def _string_value(cls, value: Any) -> str | None:
        if isinstance(value, str):
            return cls._clean(value)
        return None

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


class StateResults:
    def __init__(
        self,
        products: list[dict[str, Any]],
        breadcrumbs: list[str],
        selected_brand: str | None,
        page_number: int,
        total_pages: int | None,
    ) -> None:
        self.products = products
        self.breadcrumbs = breadcrumbs
        self.selected_brand = selected_brand
        self.page_number = page_number
        self.total_pages = total_pages
