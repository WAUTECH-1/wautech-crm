package com.wautech.crm.notification.controller;

import com.wautech.crm.notification.dto.MarkAllNotificationsReadResponse;
import com.wautech.crm.notification.dto.NotificationPageResponse;
import com.wautech.crm.notification.dto.NotificationResponse;
import com.wautech.crm.notification.dto.UnreadNotificationCountResponse;
import com.wautech.crm.notification.service.NotificationService;
import com.wautech.crm.platform.tenant.AuthenticatedOrganizationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    private final AuthenticatedOrganizationContext organizationContext;

    public NotificationController(NotificationService notificationService,
            AuthenticatedOrganizationContext organizationContext) {
        this.notificationService = notificationService;
        this.organizationContext = organizationContext;
    }

    @GetMapping
    public NotificationPageResponse list(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return notificationService.list(organizationContext.requireOrganizationId(), page, size);
    }

    @GetMapping("/{id}")
    public NotificationResponse getById(@PathVariable UUID id) {
        return notificationService.getById(organizationContext.requireOrganizationId(), id);
    }

    @GetMapping("/unread-count")
    public UnreadNotificationCountResponse unreadCount() {
        return new UnreadNotificationCountResponse(
                notificationService.unreadCount(organizationContext.requireOrganizationId()));
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable UUID id) {
        return notificationService.markRead(organizationContext.requireOrganizationId(), id);
    }

    @PatchMapping("/read-all")
    public MarkAllNotificationsReadResponse markAllRead() {
        return new MarkAllNotificationsReadResponse(
                notificationService.markAllRead(organizationContext.requireOrganizationId()));
    }
}
