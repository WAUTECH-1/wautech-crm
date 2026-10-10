package com.wautech.crm.audit.service;

import com.wautech.crm.audit.entity.AuditEvent;
import com.wautech.crm.audit.entity.AuditOutcome;
import com.wautech.crm.audit.repository.AuditEventRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditEventWriterTest {
    private final AuditEventRepository repository = mock(AuditEventRepository.class);
    private final AuditEventWriter writer = new AuditEventWriter(repository);

    @Test
    void persistsBusinessActorTenantAndTargetWithoutPayloadMetadata() {
        UUID orgId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(repository.save(any(AuditEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        writer.recordBusiness(orgId, actorId, "COMPANY_UPDATED", "COMPANY", targetId);

        var captor = org.mockito.ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(captor.capture());
        assertEquals(orgId, captor.getValue().getOrganizationId());
        assertEquals(actorId, captor.getValue().getActorUserId());
        assertEquals(targetId, captor.getValue().getTargetId());
        assertEquals(AuditOutcome.SUCCESS, captor.getValue().getOutcome());
        assertTrue(captor.getValue().getMetadata().isEmpty());
    }

    @Test
    void allowsAnonymousFailureEventWithoutIdentityOrSubmittedCredentials() {
        when(repository.save(any(AuditEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        writer.recordSecurity(null, null, "LOGIN_FAILED", "USER", null, AuditOutcome.FAILURE, Map.of());
        var captor = org.mockito.ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(captor.capture());
        assertNull(captor.getValue().getOrganizationId());
        assertNull(captor.getValue().getActorUserId());
        assertTrue(captor.getValue().getMetadata().isEmpty());
    }

    @Test
    void rejectsCredentialMetadataInsteadOfPersistingIt() {
        assertThrows(IllegalArgumentException.class, () -> new AuditEvent(null, null, "LOGIN_FAILED", "USER", null,
                AuditOutcome.FAILURE, java.time.Instant.now(), Map.of("password", "do-not-store")));
        assertThrows(IllegalArgumentException.class, () -> new AuditEvent(null, null, "ACCESS_DENIED", "API_REQUEST", null,
                AuditOutcome.FAILURE, java.time.Instant.now(), Map.of("method", "Bearer-token-value")));
    }
}
