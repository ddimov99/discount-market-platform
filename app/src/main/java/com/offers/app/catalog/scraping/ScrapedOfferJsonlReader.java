package com.offers.app.catalog.scraping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ScrapedOfferJsonlReader {

    private final ObjectReader objectReader;

    public ScrapedOfferJsonlReader(ObjectMapper objectMapper) {
        this.objectReader = objectMapper.readerFor(ScrapedOfferDto.class);
    }

    public List<ScrapedOfferDto> read(Path path) throws IOException {
        List<ScrapedOfferDto> offers = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                offers.add(readLine(path, lineNumber, line));
            }
        }

        return offers;
    }

    private ScrapedOfferDto readLine(Path path, int lineNumber, String line) throws IOException {
        try {
            return objectReader.readValue(line);
        } catch (JsonProcessingException ex) {
            throw new IOException("Failed to parse JSONL item in " + path + " at line " + lineNumber, ex);
        }
    }
}
