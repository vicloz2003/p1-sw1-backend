package com.ibpms.controller;

import com.ibpms.dto.request.CreateFormTemplateRequest;
import com.ibpms.dto.request.UpdateFormTemplateRequest;
import com.ibpms.dto.response.FormTemplateResponse;
import com.ibpms.service.api.FormTemplateService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/form-templates")
@PreAuthorize("hasAuthority('ADMIN_DESIGNER')")
public class FormTemplateController {

    private final FormTemplateService formTemplateService;

    public FormTemplateController(FormTemplateService formTemplateService) {
        this.formTemplateService = formTemplateService;
    }

    @GetMapping
    public ResponseEntity<List<FormTemplateResponse>> getAll() {
        return ResponseEntity.ok(formTemplateService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FormTemplateResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(formTemplateService.getById(id));
    }

    @PostMapping
    public ResponseEntity<FormTemplateResponse> create(
            @Valid @RequestBody CreateFormTemplateRequest request,
            Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(formTemplateService.create(request, userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FormTemplateResponse> update(
            @PathVariable String id,
            @Valid @RequestBody UpdateFormTemplateRequest request) {
        return ResponseEntity.ok(formTemplateService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        formTemplateService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
