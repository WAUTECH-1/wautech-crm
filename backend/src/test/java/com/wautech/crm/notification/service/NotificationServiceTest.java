package com.wautech.crm.notification.service;

import com.wautech.crm.identity.entity.User;
import com.wautech.crm.identity.repository.UserRepository;
import com.wautech.crm.notification.entity.Notification;
import com.wautech.crm.notification.entity.NotificationTargetType;
import com.wautech.crm.notification.entity.NotificationType;
import com.wautech.crm.notification.repository.NotificationRepository;
import com.wautech.crm.organization.entity.MembershipStatus;
import com.wautech.crm.organization.repository.OrganizationMembershipRepository;
import com.wautech.crm.organization.service.OrganizationService;
import com.wautech.crm.platform.security.CrmAuthorization;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationServiceTest {
    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final OrganizationService organizationService = mock(OrganizationService.class);
    private final OrganizationMembershipRepository membershipRepository = mock(OrganizationMembershipRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final CrmAuthorization authorization = mock(CrmAuthorization.class);
    private final NotificationService service = new NotificationService(repository, organizationService,
            membershipRepository, userRepository, authorization);
    private final UUID organizationId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void listsOnlyTheCurrentRecipientAndOrganizationWithStablePagination() {
        when(authorization.currentUserId()).thenReturn(userId);
        when(repository.findAllByOrganizationIdAndRecipientUserId(eq(organizationId), eq(userId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(notification())));

        var response = service.list(organizationId, 0, 20);

        assertEquals(1, response.content().size());
        assertEquals("Title", response.content().getFirst().title());
        var captor = org.mockito.ArgumentCaptor.forClass(PageRequest.class);
        verify(repository).findAllByOrganizationIdAndRecipientUserId(eq(organizationId), eq(userId), captor.capture());
        assertEquals(List.of("createdAt: DESC", "id: DESC"), captor.getValue().getSort().stream()
                .map(order -> order.getProperty() + ": " + order.getDirection()).toList());
    }

    @Test
    void rejectsInvalidPageBoundsBeforeQuerying() {
        assertThrows(InvalidNotificationPageException.class, () -> service.list(organizationId, -1, 20));
        assertThrows(InvalidNotificationPageException.class, () -> service.list(organizationId, 0, 101));
        verifyNoInteractions(repository);
    }

    @Test
    void unavailableNotificationIsNotFoundForNonRecipient() {
        when(authorization.currentUserId()).thenReturn(userId);
        UUID notificationId = UUID.randomUUID();
        when(repository.findByIdAndOrganizationIdAndRecipientUserId(notificationId, organizationId, userId))
                .thenReturn(Optional.empty());

        assertThrows(NotificationNotFoundException.class, () -> service.getById(organizationId, notificationId));
    }

    @Test
    void marksOneNotificationReadIdempotently() {
        when(authorization.currentUserId()).thenReturn(userId);
        Notification notification = notification();
        when(repository.findByIdAndOrganizationIdAndRecipientUserId(notification.getId(), organizationId, userId))
                .thenReturn(Optional.of(notification));
        when(repository.save(notification)).thenReturn(notification);

        var response = service.markRead(organizationId, notification.getId());

        assertNotNull(response.readAt());
        var originalReadAt = response.readAt();
        assertEquals(originalReadAt, service.markRead(organizationId, notification.getId()).readAt());
    }

    @Test
    void unreadCountAndMarkAllAreRecipientScoped() {
        when(authorization.currentUserId()).thenReturn(userId);
        when(repository.countByOrganizationIdAndRecipientUserIdAndReadAtIsNull(organizationId, userId)).thenReturn(3L);
        when(repository.markAllRead(eq(organizationId), eq(userId), any())).thenReturn(3);

        assertEquals(3, service.unreadCount(organizationId));
        assertEquals(3, service.markAllRead(organizationId));
        verify(repository).countByOrganizationIdAndRecipientUserIdAndReadAtIsNull(organizationId, userId);
        verify(repository).markAllRead(eq(organizationId), eq(userId), any());
    }

    @Test
    void trustedCreationRequiresEnabledActiveMemberAndDeduplicates() {
        User user = mock(User.class);
        when(user.isEnabled()).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(membershipRepository.existsByOrganization_IdAndUser_IdAndStatus(
                organizationId, userId, MembershipStatus.ACTIVE)).thenReturn(true);
        Notification saved = notification();
        when(repository.save(any(Notification.class))).thenReturn(saved);

        Notification created = service.createFromTrustedEvent(organizationId, userId,
                NotificationType.ORGANIZATION_MEMBERSHIP_ACTIVATED, "Title", "Message",
                NotificationTargetType.ORGANIZATION_MEMBERSHIP, UUID.randomUUID(), "event-1");
        assertSame(saved, created);

        when(repository.existsByOrganizationIdAndRecipientUserIdAndDeduplicationKey(organizationId, userId, "event-1"))
                .thenReturn(true);
        when(repository.findByOrganizationIdAndRecipientUserIdAndDeduplicationKey(organizationId, userId, "event-1"))
                .thenReturn(Optional.of(saved));
        assertSame(saved, service.createFromTrustedEvent(organizationId, userId,
                NotificationType.ORGANIZATION_MEMBERSHIP_ACTIVATED, "Title", "Message",
                NotificationTargetType.ORGANIZATION_MEMBERSHIP, UUID.randomUUID(), "event-1"));
    }

    @Test
    void trustedCreationRejectsInactiveMembership() {
        User user = mock(User.class);
        when(user.isEnabled()).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(membershipRepository.existsByOrganization_IdAndUser_IdAndStatus(
                organizationId, userId, MembershipStatus.ACTIVE)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service.createFromTrustedEvent(organizationId, userId,
                NotificationType.ORGANIZATION_MEMBERSHIP_ACTIVATED, "Title", "Message", null, null, null));
        verify(repository, never()).save(any());
    }

    private Notification notification() {
        return new Notification(organizationId, userId, NotificationType.ORGANIZATION_MEMBERSHIP_ACTIVATED,
                "Title", "Message", null, null, null);
    }
}
