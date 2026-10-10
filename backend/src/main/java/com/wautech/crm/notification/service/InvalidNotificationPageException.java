package com.wautech.crm.notification.service;

public class InvalidNotificationPageException extends RuntimeException {
    public InvalidNotificationPageException() {
        super("Page must be non-negative and size must be between 1 and 100");
    }
}
