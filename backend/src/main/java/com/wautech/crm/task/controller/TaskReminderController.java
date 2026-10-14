package com.wautech.crm.task.controller;

import com.wautech.crm.platform.tenant.AuthenticatedOrganizationContext;
import com.wautech.crm.task.service.TaskReminderService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks/reminders")
public class TaskReminderController {
  private final TaskReminderService service;
  private final AuthenticatedOrganizationContext context;

  public TaskReminderController(TaskReminderService s, AuthenticatedOrganizationContext c) {
    service = s;
    context = c;
  }

  @PostMapping("/check")
  public Map<String, Integer> check() {
    return Map.of("checkedCount", service.checkMine(context.requireOrganizationId()));
  }
}
