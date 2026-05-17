package com.offers.app;

import com.offers.app.catalog.repository.CategoryRepository;
import com.offers.app.catalog.repository.MarketRepository;
import com.offers.app.catalog.repository.OfferRepository;
import com.offers.app.catalog.repository.OfferSnapshotRepository;
import com.offers.app.catalog.repository.ScrapeRunRepository;
import com.offers.app.catalog.repository.ScraperConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude="
				+ "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
				+ "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
				+ "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
				+ "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"
})
class AppApplicationTests {

	@MockitoBean
	private OfferRepository offerRepository;

	@MockitoBean
	private OfferSnapshotRepository offerSnapshotRepository;

	@MockitoBean
	private CategoryRepository categoryRepository;

	@MockitoBean
	private MarketRepository marketRepository;

	@MockitoBean
	private ScraperConfigRepository scraperConfigRepository;

	@MockitoBean
	private ScrapeRunRepository scrapeRunRepository;

	@Test
	void contextLoads() {
	}

}
