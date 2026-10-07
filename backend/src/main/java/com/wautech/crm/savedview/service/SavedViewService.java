package com.wautech.crm.savedview.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.wautech.crm.savedview.dto.SavedViewRequest;
import com.wautech.crm.savedview.dto.SavedViewResponse;
import com.wautech.crm.savedview.entity.SavedView;
import com.wautech.crm.savedview.repository.SavedViewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class SavedViewService {
    private final SavedViewRepository repository;
    private final SavedViewConfigurationValidator validator;
    public SavedViewService(SavedViewRepository repository, SavedViewConfigurationValidator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    public SavedViewResponse create(SavedViewRequest request) {
        validate(request);
        JsonNode configuration = request.configuration().deepCopy();
        SavedView view = new SavedView(request.name().trim(), request.resource(), request.configurationVersion(), configuration);
        return SavedViewResponse.from(repository.save(view));
    }

    @Transactional(readOnly = true)
    public List<SavedViewResponse> listActive() {
        return repository.findAllByArchivedFalseOrderByNameAscIdAsc().stream()
                .map(SavedViewResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public SavedViewResponse getById(UUID id) {
        return SavedViewResponse.from(findActive(id));
    }

    public SavedViewResponse update(UUID id, SavedViewRequest request) {
        validate(request);
        SavedView view = findActive(id);
        view.update(request.name().trim(), request.resource(), request.configurationVersion(),
                request.configuration().deepCopy());
        return SavedViewResponse.from(repository.save(view));
    }

    public void archive(UUID id) {
        SavedView view = findActive(id);
        view.archive();
        repository.save(view);
    }

    private void validate(SavedViewRequest request) {
        validator.validate(request.resource(), request.configurationVersion(), request.configuration());
    }

    private SavedView findActive(UUID id) {
        return repository.findByIdAndArchivedFalse(id).orElseThrow(() -> new SavedViewNotFoundException(id));
    }
}
