import scrapy


class OzoneDiscountItem(scrapy.Item):
    scraped_at = scrapy.Field()
    source_url = scrapy.Field()
    product_id = scrapy.Field()
    title = scrapy.Field()
    product_url = scrapy.Field()
    image_url = scrapy.Field()
    currency = scrapy.Field()
    old_price = scrapy.Field()
    sale_price = scrapy.Field()
    discount_percent = scrapy.Field()
    discount_label = scrapy.Field()
    labels = scrapy.Field()
    stock = scrapy.Field()
    stock_status = scrapy.Field()
    is_last_units = scrapy.Field()
    category = scrapy.Field()
    subcategory = scrapy.Field()
    sales_type = scrapy.Field()
    attribute_set = scrapy.Field()
