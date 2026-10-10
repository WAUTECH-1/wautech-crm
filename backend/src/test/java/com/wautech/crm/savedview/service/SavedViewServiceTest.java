package com.wautech.crm.savedview.service;

import com.wautech.crm.TestOrganization;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wautech.crm.savedview.dto.SavedViewRequest;
import com.wautech.crm.savedview.entity.SavedView;
import com.wautech.crm.savedview.entity.SavedViewResource;
import com.wautech.crm.savedview.repository.SavedViewRepository;
import com.wautech.crm.organization.service.OrganizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SavedViewServiceTest {
    @Mock SavedViewRepository repository;
    @Mock OrganizationService organizationService;
    SavedViewService service;

    private final SavedViewConfigurationValidator validator = new SavedViewConfigurationValidator();
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new SavedViewService(repository, validator, organizationService);
    }

    @Test
    void createsAndReturnsValidatedSavedViewConfiguration() throws Exception {
        var request = request("Sales view", "{\"search\":\"Acme\",\"filters\":{\"status\":\"NEW\"},\"sortBy\":\"createdAt\",\"sortDirection\":\"desc\"}");
        when(repository.save(any(SavedView.class))).thenAnswer(call -> call.getArgument(0));
        var response = service.create(TestOrganization.ID, request);

        assertEquals("Sales view", response.name());
        assertEquals(SavedViewResource.LEAD, response.resource());
        assertEquals(1, response.configurationVersion());
        assertEquals("Acme", response.configuration().get("search").asText());
        verify(repository).save(any(SavedView.class));
    }

    @Test
    void listsOnlyRepositorySelectedActiveViewsAndSoftArchives() throws Exception {
        UUID id = UUID.randomUUID();
        SavedView view = new SavedView(new com.wautech.crm.organization.entity.Organization("Test Organization"), "My view", SavedViewResource.COMPANY, 1,
                mapper.readTree("{\"filters\":{}}"));
        when(repository.findAllByOrganization_IdAndArchivedFalseOrderByNameAscIdAsc(TestOrganization.ID)).thenReturn(List.of(view));
        assertEquals(1, service.listActive(TestOrganization.ID).size());

        when(repository.findByIdAndOrganization_IdAndArchivedFalse(id, TestOrganization.ID)).thenReturn(Optional.of(view));
        service.archive(TestOrganization.ID, id);
        verify(repository).findByIdAndOrganization_IdAndArchivedFalse(id, TestOrganization.ID);
        verify(repository).save(view);
        org.junit.jupiter.api.Assertions.assertTrue(view.isArchived());
    }

    @Test
    void rejectsUnsupportedConfigurationBeforePersistence() throws Exception {
        var invalid = request("Bad view", "{\"filters\":{\"unknown\":\"value\"}}");
        assertThrows(com.wautech.crm.platform.search.InvalidListQueryException.class, () -> service.create(TestOrganization.ID, invalid));
    }

    @Test
    void retrievesAndUpdatesOnlyActiveSavedViews() throws Exception {
        UUID id = UUID.randomUUID();
        SavedView existing = new SavedView(new com.wautech.crm.organization.entity.Organization("Test Organization"), "Old", SavedViewResource.LEAD, 1,
                mapper.readTree("{\"filters\":{}}"));
        when(repository.findByIdAndOrganization_IdAndArchivedFalse(id, TestOrganization.ID)).thenReturn(Optional.of(existing));
        when(repository.save(any(SavedView.class))).thenAnswer(call -> call.getArgument(0));

        assertEquals("Old", service.getById(TestOrganization.ID, id).name());
        var updated = service.update(TestOrganization.ID, id, request("New", "{\"sortBy\":\"lastName\",\"sortDirection\":\"asc\"}"));
        assertEquals("New", updated.name());
        verify(repository, org.mockito.Mockito.times(2)).findByIdAndOrganization_IdAndArchivedFalse(id, TestOrganization.ID);
        verify(repository).save(existing);
    }

    private SavedViewRequest request(String name, String configuration) throws Exception {
        return new SavedViewRequest(name, SavedViewResource.LEAD, 1, mapper.readTree(configuration));
    }

    @Test
    void anotherOrganizationCannotReadSavedViewById() {
        UUID viewId = UUID.randomUUID();
        UUID otherOrganizationId = UUID.randomUUID();
        when(repository.findByIdAndOrganization_IdAndArchivedFalse(viewId, otherOrganizationId))
                .thenReturn(Optional.empty());

        assertThrows(com.wautech.crm.savedview.service.SavedViewNotFoundException.class,
                () -> service.getById(otherOrganizationId, viewId));

        verify(repository).findByIdAndOrganization_IdAndArchivedFalse(viewId, otherOrganizationId);
    }
}
