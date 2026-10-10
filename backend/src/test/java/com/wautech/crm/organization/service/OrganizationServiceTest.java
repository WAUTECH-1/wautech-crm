package com.wautech.crm.organization.service;

import com.wautech.crm.organization.dto.OrganizationRequest;
import com.wautech.crm.organization.entity.Organization;
import com.wautech.crm.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {
    @Mock private OrganizationRepository repository;
    @InjectMocks private OrganizationService service;

    @Test
    void createsOrganizationWithTrimmedName() {
        when(repository.save(any(Organization.class))).thenAnswer(call -> call.getArgument(0));

        var response = service.create(new OrganizationRequest("  WAU TECH  "));

        assertEquals("WAU TECH", response.name());
        assertFalse(response.archived());
        verify(repository).save(any(Organization.class));
    }

    @Test
    void retrievesAndUpdatesOnlyAnActiveOrganization() {
        UUID id = UUID.randomUUID();
        Organization organization = new Organization("Before");
        when(repository.findByIdAndArchivedFalse(id)).thenReturn(Optional.of(organization));
        when(repository.save(organization)).thenReturn(organization);

        assertEquals("Before", service.getById(id).name());
        assertEquals("After", service.update(id, new OrganizationRequest("After")).name());
        assertEquals("After", organization.getName());
        verify(repository, times(2)).findByIdAndArchivedFalse(id);
    }

    @Test
    void archivesOrganizationAndRejectsFurtherActiveLookup() {
        UUID id = UUID.randomUUID();
        Organization organization = new Organization("Archived");
        when(repository.findByIdAndArchivedFalse(id)).thenReturn(Optional.of(organization), Optional.empty());

        service.archive(id);

        assertTrue(organization.isArchived());
        assertThrows(OrganizationNotFoundException.class, () -> service.requireActiveOrganization(id));
        verify(repository).save(organization);
    }
}
