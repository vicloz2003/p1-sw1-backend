package com.ibpms.controller;

import com.ibpms.dto.request.UpdateFcmTokenRequest;
import com.ibpms.dto.request.UpdateProfileEmailRequest;
import com.ibpms.dto.request.UpdateProfilePasswordRequest;
import com.ibpms.dto.response.UserResponse;
import com.ibpms.service.api.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ResponseEntity<UserResponse> getProfile(Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        return ResponseEntity.ok(profileService.getProfile(userId));
    }

    @PatchMapping("/email")
    public ResponseEntity<UserResponse> updateEmail(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileEmailRequest request) {
        String userId = (String) authentication.getPrincipal();
        return ResponseEntity.ok(profileService.updateEmail(userId, request));
    }

    @PatchMapping("/password")
    public ResponseEntity<Void> updatePassword(
            Authentication authentication,
            @Valid @RequestBody UpdateProfilePasswordRequest request) {
        String userId = (String) authentication.getPrincipal();
        profileService.updatePassword(userId, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Registers or refreshes the Firebase Cloud Messaging token for the caller's
     * mobile device (RF-28). Flutter calls this right after login.
     */
    @PatchMapping("/fcm-token")
    public ResponseEntity<Void> updateFcmToken(
            Authentication authentication,
            @Valid @RequestBody UpdateFcmTokenRequest request) {
        String userId = (String) authentication.getPrincipal();
        profileService.updateFcmToken(userId, request);
        return ResponseEntity.noContent().build();
    }
}
