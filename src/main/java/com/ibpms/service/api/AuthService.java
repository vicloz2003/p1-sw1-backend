package com.ibpms.service.api;

import com.ibpms.dto.request.LoginRequest;
import com.ibpms.dto.request.LogoutRequest;
import com.ibpms.dto.request.RefreshTokenRequest;
import com.ibpms.dto.request.RegisterRequest;
import com.ibpms.dto.response.LoginResponse;
import com.ibpms.dto.response.RefreshResponse;
import com.ibpms.dto.response.RegisterResponse;

public interface AuthService {
    LoginResponse login(LoginRequest request);
    RegisterResponse register(RegisterRequest request);
    RefreshResponse refresh(RefreshTokenRequest request);
    void logout(LogoutRequest request);
}

