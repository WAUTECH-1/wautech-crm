package com.wautech.crm.task.dto;

import com.wautech.crm.task.entity.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record TaskStatusRequest(@NotNull TaskStatus status) {
}
