package com.ibpms.service.impl;

import com.ibpms.domain.User;
import com.ibpms.dto.request.UpdateProfileEmailRequest;
import com.ibpms.dto.request.UpdateProfilePasswordRequest;
import com.ibpms.dto.response.UserResponse;
import com.ibpms.exception.EmailAlreadyExistsException;
import com.ibpms.exception.UserNotFoundException;
import com.ibpms.repository.UserRepository;
import com.ibpms.service.api.ProfileService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class ProfileServiceImpl implements ProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public ProfileServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserResponse getProfile(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return toResponse(user);
    }

    @Override
    public UserResponse updateEmail(String userId, UpdateProfileEmailRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid current password");
        }

        if (!user.getEmail().equalsIgnoreCase(request.email())
                && userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        user.setEmail(request.email());
        return toResponse(userRepository.save(user));
    }

    @Override
    public void updatePassword(String userId, UpdateProfilePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid current password");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.getDepartmentId(),
                user.isEnabled()
        );
    }
}
