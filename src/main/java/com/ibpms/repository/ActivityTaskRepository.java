package com.ibpms.repository;

import com.ibpms.domain.ActivityTask;
import com.ibpms.domain.enums.TaskStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface ActivityTaskRepository extends MongoRepository<ActivityTask, String> {
    List<ActivityTask> findByAssignedDepartmentIdAndStatus(String departmentId, TaskStatus status);
    List<ActivityTask> findByAssignedUserIdAndStatus(String userId, TaskStatus status);
    List<ActivityTask> findByProcessInstanceId(String processInstanceId);
    boolean existsByProcessInstanceIdAndStatus(String processInstanceId, TaskStatus status);
    boolean existsByProcessInstanceIdAndNodeIdAndStatus(String processInstanceId, String nodeId, TaskStatus status);
}


