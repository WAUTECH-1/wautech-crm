package com.wautech.crm.notification.service;

import com.wautech.crm.identity.repository.UserRepository;
import com.wautech.crm.notification.dto.NotificationPageResponse;
import com.wautech.crm.notification.dto.NotificationResponse;
import com.wautech.crm.notification.entity.Notification;
import com.wautech.crm.notification.entity.NotificationTargetType;
import com.wautech.crm.notification.entity.NotificationType;
import com.wautech.crm.notification.repository.NotificationRepository;
import com.wautech.crm.organization.entity.MembershipStatus;
import com.wautech.crm.organization.repository.OrganizationMembershipRepository;
import com.wautech.crm.organization.service.OrganizationService;
import com.wautech.crm.platform.security.CrmAuthorization;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class NotificationService {
    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository notificationRepository;
    private final OrganizationService organizationService;
    private final OrganizationMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final CrmAuthorization authorization;

    public NotificationService(NotificationRepository notificationRepository, OrganizationService organizationService,
            OrganizationMembershipRepository membershipRepository, UserRepository userRepository,
            CrmAuthorization authorization) {
        this.notificationRepository = notificationRepository;
        this.organizationService = organizationService;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@crmAuthorization.canView(#p0)")
    public NotificationPageResponse list(UUID organizationId, int page, int size) {
        validatePage(page, size);
        UUID userId = requireCurrentUserId();
        organizationService.requireActiveOrganization(organizationId);
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<NotificationResponse> result = notificationRepository
                .findAllByOrganizationIdAndRecipientUserId(organizationId, userId, pageable)
                .map(NotificationResponse::from);
        return NotificationPageResponse.from(result);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@crmAuthorization.canView(#p0)")
    public NotificationResponse getById(UUID organizationId, UUID id) {
        organizationService.requireActiveOrganization(organizationId);
        return NotificationResponse.from(findRecipientNotification(organizationId, id));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@crmAuthorization.canView(#p0)")
    public long unreadCount(UUID organizationId) {
        organizationService.requireActiveOrganization(organizationId);
        return notificationRepository.countByOrganizationIdAndRecipientUserIdAndReadAtIsNull(
                organizationId, requireCurrentUserId());
    }

    @PreAuthorize("@crmAuthorization.canView(#p0)")
    public NotificationResponse markRead(UUID organizationId, UUID id) {
        organizationService.requireActiveOrganization(organizationId);
        Notification notification = findRecipientNotification(organizationId, id);
        notification.markRead(Instant.now());
        return NotificationResponse.from(notificationRepository.save(notification));
    }

    @PreAuthorize("@crmAuthorization.canView(#p0)")
    public int markAllRead(UUID organizationId) {
        organizationService.requireActiveOrganization(organizationId);
        return notificationRepository.markAllRead(organizationId, requireCurrentUserId(), Instant.now());
    }

    /** Called only by trusted domain workflows; the public API has no notification-creation route. */
    public Notification createFromTrustedEvent(UUID organizationId, UUID recipientUserId, NotificationType type,
            String title, String message, NotificationTargetType targetType, UUID targetId,
            String deduplicationKey) {
        organizationService.requireActiveOrganization(organizationId);
        if (userRepository.findById(recipientUserId).filter(user -> user.isEnabled()).isEmpty()
                || !membershipRepository.existsByOrganization_IdAndUser_IdAndStatus(
                        organizationId, recipientUserId, MembershipStatus.ACTIVE)) {
            throw new IllegalArgumentException("Notification recipient must have an active organization membership");
        }
        if (deduplicationKey != null && notificationRepository
                .existsByOrganizationIdAndRecipientUserIdAndDeduplicationKey(
                        organizationId, recipientUserId, deduplicationKey)) {
            return notificationRepository.findByOrganizationIdAndRecipientUserIdAndDeduplicationKey(
                    organizationId, recipientUserId, deduplicationKey).orElseThrow();
        }
        return notificationRepository.save(new Notification(organizationId, recipientUserId, type, title, message,
                targetType, targetId, deduplicationKey));
    }

    private Notification findRecipientNotification(UUID organizationId, UUID id) {
        return notificationRepository.findByIdAndOrganizationIdAndRecipientUserId(
                id, organizationId, requireCurrentUserId()).orElseThrow(() -> new NotificationNotFoundException(id));
    }

    private UUID requireCurrentUserId() {
        UUID userId = authorization.currentUserId();
        if (userId == null) throw new IllegalStateException("Authenticated user context is unavailable");
        return userId;
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidNotificationPageException();
        }
    }
}
