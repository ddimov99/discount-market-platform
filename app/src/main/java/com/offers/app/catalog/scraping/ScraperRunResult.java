package com.offers.app.catalog.scraping;

import java.nio.file.Path;

public record ScraperRunResult(
        Path outputFile,
        int exitCode,
        String stdout,
        String stderr,
        String errorMessage
) {

    public boolean successful() {
        return exitCode == 0 && errorMessage == null;
    }
}
