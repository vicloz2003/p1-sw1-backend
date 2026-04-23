package com.ibpms.service.api;

import com.ibpms.dto.request.UpdateProfileEmailRequest;
import com.ibpms.dto.request.UpdateProfilePasswordRequest;
import com.ibpms.dto.response.UserResponse;

public interface ProfileService {
    UserResponse getProfile(String userId);
    UserResponse updateEmail(String userId, UpdateProfileEmailRequest request);
    void updatePassword(String userId, UpdateProfilePasswordRequest request);
}
