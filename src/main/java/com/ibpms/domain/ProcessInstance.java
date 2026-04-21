
package com.ibpms.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import com.ibpms.domain.enums.InstanceStatus;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Document("process_instances")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProcessInstance {
    @Id
    private String id;
    private String businessPolicyId;
    private String currentNodeId;
    private String initiatedBy;
    private String clientId;
    private InstanceStatus status;
    private Map<String, Object> contextData;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}

