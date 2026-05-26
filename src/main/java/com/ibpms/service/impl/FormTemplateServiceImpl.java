package com.ibpms.service.impl;

import com.ibpms.domain.FormTemplate;
import com.ibpms.dto.request.CreateFormTemplateRequest;
import com.ibpms.dto.request.UpdateFormTemplateRequest;
import com.ibpms.dto.response.FormTemplateResponse;
import com.ibpms.exception.FormTemplateNotFoundException;
import com.ibpms.repository.FormTemplateRepository;
import com.ibpms.service.api.FormTemplateService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FormTemplateServiceImpl implements FormTemplateService {

    private final FormTemplateRepository repository;

    public FormTemplateServiceImpl(FormTemplateRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<FormTemplateResponse> getAll() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public FormTemplateResponse getById(String id) {
        return toResponse(findOrThrow(id));
    }

    @Override
    public FormTemplateResponse create(CreateFormTemplateRequest request, String userId) {
        FormTemplate template = new FormTemplate();
        template.setName(request.name());
        template.setDescription(request.description());
        template.setCreatedBy(userId);
        template.setFormSchema(request.formSchema());
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());
        return toResponse(repository.save(template));
    }

    @Override
    public FormTemplateResponse update(String id, UpdateFormTemplateRequest request) {
        FormTemplate template = findOrThrow(id);
        template.setName(request.name());
        template.setDescription(request.description());
        template.setFormSchema(request.formSchema());
        template.setUpdatedAt(LocalDateTime.now());
        return toResponse(repository.save(template));
    }

    @Override
    public void delete(String id) {
        findOrThrow(id);
        repository.deleteById(id);
    }

    private FormTemplate findOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new FormTemplateNotFoundException(id));
    }

    private FormTemplateResponse toResponse(FormTemplate t) {
        return new FormTemplateResponse(
                t.getId(),
                t.getName(),
                t.getDescription(),
                t.getCreatedBy(),
                t.getFormSchema(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }
}
