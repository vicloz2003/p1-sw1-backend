package com.ibpms.repository;

import com.ibpms.domain.Department;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DepartmentRepository extends MongoRepository<Department, String> {
    // No custom methods needed for workflow engine
}

