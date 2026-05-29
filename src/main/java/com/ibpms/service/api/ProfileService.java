package com.ibpms.service.api;

import com.ibpms.dto.request.UpdateFcmTokenRequest;
import com.ibpms.dto.request.UpdateProfileEmailRequest;
import com.ibpms.dto.request.UpdateProfilePasswordRequest;
import com.ibpms.dto.response.UserResponse;

public interface ProfileService {
    UserResponse getProfile(String userId);
    UserResponse updateEmail(String userId, UpdateProfileEmailRequest request);
    void updatePassword(String userId, UpdateProfilePasswordRequest request);
    /** Registers or updates the Firebase Cloud Messaging token for a user's device (RF-28). */
    void updateFcmToken(String userId, UpdateFcmTokenRequest request);
}
