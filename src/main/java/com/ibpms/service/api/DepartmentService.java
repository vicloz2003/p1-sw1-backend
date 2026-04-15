package com.ibpms.service.api;

import com.ibpms.domain.Department;
import com.ibpms.dto.request.CreateDepartmentRequest;

import java.util.List;

public interface DepartmentService {
    List<Department> getAll();
    Department create(CreateDepartmentRequest request);
}

