package com.ibpms.service.api;

import com.ibpms.dto.request.AssignDepartmentRequest;
import com.ibpms.dto.request.UpdateUserRequest;
import com.ibpms.dto.response.UserResponse;

import java.util.List;

public interface UserService {
    List<UserResponse> getAll();
    UserResponse assignDepartment(String userId, AssignDepartmentRequest request);
    List<UserResponse> searchByEmail(String email);
    UserResponse updateUser(String id, UpdateUserRequest request);
    UserResponse toggleStatus(String id);
    void deleteUser(String id);
}

