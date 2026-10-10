package com.wautech.crm.datatransfer;

import java.util.Arrays;

public enum ResourceType {
    COMPANIES("companies"), CONTACTS("contacts"), LEADS("leads"), OPPORTUNITIES("opportunities");

    private final String path;
    ResourceType(String path) { this.path = path; }
    public String path() { return path; }

    public static ResourceType fromPath(String path) {
        return Arrays.stream(values()).filter(value -> value.path.equals(path)).findFirst()
                .orElseThrow(() -> new UnsupportedResourceTypeException());
    }
}
