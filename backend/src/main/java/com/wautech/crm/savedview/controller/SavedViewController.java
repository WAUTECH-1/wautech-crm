package com.wautech.crm.savedview.controller;

import com.wautech.crm.savedview.dto.SavedViewRequest;
import com.wautech.crm.savedview.dto.SavedViewResponse;
import com.wautech.crm.savedview.service.SavedViewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/saved-views")
public class SavedViewController {
    private final SavedViewService service;

    public SavedViewController(SavedViewService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SavedViewResponse create(@Valid @RequestBody SavedViewRequest request) {
        return service.create(request);
    }

    @GetMapping
    public List<SavedViewResponse> listActive() {
        return service.listActive();
    }

    @GetMapping("/{id}")
    public SavedViewResponse getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    @PutMapping("/{id}")
    public SavedViewResponse update(@PathVariable UUID id, @Valid @RequestBody SavedViewRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID id) {
        service.archive(id);
    }
}
