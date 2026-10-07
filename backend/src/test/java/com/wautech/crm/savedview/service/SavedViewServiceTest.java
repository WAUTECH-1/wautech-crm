package com.wautech.crm.savedview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wautech.crm.savedview.dto.SavedViewRequest;
import com.wautech.crm.savedview.entity.SavedView;
import com.wautech.crm.savedview.entity.SavedViewResource;
import com.wautech.crm.savedview.repository.SavedViewRepository;
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
    SavedViewService service;

    private final SavedViewConfigurationValidator validator = new SavedViewConfigurationValidator();
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new SavedViewService(repository, validator);
    }

    @Test
    void createsAndReturnsValidatedSavedViewConfiguration() throws Exception {
        var request = request("Sales view", "{\"search\":\"Acme\",\"filters\":{\"status\":\"NEW\"},\"sortBy\":\"createdAt\",\"sortDirection\":\"desc\"}");
        when(repository.save(any(SavedView.class))).thenAnswer(call -> call.getArgument(0));
        var response = service.create(request);

        assertEquals("Sales view", response.name());
        assertEquals(SavedViewResource.LEAD, response.resource());
        assertEquals(1, response.configurationVersion());
        assertEquals("Acme", response.configuration().get("search").asText());
        verify(repository).save(any(SavedView.class));
    }

    @Test
    void listsOnlyRepositorySelectedActiveViewsAndSoftArchives() throws Exception {
        UUID id = UUID.randomUUID();
        SavedView view = new SavedView("My view", SavedViewResource.COMPANY, 1,
                mapper.readTree("{\"filters\":{}}"));
        when(repository.findAllByArchivedFalseOrderByNameAscIdAsc()).thenReturn(List.of(view));
        assertEquals(1, service.listActive().size());

        when(repository.findByIdAndArchivedFalse(id)).thenReturn(Optional.of(view));
        service.archive(id);
        verify(repository).findByIdAndArchivedFalse(id);
        verify(repository).save(view);
        org.junit.jupiter.api.Assertions.assertTrue(view.isArchived());
    }

    @Test
    void rejectsUnsupportedConfigurationBeforePersistence() throws Exception {
        var invalid = request("Bad view", "{\"filters\":{\"unknown\":\"value\"}}");
        assertThrows(com.wautech.crm.platform.search.InvalidListQueryException.class, () -> service.create(invalid));
    }

    @Test
    void retrievesAndUpdatesOnlyActiveSavedViews() throws Exception {
        UUID id = UUID.randomUUID();
        SavedView existing = new SavedView("Old", SavedViewResource.LEAD, 1,
                mapper.readTree("{\"filters\":{}}"));
        when(repository.findByIdAndArchivedFalse(id)).thenReturn(Optional.of(existing));
        when(repository.save(any(SavedView.class))).thenAnswer(call -> call.getArgument(0));

        assertEquals("Old", service.getById(id).name());
        var updated = service.update(id, request("New", "{\"sortBy\":\"lastName\",\"sortDirection\":\"asc\"}"));
        assertEquals("New", updated.name());
        verify(repository, org.mockito.Mockito.times(2)).findByIdAndArchivedFalse(id);
        verify(repository).save(existing);
    }

    private SavedViewRequest request(String name, String configuration) throws Exception {
        return new SavedViewRequest(name, SavedViewResource.LEAD, 1, mapper.readTree(configuration));
    }
}
