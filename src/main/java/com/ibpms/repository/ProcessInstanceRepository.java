package com.ibpms.repository;

import com.ibpms.domain.ProcessInstance;
import com.ibpms.domain.enums.InstanceStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface ProcessInstanceRepository extends MongoRepository<ProcessInstance, String> {
    boolean existsByBusinessPolicyIdAndStatus(String businessPolicyId, InstanceStatus status);
    List<ProcessInstance> findByBusinessPolicyId(String businessPolicyId);
    List<ProcessInstance> findByStatus(InstanceStatus status);
}


