package com.ibpms.service.impl;

import com.ibpms.domain.ActivityNode;
import com.ibpms.domain.ActivityPartition;
import com.ibpms.domain.ActivityTask;
import com.ibpms.domain.BusinessPolicy;
import com.ibpms.domain.Department;
import com.ibpms.domain.DocumentRequirement;
import com.ibpms.domain.ProcessDocument;
import com.ibpms.domain.ProcessInstance;
import com.ibpms.domain.enums.DocumentStatus;
import com.ibpms.domain.enums.NodeType;
import com.ibpms.domain.enums.TaskStatus;
import com.ibpms.dto.request.StartProcessRequest;
import com.ibpms.dto.response.NodeProgressItem;
import com.ibpms.dto.response.ProcessStatusResponse;
import com.ibpms.engine.api.WorkflowEngine;
import com.ibpms.exception.MissingMandatoryDocumentsException;
import com.ibpms.exception.PolicyNotFoundException;
import com.ibpms.exception.ProcessInstanceNotFoundException;
import com.ibpms.repository.ActivityTaskRepository;
import com.ibpms.repository.BusinessPolicyRepository;
import com.ibpms.repository.DepartmentRepository;
import com.ibpms.repository.ProcessDocumentRepository;
import com.ibpms.repository.ProcessInstanceRepository;
import com.ibpms.repository.UserRepository;
import com.ibpms.service.api.ProcessService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProcessServiceImpl implements ProcessService {

    private final WorkflowEngine workflowEngine;
    private final ProcessInstanceRepository processInstanceRepository;
    private final BusinessPolicyRepository policyRepository;
    private final ActivityTaskRepository taskRepository;
    private final DepartmentRepository departmentRepository;
    private final ProcessDocumentRepository documentRepository;
    private final UserRepository userRepository;

    public ProcessServiceImpl(WorkflowEngine workflowEngine,
                              ProcessInstanceRepository processInstanceRepository,
                              BusinessPolicyRepository policyRepository,
                              ActivityTaskRepository taskRepository,
                              DepartmentRepository departmentRepository,
                              ProcessDocumentRepository documentRepository,
                              UserRepository userRepository) {
        this.workflowEngine = workflowEngine;
        this.processInstanceRepository = processInstanceRepository;
        this.policyRepository = policyRepository;
        this.taskRepository = taskRepository;
        this.departmentRepository = departmentRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
    }

    /**
     * Starts a new process instance.
     *
     * <p><strong>Document validation (RF-01):</strong> only enforced when the caller
     * explicitly provides {@code confirmedDocumentIds} (i.e. a CLIENT starting from
     * the mobile app who has pre-uploaded the required documents).  When an EMPLOYEE
     * initiates from the web app without document IDs, validation is skipped — the
     * employee is not responsible for uploading the client's documents.
     */
    @Override
    public ProcessStatusResponse startProcess(StartProcessRequest request, String userId) {
        List<String> provided = request.confirmedDocumentIds() != null
                && !request.confirmedDocumentIds().isEmpty()
                ? request.confirmedDocumentIds()
                : List.of();

        List<ProcessDocument> confirmedDocs = List.of();

        // Only validate when the caller provided document IDs (CLIENT mobile flow)
        if (!provided.isEmpty()) {
            BusinessPolicy policy = policyRepository.findById(request.policyId())
                    .orElseThrow(() -> new PolicyNotFoundException(request.policyId()));

            List<DocumentRequirement> mandatoryAtStart = policy.getDocumentRequirements() == null
                    ? List.of()
                    : policy.getDocumentRequirements().stream()
                            .filter(r -> r.isMandatory() && "PROCESS_START".equals(r.getUploadStage()))
                            .toList();

            if (!mandatoryAtStart.isEmpty()) {
                confirmedDocs = documentRepository
                        .findByIdInAndStatus(provided, DocumentStatus.CONFIRMED)
                        .stream()
                        .filter(d -> request.policyId().equals(d.getBusinessPolicyId()))
                        .toList();

                Set<String> confirmedReqIds = confirmedDocs.stream()
                        .map(ProcessDocument::getDocumentRequirementId)
                        .collect(Collectors.toSet());

                List<String> missing = mandatoryAtStart.stream()
                        .filter(r -> !confirmedReqIds.contains(r.getId()))
                        .map(DocumentRequirement::getName)
                        .toList();

                if (!missing.isEmpty()) {
                    throw new MissingMandatoryDocumentsException(missing);
                }
            }
        }

        ProcessInstance instance = workflowEngine.startProcess(
                request.policyId(),
                userId,
                request.initialData() != null ? request.initialData() : Collections.emptyMap()
        );
        instance.setClientId(request.clientId());
        processInstanceRepository.save(instance);

        // Link pre-process documents to the new instance if provided by CLIENT
        if (!confirmedDocs.isEmpty()) {
            confirmedDocs.forEach(d -> d.setProcessInstanceId(instance.getId()));
            documentRepository.saveAll(confirmedDocs);
        }

        return toStatusResponse(instance);
    }

    @Override
    public ProcessStatusResponse getStatus(String processInstanceId) {
        ProcessInstance instance = processInstanceRepository.findById(processInstanceId)
                .orElseThrow(() -> new ProcessInstanceNotFoundException(
                        "Process instance not found: " + processInstanceId));
        return toStatusResponse(instance);
    }

    @Override
    public List<ProcessStatusResponse> getByClientId(String clientId) {
        return processInstanceRepository
                .findByClientId(clientId)
                .stream()
                .map(this::toStatusResponse)
                .toList();
    }

    @Override
    public List<ProcessStatusResponse> getAll() {
        return processInstanceRepository.findAll()
                .stream()
                .map(this::toStatusResponse)
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private ProcessStatusResponse toStatusResponse(ProcessInstance instance) {
        String policyName = "";
        String currentNodeLabel = instance.getCurrentNodeId();
        String currentDepartmentId = null;
        String currentDepartmentName = null;
        List<NodeProgressItem> nodeProgress = List.of();
        String pendingClientAction = null;

        var policyOpt = policyRepository.findById(instance.getBusinessPolicyId());
        if (policyOpt.isPresent()) {
            BusinessPolicy policy = policyOpt.get();
            policyName = policy.getName();

            // Resolve current node label
            ActivityNode currentNode = policy.getNodes() == null ? null :
                    policy.getNodes().stream()
                            .filter(n -> n.getId().equals(instance.getCurrentNodeId()))
                            .findFirst()
                            .orElse(null);

            if (currentNode != null) {
                currentNodeLabel = currentNode.getLabel();

                // Resolve current department from partition
                if (currentNode.getPartitionId() != null && policy.getPartitions() != null) {
                    currentDepartmentId = policy.getPartitions().stream()
                            .filter(p -> p.getId().equals(currentNode.getPartitionId()))
                            .map(ActivityPartition::getDepartmentId)
                            .findFirst()
                            .orElse(null);

                    if (currentDepartmentId != null) {
                        currentDepartmentName = departmentRepository.findById(currentDepartmentId)
                                .map(Department::name)
                                .orElse(currentDepartmentId);
                    }
                }
            }

            // Build visual progress timeline (RF-00b)
            nodeProgress = buildNodeProgress(instance, policy);

            // What does the CLIENT need to do now? (pending mandatory document at the current node)
            pendingClientAction = computePendingClientAction(instance, policy);
        }

        // % of stages completed (ACTION nodes only)
        int total = nodeProgress.size();
        long done = nodeProgress.stream().filter(n -> "COMPLETED".equals(n.progressStatus())).count();
        int progressPercent = (instance.getStatus() == com.ibpms.domain.enums.InstanceStatus.COMPLETED)
                ? 100
                : (total > 0 ? (int) Math.round(done * 100.0 / total) : 0);

        return new ProcessStatusResponse(
                instance.getId(),
                instance.getBusinessPolicyId(),
                instance.getCurrentNodeId(),
                currentNodeLabel,
                currentDepartmentId,
                currentDepartmentName,
                instance.getStatus(),
                instance.getStartedAt(),
                instance.getCompletedAt(),
                instance.getClientId(),
                policyName,
                nodeProgress,
                progressPercent,
                pendingClientAction
        );
    }

    /**
     * If the trámite is active and the current node requires a document the CLIENT must still
     * upload (uploaderRole CLIENT, uploadStage = current node, not yet confirmed), returns a
     * human-friendly call to action. Otherwise null (nothing pending from the client).
     */
    private String computePendingClientAction(ProcessInstance instance, BusinessPolicy policy) {
        if (instance.getStatus() != com.ibpms.domain.enums.InstanceStatus.ACTIVE) return null;
        if (policy.getDocumentRequirements() == null) return null;

        String currentNodeId = instance.getCurrentNodeId();
        Set<String> confirmedReqIds = documentRepository
                .findByProcessInstanceIdAndStatusNot(instance.getId(),
                        com.ibpms.domain.enums.DocumentStatus.DELETED)
                .stream()
                .filter(d -> d.getStatus() == com.ibpms.domain.enums.DocumentStatus.CONFIRMED)
                .map(com.ibpms.domain.ProcessDocument::getDocumentRequirementId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        return policy.getDocumentRequirements().stream()
                .filter(r -> "CLIENT".equalsIgnoreCase(r.getUploaderRole()))
                .filter(r -> currentNodeId != null && currentNodeId.equals(r.getUploadStage()))
                .filter(r -> !confirmedReqIds.contains(r.getId()))
                .map(r -> "Debes subir: " + r.getName())
                .findFirst()
                .orElse(null);
    }

    /**
     * Builds the visual timeline for the Flutter app (RF-00b).
     * Only ACTION nodes are shown (they are the meaningful user-facing steps).
     * Status: COMPLETED if the task exists and is done; CURRENT if in progress/pending;
     * PENDING if the process hasn't reached that node yet.
     */
    private List<NodeProgressItem> buildNodeProgress(ProcessInstance instance, BusinessPolicy policy) {
        if (policy.getNodes() == null) return List.of();

        // Fetch all tasks for this instance
        List<ActivityTask> tasks = taskRepository.findByProcessInstanceId(instance.getId());
        Map<String, ActivityTask> taskByNodeId = tasks.stream()
                .collect(Collectors.toMap(ActivityTask::getNodeId, Function.identity(), (a, b) -> a));

        // Resolve responsible employee names (assignedUserId → username)
        Set<String> assignedUserIds = tasks.stream()
                .map(ActivityTask::getAssignedUserId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());
        Map<String, String> userNameById = userRepository.findAllById(assignedUserIds).stream()
                .collect(Collectors.toMap(com.ibpms.domain.User::getId,
                        u -> u.getUsername() != null ? u.getUsername() : u.getEmail(), (a, b) -> a));

        // Count documents attached to each task (per stage)
        Map<String, Long> docCountByTaskId = documentRepository
                .findByProcessInstanceIdAndStatusNot(instance.getId(),
                        com.ibpms.domain.enums.DocumentStatus.DELETED)
                .stream()
                .filter(d -> d.getTaskId() != null)
                .collect(Collectors.groupingBy(com.ibpms.domain.ProcessDocument::getTaskId, Collectors.counting()));

        // Build department name lookup
        Set<String> deptIds = policy.getPartitions() == null ? Set.of() :
                policy.getPartitions().stream()
                        .map(ActivityPartition::getDepartmentId)
                        .collect(Collectors.toSet());
        Map<String, String> deptNameById = departmentRepository.findAllById(deptIds).stream()
                .collect(Collectors.toMap(Department::id, Department::name));

        Map<String, String> deptIdByPartitionId = policy.getPartitions() == null ? Map.of() :
                policy.getPartitions().stream()
                        .collect(Collectors.toMap(ActivityPartition::getId,
                                ActivityPartition::getDepartmentId, (a, b) -> a));

        return policy.getNodes().stream()
                .filter(n -> n.getType() == NodeType.ACTION) // Only show ACTION nodes in timeline
                .map(node -> {
                    String deptId = deptIdByPartitionId.get(node.getPartitionId());
                    String deptName = deptId != null ? deptNameById.getOrDefault(deptId, deptId) : null;

                    ActivityTask task = taskByNodeId.get(node.getId());
                    String progressStatus;
                    java.time.LocalDateTime completedAt = null;
                    String assignedToName = null;
                    int documentCount = 0;

                    if (task == null) {
                        progressStatus = "PENDING";
                    } else if (task.getStatus() == TaskStatus.COMPLETED) {
                        progressStatus = "COMPLETED";
                        completedAt = task.getCompletedAt();
                    } else {
                        progressStatus = "CURRENT";
                    }
                    if (task != null) {
                        assignedToName = userNameById.get(task.getAssignedUserId());
                        documentCount = docCountByTaskId.getOrDefault(task.getId(), 0L).intValue();
                    }

                    return new NodeProgressItem(
                            node.getId(),
                            node.getLabel(),
                            deptName,
                            progressStatus,
                            completedAt,
                            assignedToName,
                            documentCount
                    );
                })
                .toList();
    }
}
