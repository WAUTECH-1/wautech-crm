package com.wautech.crm.datatransfer;

import java.util.List;

public record ImportPreviewResponse(int totalRows, int validRows, int invalidRows, List<ImportRowError> errors) { }
