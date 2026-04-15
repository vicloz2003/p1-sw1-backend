package com.ibpms.service.impl;

import com.ibpms.domain.User;
import com.ibpms.dto.request.AssignDepartmentRequest;
import com.ibpms.dto.response.UserResponse;
import com.ibpms.exception.UserNotFoundException;
import com.ibpms.repository.UserRepository;
import com.ibpms.service.api.UserService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public List<UserResponse> getAll() {
        return userRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public UserResponse assignDepartment(String userId, AssignDepartmentRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        user.setDepartmentId(request.departmentId());
        return toResponse(userRepository.save(user));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.getDepartmentId()
        );
    }
}

