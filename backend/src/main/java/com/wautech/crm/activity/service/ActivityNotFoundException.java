package com.wautech.crm.activity.service;

import java.util.UUID;

public class ActivityNotFoundException extends RuntimeException {
    public ActivityNotFoundException(UUID id) {
        super("Activity with ID '" + id + "' was not found");
    }
}
