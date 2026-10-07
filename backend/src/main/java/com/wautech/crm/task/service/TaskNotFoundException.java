package com.wautech.crm.task.service;

import java.util.UUID;

public class TaskNotFoundException extends RuntimeException {
    public TaskNotFoundException(UUID id) {
        super("Task with ID '" + id + "' was not found");
    }
}
