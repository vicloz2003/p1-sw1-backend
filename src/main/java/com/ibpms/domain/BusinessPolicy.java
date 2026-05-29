
package com.ibpms.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import com.ibpms.domain.enums.PolicyStatus;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Document("business_policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BusinessPolicy {
    @Id
    private String id;
    private String name;
    private String description;
    private String createdBy;
    private PolicyStatus status;
    private List<ActivityPartition> partitions;
    private List<ActivityNode> nodes;
    private List<ControlFlow> flows;
    private String bpmnXml;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Documents that must or may be submitted during this policy's lifecycle (RF-01).
     * Embedded subdocuments — no separate collection.
     */
    private List<DocumentRequirement> documentRequirements;

    /**
     * Semantic tags for NLP policy classification (RF-11).
     * e.g. ["credito", "prestamo", "financiamiento"]
     */
    private List<String> tags;
}

