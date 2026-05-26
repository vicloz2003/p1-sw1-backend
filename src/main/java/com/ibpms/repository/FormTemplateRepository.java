package com.ibpms.repository;

import com.ibpms.domain.FormTemplate;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface FormTemplateRepository extends MongoRepository<FormTemplate, String> {
    List<FormTemplate> findAllByOrderByCreatedAtDesc();
}
