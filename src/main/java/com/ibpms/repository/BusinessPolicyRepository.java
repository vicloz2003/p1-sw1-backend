package com.ibpms.repository;

import com.ibpms.domain.BusinessPolicy;
import com.ibpms.domain.enums.PolicyStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface BusinessPolicyRepository extends MongoRepository<BusinessPolicy, String> {
    List<BusinessPolicy> findByStatus(PolicyStatus status);
    boolean existsByName(String name);
}


