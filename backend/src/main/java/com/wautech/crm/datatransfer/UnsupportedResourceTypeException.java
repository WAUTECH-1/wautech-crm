package com.wautech.crm.datatransfer;

public class UnsupportedResourceTypeException extends RuntimeException {
    public UnsupportedResourceTypeException() { super("Unsupported import/export resource"); }
}
