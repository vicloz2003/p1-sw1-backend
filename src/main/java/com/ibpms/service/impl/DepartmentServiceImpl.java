package com.ibpms.service.impl;

import com.ibpms.domain.Department;
import com.ibpms.dto.request.CreateDepartmentRequest;
import com.ibpms.repository.DepartmentRepository;
import com.ibpms.service.api.DepartmentService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentRepository departmentRepository;

    public DepartmentServiceImpl(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @Override
    public List<Department> getAll() {
        return departmentRepository.findAll();
    }

    @Override
    public Department create(CreateDepartmentRequest request) {
        Department department = new Department(null, request.name(), request.description());
        return departmentRepository.save(department);
    }
}

