package com.wautech.crm.task.entity;

public class IllegalTaskStatusTransitionException extends RuntimeException {
    public IllegalTaskStatusTransitionException(TaskStatus current, TaskStatus requested) {
        super("Task status cannot transition from " + current + " to " + requested);
    }
}
