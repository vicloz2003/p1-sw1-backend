
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

