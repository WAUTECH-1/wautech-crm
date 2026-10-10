package com.wautech.crm.datatransfer;

import java.util.List;

public class CsvImportValidationException extends RuntimeException {
    private final ImportPreviewResponse result;
    public CsvImportValidationException(ImportPreviewResponse result) {
        super("CSV import contains invalid rows");
        this.result = result;
    }
    public ImportPreviewResponse getResult() { return result; }
}
