package com.wautech.crm.savedview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wautech.crm.platform.search.InvalidListQueryException;
import com.wautech.crm.savedview.entity.SavedViewResource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SavedViewConfigurationValidatorTest {
    private final SavedViewConfigurationValidator validator = new SavedViewConfigurationValidator();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsSupportedResourceFiltersSortAndSearchConfiguration() throws Exception {
        var config = mapper.readTree("""
                {"search":"Acme","filters":{"companyId":"22222222-2222-2222-2222-222222222222","status":"NEW"},"sortBy":"createdAt","sortDirection":"desc"}
                """);
        assertDoesNotThrow(() -> validator.validate(SavedViewResource.LEAD, 1, config));
    }

    @Test
    void rejectsUnknownConfigurationFieldsUnsupportedFiltersAndSorts() throws Exception {
        assertThrows(InvalidListQueryException.class, () -> validator.validate(SavedViewResource.COMPANY, 1,
                mapper.readTree("{\"rawSql\":\"select 1\"}")));
        assertThrows(InvalidListQueryException.class, () -> validator.validate(SavedViewResource.COMPANY, 1,
                mapper.readTree("{\"filters\":{\"tenantId\":\"22222222-2222-2222-2222-222222222222\"}}")));
        assertThrows(InvalidListQueryException.class, () -> validator.validate(SavedViewResource.TASK, 1,
                mapper.readTree("{\"sortBy\":\"constructor\",\"sortDirection\":\"asc\"}")));
        assertThrows(InvalidListQueryException.class, () -> validator.validate(SavedViewResource.TASK, 1,
                mapper.readTree("{\"sortBy\":\"dueAt\",\"sortDirection\":\"sideways\"}")));
    }
}
