package com.wautech.crm.savedview.service;

import java.util.UUID;

public class SavedViewNotFoundException extends RuntimeException {
    public SavedViewNotFoundException(UUID id) {
        super("Saved View not found: " + id);
    }
}
