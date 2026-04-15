package com.ibpms.controller;

import com.ibpms.dto.request.CompleteTaskRequest;
import com.ibpms.dto.response.TaskResponse;
import com.ibpms.engine.api.WorkflowEngine;
import com.ibpms.service.api.TaskService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tasks")
@PreAuthorize("hasAuthority('EMPLOYEE')")
public class TaskController {

    private final TaskService taskService;
    private final WorkflowEngine workflowEngine;

    public TaskController(TaskService taskService, WorkflowEngine workflowEngine) {
        this.taskService = taskService;
        this.workflowEngine = workflowEngine;
    }

    /**
     * Returns PENDING tasks for the caller's department (available to claim) +
     * IN_PROGRESS tasks assigned to the caller (already claimed).
     * departmentId is read from the request attribute set by JwtAuthFilter — no DB call.
     */
    @GetMapping("/my")
    public ResponseEntity<List<TaskResponse>> getMyTasks(HttpServletRequest request,
                                                          Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        String departmentId = (String) request.getAttribute("departmentId");
        return ResponseEntity.ok(taskService.getMyTasks(departmentId, userId));
    }

    @PostMapping("/{id}/claim")
    public ResponseEntity<TaskResponse> claim(@PathVariable String id,
                                               Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        return ResponseEntity.ok(taskService.claim(id, userId));
    }

    /**
     * Delegates directly to WorkflowEngine as specified in the plan.
     * The engine merges formData into contextData and advances the process.
     */
    @PostMapping("/{id}/complete")
    public ResponseEntity<Void> complete(@PathVariable String id,
                                          @Valid @RequestBody CompleteTaskRequest request,
                                          Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        workflowEngine.completeTask(id, request.formData(), userId);
        return ResponseEntity.noContent().build();
    }
}

