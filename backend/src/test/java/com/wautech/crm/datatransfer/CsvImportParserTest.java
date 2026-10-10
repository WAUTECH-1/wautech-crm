package com.wautech.crm.datatransfer;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvImportParserTest {
    private final CsvImportParser parser = new CsvImportParser();
    private final List<String> headers = List.of("name", "website", "industry", "phone", "email", "status");

    @Test
    void parsesQuotedCommasQuotesAndNewlinesAsOneCsvRecord() {
        String csv = "name,website,industry,phone,email,status\r\n\"A, \"\"B\"\"\",https://x.test,\"line1\nline2\",,a@b.test,ACTIVE\r\n";
        var parsed = parser.parse(csv.getBytes(StandardCharsets.UTF_8), headers);
        assertEquals(1, parsed.totalRows());
        assertEquals("A, \"B\"", parsed.rows().getFirst().values().get("name"));
        assertEquals("line1\nline2", parsed.rows().getFirst().values().get("industry"));
    }

    @Test
    void rejectsMissingColumnsAndDuplicateHeaders() {
        CsvFileException missing = assertThrows(CsvFileException.class, () -> parser.parse(
                "name,email\nA,a@b.test\n".getBytes(StandardCharsets.UTF_8), headers));
        assertEquals("INVALID_HEADER", missing.getFailureCode());
        CsvFileException duplicate = assertThrows(CsvFileException.class, () -> parser.parse(
                "name,name,industry,phone,email,status\nA,B,C,D,E,F\n".getBytes(StandardCharsets.UTF_8), headers));
        assertEquals("INVALID_HEADER", duplicate.getFailureCode());
    }

    @Test
    void rejectsInvalidUtf8OversizedFilesAndExcessiveRows() {
        assertEquals("INVALID_ENCODING", assertThrows(CsvFileException.class,
                () -> parser.parse(new byte[]{(byte) 0xc3, 0x28}, headers)).getFailureCode());
        assertEquals("FILE_TOO_LARGE", assertThrows(CsvFileException.class,
                () -> parser.parse(new byte[CsvImportParser.MAX_FILE_BYTES + 1], headers)).getFailureCode());
        StringBuilder csv = new StringBuilder(String.join(",", headers)).append('\n');
        for (int i = 0; i < CsvImportParser.MAX_ROWS + 1; i++) csv.append("A,,,,,\n");
        assertEquals("TOO_MANY_ROWS", assertThrows(CsvFileException.class,
                () -> parser.parse(csv.toString().getBytes(StandardCharsets.UTF_8), headers)).getFailureCode());
    }

    @Test
    void reportsMalformedRowsAndRepeatedRecords() {
        var parsed = parser.parse(("name,website,industry,phone,email,status\nA,,,,,\nA,,,,,\nB,C\n").getBytes(StandardCharsets.UTF_8), headers);
        assertEquals(3, parsed.totalRows());
        assertEquals(1, parsed.rows().size());
        assertEquals(2, parsed.errors().size());
    }
}
