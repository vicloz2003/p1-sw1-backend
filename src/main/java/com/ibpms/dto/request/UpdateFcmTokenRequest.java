package com.ibpms.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Body for {@code PATCH /api/v1/profile/fcm-token} (RF-28).
 * Flutter sends this after obtaining the FCM device token on login.
 */
public record UpdateFcmTokenRequest(
        @NotBlank(message = "fcmToken must not be blank")
        String fcmToken
) {}
