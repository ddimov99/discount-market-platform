package com.offers.app.catalog.scraping;

import static org.assertj.core.api.Assertions.assertThat;

import com.offers.app.catalog.domain.Market;
import com.offers.app.catalog.domain.ScraperConfig;
import com.offers.app.scraper.config.ScraperRuntimeProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScrapyProcessRunnerTest {

    @TempDir
    private Path tempDir;

    @Test
    void buildsScrapyCommandAsArgumentList() {
        ScrapyProcessRunner runner = new ScrapyProcessRunner(
                runtimeProperties(
                        Path.of("/opt/discount-market/scrapers"),
                        Path.of("/var/tmp/scrapes"),
                        "/opt/venv/bin/scrapy",
                        1000),
                fixedClock()
        );

        List<String> command = runner.buildCommand(
                scraperConfig(300),
                Path.of("/var/tmp/scrapes/ozone-discounts-123.jsonl")
        );

        assertThat(command).containsExactly(
                "/opt/venv/bin/scrapy",
                "crawl",
                "ozone_discounts",
                "-a",
                "start_urls=https://www.ozone.bg/",
                "-a",
                "min_discount=1",
                "-a",
                "max_pages=1000",
                "-O",
                "/var/tmp/scrapes/ozone-discounts-123.jsonl"
        );
    }

    @Test
    void runsScrapyProcessAndCapturesResult() throws IOException {
        Path executable = executableScript("""
                #!/bin/sh
                previous=""
                output=""
                for arg in "$@"; do
                  echo "arg=$arg"
                  if [ "$previous" = "-O" ]; then
                    output="$arg"
                  fi
                  previous="$arg"
                done
                echo "stderr=ok" >&2
                printf '{"ok":true}\\n' > "$output"
                exit 0
                """);
        Path baseDir = tempDir.resolve("scrapers");
        Path outputDir = tempDir.resolve("out");
        Files.createDirectories(baseDir.resolve("ozone_discount_scraper"));
        ScrapyProcessRunner runner = new ScrapyProcessRunner(
                runtimeProperties(baseDir, outputDir, executable.toString(), 1),
                fixedClock()
        );

        ScraperRunResult result = runner.run(scraperConfig(300));

        assertThat(result.successful()).isTrue();
        assertThat(result.exitCode()).isZero();
        assertThat(result.outputFile()).isEqualTo(outputDir.resolve("ozone-discounts-123.jsonl"));
        assertThat(result.stdout()).contains("arg=crawl", "arg=ozone_discounts", "arg=-O");
        assertThat(result.stderr()).contains("stderr=ok");
        assertThat(result.errorMessage()).isNull();
        assertThat(result.outputFile()).hasContent("{\"ok\":true}\n");
    }

    @Test
    void returnsFailureWhenProcessTimesOut() throws IOException {
        Path executable = executableScript("""
                #!/bin/sh
                echo "started"
                sleep 3
                """);
        Path baseDir = tempDir.resolve("scrapers");
        Path outputDir = tempDir.resolve("out");
        Files.createDirectories(baseDir.resolve("ozone_discount_scraper"));
        ScrapyProcessRunner runner = new ScrapyProcessRunner(
                runtimeProperties(baseDir, outputDir, executable.toString(), 1),
                fixedClock()
        );

        ScraperRunResult result = runner.run(scraperConfig(1));

        assertThat(result.successful()).isFalse();
        assertThat(result.exitCode()).isEqualTo(-1);
        assertThat(result.stdout()).contains("started");
        assertThat(result.errorMessage()).isEqualTo("Scrapy timed out after 1 seconds");
    }

    @Test
    void returnsClearFailureWhenScrapyExecutableIsMissing() throws IOException {
        Path baseDir = tempDir.resolve("scrapers");
        Path outputDir = tempDir.resolve("out");
        Path projectDir = baseDir.resolve("ozone_discount_scraper");
        Path missingExecutable = tempDir.resolve("missing-scrapy");
        Files.createDirectories(projectDir);
        ScrapyProcessRunner runner = new ScrapyProcessRunner(
                runtimeProperties(baseDir, outputDir, missingExecutable.toString(), 1),
                fixedClock()
        );

        ScraperRunResult result = runner.run(scraperConfig(300));

        assertThat(result.successful()).isFalse();
        assertThat(result.exitCode()).isEqualTo(-1);
        assertThat(result.errorMessage())
                .contains(
                        "Failed to start Scrapy for scraper config 'ozone-discounts'",
                        missingExecutable.toString(),
                        projectDir.toString()
                );
    }

    private Path executableScript(String contents) throws IOException {
        Path executable = tempDir.resolve("fake-scrapy");
        Files.writeString(executable, contents);
        assertThat(executable.toFile().setExecutable(true)).isTrue();
        return executable;
    }

    private ScraperConfig scraperConfig(int timeoutSeconds) {
        ScraperConfig config = new ScraperConfig(
                new Market("Ozone", "ozone", "https://www.ozone.bg/"),
                "Ozone Discounts",
                "ozone-discounts",
                "ozone_discount_scraper",
                "ozone_discounts",
                60,
                timeoutSeconds
        );
        config.setStartUrls(new String[]{"https://www.ozone.bg/"});
        LinkedHashMap<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("min_discount", 1);
        arguments.put("max_pages", 1);
        config.setArguments(arguments);
        return config;
    }

    private ScraperRuntimeProperties runtimeProperties(
            Path baseDir,
            Path outputDir,
            String executable,
            int maxPages
    ) {
        return new ScraperRuntimeProperties(
                baseDir,
                outputDir,
                new ScraperRuntimeProperties.Scrapy(executable, maxPages),
                new ScraperRuntimeProperties.Scheduler(60000, 3)
        );
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.ofEpochMilli(123), ZoneOffset.UTC);
    }
}
