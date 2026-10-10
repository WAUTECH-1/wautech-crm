package com.wautech.crm.datatransfer;

public class CsvFileException extends RuntimeException {
    private final String failureCode;
    public CsvFileException(String message, String failureCode) { super(message); this.failureCode = failureCode; }
    public CsvFileException(String message, String failureCode, Throwable cause) { super(message, cause); this.failureCode = failureCode; }
    public String getFailureCode() { return failureCode; }
}
