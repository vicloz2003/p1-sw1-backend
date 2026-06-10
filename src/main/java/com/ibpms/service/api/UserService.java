package com.ibpms.service.api;

import com.ibpms.dto.request.AssignDepartmentRequest;
import com.ibpms.dto.request.UpdateUserRequest;
import com.ibpms.dto.response.UserResponse;

import java.util.List;

public interface UserService {
    List<UserResponse> getAll();
    UserResponse assignDepartment(String userId, AssignDepartmentRequest request);
    List<UserResponse> searchByEmail(String email);

    /** Search by name OR email (max 10 results). */
    List<UserResponse> search(String q);

    /** Resolve a batch of user IDs to their full data (unknown IDs are silently ignored). */
    List<UserResponse> getByIds(List<String> ids);
    UserResponse updateUser(String id, UpdateUserRequest request);
    UserResponse toggleStatus(String id);
    void deleteUser(String id);
}

