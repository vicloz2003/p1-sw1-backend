package com.ibpms.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Document("form_templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FormTemplate {
    @Id
    private String id;
    private String name;
    private String description;
    private String createdBy;
    private Map<String, Object> formSchema;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
