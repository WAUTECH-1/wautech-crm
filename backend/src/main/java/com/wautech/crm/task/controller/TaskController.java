package com.wautech.crm.task.controller;

import com.wautech.crm.task.dto.TaskRequest;
import com.wautech.crm.task.dto.TaskResponse;
import com.wautech.crm.task.dto.TaskStatusRequest;
import com.wautech.crm.task.entity.TaskPriority;
import com.wautech.crm.task.entity.TaskStatus;
import com.wautech.crm.task.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@Valid @RequestBody TaskRequest request) {
        return taskService.create(request);
    }

    @GetMapping
    public List<TaskResponse> listActive(@RequestParam(required = false) UUID companyId,
                                         @RequestParam(required = false) UUID contactId,
                                         @RequestParam(required = false) UUID leadId,
                                         @RequestParam(required = false) UUID opportunityId,
                                         @RequestParam(required = false) TaskStatus status,
                                         @RequestParam(required = false) TaskPriority priority,
                                         @RequestParam(required = false) Instant dueBefore,
                                         @RequestParam(required = false) Instant dueAfter,
                                         @RequestParam(required = false) Boolean overdue) {
        return taskService.listActive(companyId, contactId, leadId, opportunityId, status, priority,
                dueBefore, dueAfter, overdue);
    }

    @GetMapping("/{id}")
    public TaskResponse getById(@PathVariable UUID id) {
        return taskService.getById(id);
    }

    @PutMapping("/{id}")
    public TaskResponse update(@PathVariable UUID id, @Valid @RequestBody TaskRequest request) {
        return taskService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public TaskResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody TaskStatusRequest request) {
        return taskService.changeStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID id) {
        taskService.archive(id);
    }
}
