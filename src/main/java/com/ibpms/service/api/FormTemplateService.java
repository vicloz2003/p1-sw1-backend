package com.ibpms.service.api;

import com.ibpms.dto.request.CreateFormTemplateRequest;
import com.ibpms.dto.request.UpdateFormTemplateRequest;
import com.ibpms.dto.response.FormTemplateResponse;

import java.util.List;

public interface FormTemplateService {
    List<FormTemplateResponse> getAll();
    FormTemplateResponse getById(String id);
    FormTemplateResponse create(CreateFormTemplateRequest request, String userId);
    FormTemplateResponse update(String id, UpdateFormTemplateRequest request);
    void delete(String id);
}
