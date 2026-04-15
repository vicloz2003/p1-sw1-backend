package com.ibpms.service.api;

import com.ibpms.dto.response.TaskResponse;

import java.util.List;

public interface TaskService {
    /**
     * Returns PENDING tasks for the department (claimable) +
     * IN_PROGRESS tasks assigned to the userId (active).
     * Sorted by assignedAt descending.
     */
    List<TaskResponse> getMyTasks(String departmentId, String userId);

    TaskResponse claim(String taskId, String userId);
}

