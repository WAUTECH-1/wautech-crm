package com.wautech.crm.notification.repository;

import com.wautech.crm.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Page<Notification> findAllByOrganizationIdAndRecipientUserId(UUID organizationId, UUID recipientUserId,
                                                                  Pageable pageable);

    Page<Notification> findAllByOrganizationIdAndRecipientUserIdAndReadAtIsNull(UUID organizationId,
                                                                                 UUID recipientUserId,
                                                                                 Pageable pageable);

    Optional<Notification> findByIdAndOrganizationIdAndRecipientUserId(UUID id, UUID organizationId,
                                                                       UUID recipientUserId);

    long countByOrganizationIdAndRecipientUserIdAndReadAtIsNull(UUID organizationId, UUID recipientUserId);

    boolean existsByOrganizationIdAndRecipientUserIdAndDeduplicationKey(UUID organizationId,
                                                                        UUID recipientUserId,
                                                                        String deduplicationKey);

    Optional<Notification> findByOrganizationIdAndRecipientUserIdAndDeduplicationKey(UUID organizationId,
                                                                                     UUID recipientUserId,
                                                                                     String deduplicationKey);

    @Modifying
    @Query("update Notification n set n.readAt = :readAt where n.organizationId = :organizationId "
            + "and n.recipientUserId = :recipientUserId and n.readAt is null")
    int markAllRead(@Param("organizationId") UUID organizationId,
                    @Param("recipientUserId") UUID recipientUserId, @Param("readAt") Instant readAt);
}
