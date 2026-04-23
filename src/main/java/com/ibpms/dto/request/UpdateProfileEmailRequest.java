package com.ibpms.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UpdateProfileEmailRequest(
        @NotBlank @Email String email,
        @NotBlank String currentPassword
) {}
