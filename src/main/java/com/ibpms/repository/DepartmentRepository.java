package com.ibpms.repository;

import com.ibpms.domain.Department;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface DepartmentRepository extends MongoRepository<Department, String> {
    boolean existsByName(String name);
    Optional<Department> findByName(String name);
}

