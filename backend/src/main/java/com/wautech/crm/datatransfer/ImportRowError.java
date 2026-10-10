package com.wautech.crm.datatransfer;

public record ImportRowError(int row, String column, String message) { }
