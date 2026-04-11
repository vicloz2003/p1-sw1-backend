
package com.ibpms.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import com.ibpms.domain.enums.TaskStatus;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Document("activity_tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ActivityTask {
    @Id
    private String id;
    private String processInstanceId;
    private String nodeId;
    private String assignedDepartmentId;
    private String assignedUserId;
    private TaskStatus status;
    private Map<String, Object> formData;
    private LocalDateTime assignedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}

