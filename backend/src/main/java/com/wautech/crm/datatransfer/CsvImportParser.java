package com.wautech.crm.datatransfer;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CsvImportParser {
    public static final int MAX_FILE_BYTES = 5 * 1024 * 1024;
    public static final int MAX_ROWS = 5_000;
    private static final int MAX_ERRORS = 200;

    public ParsedCsv parse(byte[] bytes, List<String> expectedHeaders) {
        if (bytes == null || bytes.length == 0) throw new CsvFileException("CSV file is empty", "EMPTY_FILE");
        if (bytes.length > MAX_FILE_BYTES) throw new CsvFileException("CSV file exceeds the 5 MiB limit", "FILE_TOO_LARGE");
        final String content;
        try {
            content = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException invalidEncoding) {
            throw new CsvFileException("CSV file must use UTF-8 encoding", "INVALID_ENCODING", invalidEncoding);
        }
        String csv = content.startsWith("\uFEFF") ? content.substring(1) : content;
        try (CSVParser parser = CSVParser.parse(new StringReader(csv), CSVFormat.DEFAULT.builder()
                .setHeader().setSkipHeaderRecord(true).build())) {
            List<String> headers = parser.getHeaderNames();
            if (headers == null || headers.isEmpty()) throw new CsvFileException("CSV header is required", "INVALID_HEADER");
            Set<String> unique = new HashSet<>();
            if (headers.stream().anyMatch(h -> h == null || h.isBlank() || !unique.add(h))) {
                throw new CsvFileException("CSV header contains a blank or duplicate column", "INVALID_HEADER");
            }
            if (!headers.equals(expectedHeaders)) {
                throw new CsvFileException("CSV columns do not match the required template", "INVALID_HEADER");
            }
            List<CsvRow> rows = new ArrayList<>();
            List<ImportRowError> errors = new ArrayList<>();
            Set<List<String>> seen = new HashSet<>();
            int totalRows = 0;
            for (CSVRecord record : parser) {
                if (++totalRows > MAX_ROWS) {
                    throw new CsvFileException("CSV file exceeds the 5,000 row limit", "TOO_MANY_ROWS");
                }
                int rowNumber = (int) record.getRecordNumber();
                if (!record.isConsistent()) {
                    addError(errors, new ImportRowError(rowNumber, "", "Row has a different number of columns than the header"));
                    continue;
                }
                List<String> values = new ArrayList<>(headers.size());
                Map<String, String> fields = new LinkedHashMap<>();
                for (String header : headers) {
                    String value = record.get(header);
                    values.add(value);
                    fields.put(header, value == null ? "" : value);
                }
                if (!seen.add(List.copyOf(values))) {
                    addError(errors, new ImportRowError(rowNumber, "", "Duplicate row in this CSV file"));
                    continue;
                }
                rows.add(new CsvRow(rowNumber, fields));
            }
            if (rows.isEmpty() && errors.isEmpty()) throw new CsvFileException("CSV file has no data rows", "NO_ROWS");
            return new ParsedCsv(totalRows, List.copyOf(rows), List.copyOf(errors));
        } catch (CsvFileException known) {
            throw known;
        } catch (IOException | RuntimeException malformed) {
            throw new CsvFileException("CSV file is malformed", "MALFORMED_CSV", malformed);
        }
    }

    private static void addError(List<ImportRowError> errors, ImportRowError error) {
        if (errors.size() < MAX_ERRORS) errors.add(error);
    }

    public record CsvRow(int rowNumber, Map<String, String> values) { }
    public record ParsedCsv(int totalRows, List<CsvRow> rows, List<ImportRowError> errors) { }
}
