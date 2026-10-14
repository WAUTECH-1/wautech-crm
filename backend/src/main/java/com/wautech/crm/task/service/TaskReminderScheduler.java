package com.wautech.crm.task.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

/** Opt-in single-instance scheduler; manual checks use the same trusted domain service. */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.reminders.enabled", havingValue = "true")
public class TaskReminderScheduler {
  private final TaskReminderService service;

  public TaskReminderScheduler(TaskReminderService s) {
    service = s;
  }

  @Scheduled(fixedDelayString = "60000", initialDelayString = "60000")
  public void tick() {
    service.checkScheduled();
  }
}
