package com.ibpms.repository;

import com.ibpms.domain.Department;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DepartmentRepository extends MongoRepository<Department, String> {
    boolean existsByName(String name);
}

