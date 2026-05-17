package com.offers.app.catalog.scraping;

import com.offers.app.catalog.domain.ScraperConfig;
import com.offers.app.scraper.config.ScraperRuntimeProperties;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ScrapyProcessRunner implements ScraperRunner {

    private static final String START_URLS_ARGUMENT = "start_urls";
    private static final String MAX_PAGES_ARGUMENT = "max_pages";

    private final ScraperRuntimeProperties runtimeProperties;
    private final Clock clock;

    @Autowired
    public ScrapyProcessRunner(ScraperRuntimeProperties runtimeProperties) {
        this(runtimeProperties, Clock.systemUTC());
    }

    ScrapyProcessRunner(ScraperRuntimeProperties runtimeProperties, Clock clock) {
        this.runtimeProperties = runtimeProperties;
        this.clock = clock;
    }

    @Override
    public ScraperRunResult run(ScraperConfig config) {
        Path outputFile = outputFile(config);

        try {
            Files.createDirectories(runtimeProperties.outputDir());

            Process process = new ProcessBuilder(buildCommand(config, outputFile))
                    .directory(runtimeProperties.resolveProjectDir(config.getProjectKey()).toFile())
                    .start();

            CompletableFuture<String> stdout = readAsync(process.getInputStream());
            CompletableFuture<String> stderr = readAsync(process.getErrorStream());

            boolean finished = process.waitFor(config.getTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                stop(process);
                return new ScraperRunResult(
                        outputFile,
                        -1,
                        collect(stdout),
                        collect(stderr),
                        "Scrapy timed out after " + config.getTimeoutSeconds() + " seconds"
                );
            }

            int exitCode = process.exitValue();
            String stderrLog = collect(stderr);
            return new ScraperRunResult(
                    outputFile,
                    exitCode,
                    collect(stdout),
                    stderrLog,
                    exitCode == 0 ? null : "Scrapy exited with code " + exitCode
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return failed(outputFile, "Scrapy execution was interrupted");
        } catch (IOException ex) {
            return failed(outputFile, ex.getMessage());
        }
    }

    List<String> buildCommand(ScraperConfig config, Path outputFile) {
        List<String> command = new ArrayList<>();
        command.add(runtimeProperties.scrapy().executable());
        command.add("crawl");
        command.add(config.getSpiderName());

        if (config.getStartUrls() != null && config.getStartUrls().length > 0) {
            command.add("-a");
            command.add(START_URLS_ARGUMENT + "=" + String.join(",", config.getStartUrls()));
        }

        Map<String, Object> arguments = config.getArguments();
        if (arguments != null) {
            arguments.forEach((key, value) -> {
                if (value != null && !START_URLS_ARGUMENT.equals(key) && !MAX_PAGES_ARGUMENT.equals(key)) {
                    command.add("-a");
                    command.add(key + "=" + value);
                }
            });
        }

        command.add("-a");
        command.add(MAX_PAGES_ARGUMENT + "=" + runtimeProperties.scrapy().maxPages());

        command.add("-O");
        command.add(outputFile.toString());
        return command;
    }

    private Path outputFile(ScraperConfig config) {
        return runtimeProperties.outputDir()
                .resolve(config.getSlug() + "-" + clock.millis() + ".jsonl")
                .normalize();
    }

    private CompletableFuture<String> readAsync(InputStream inputStream) {
        return CompletableFuture.supplyAsync(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                }
                return output.toString();
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to capture process output", ex);
            }
        });
    }

    private void stop(Process process) throws InterruptedException {
        process.destroy();
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor();
        }
    }

    private String collect(CompletableFuture<String> output) throws InterruptedException {
        try {
            return output.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException ex) {
            return "";
        }
    }

    private ScraperRunResult failed(Path outputFile, String errorMessage) {
        return new ScraperRunResult(
                outputFile,
                -1,
                "",
                "",
                Objects.requireNonNullElse(errorMessage, "Scrapy execution failed")
        );
    }
}
