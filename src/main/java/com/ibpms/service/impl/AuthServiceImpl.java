package com.ibpms.service.impl;

import com.ibpms.domain.RefreshToken;
import com.ibpms.domain.User;
import com.ibpms.dto.request.LoginRequest;
import com.ibpms.dto.request.LogoutRequest;
import com.ibpms.dto.request.RefreshTokenRequest;
import com.ibpms.dto.request.RegisterRequest;
import com.ibpms.dto.response.LoginResponse;
import com.ibpms.dto.response.RefreshResponse;
import com.ibpms.dto.response.RegisterResponse;
import com.ibpms.exception.EmailAlreadyExistsException;
import com.ibpms.exception.InvalidRefreshTokenException;
import com.ibpms.exception.UsernameAlreadyExistsException;
import com.ibpms.repository.RefreshTokenRepository;
import com.ibpms.repository.UserRepository;
import com.ibpms.security.JwtService;
import com.ibpms.service.api.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final int refreshTokenExpirationDays;

    public AuthServiceImpl(UserRepository userRepository,
                           RefreshTokenRepository refreshTokenRepository,
                           PasswordEncoder passwordEncoder,
                           JwtService jwtService,
                           AuthenticationManager authenticationManager,
                           @Value("${jwt.refresh-token-expiration-days:7}") int refreshTokenExpirationDays) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.refreshTokenExpirationDays = refreshTokenExpirationDays;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        // Authenticate — delegates to UserDetailsServiceImpl.loadUserByUsername(email)
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + request.email()));

        return buildLoginResponse(user);
    }

    @Override
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new UsernameAlreadyExistsException(request.username());
        }

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setDepartmentId(request.departmentId());

        User saved = userRepository.save(user);
        return buildRegisterResponse(saved);
    }

    @Override
    public RefreshResponse refresh(RefreshTokenRequest request) {
        String hash = hashToken(request.refreshToken());

        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token not found"));

        if (stored.isRevoked()) {
            throw new InvalidRefreshTokenException("Refresh token has been revoked");
        }
        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(stored);
            throw new InvalidRefreshTokenException("Refresh token has expired");
        }

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + stored.getUserId()));

        // Rotate only after confirming the associated user exists
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);
        return buildRefreshResponse(user);
    }

    @Override
    public void logout(LogoutRequest request) {
        String hash = hashToken(request.refreshToken());

        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Generates tokens, persists the hashed refresh token, and returns RegisterResponse. */
    private RegisterResponse buildRegisterResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = persistRefreshToken(user);
        return new RegisterResponse(
                accessToken,
                rawRefreshToken,
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.getDepartmentId(),
                jwtService.getAccessTokenExpirationMs()
        );
    }

    /** Generates tokens, persists the hashed refresh token, and returns LoginResponse. */
    private LoginResponse buildLoginResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = persistRefreshToken(user);
        return new LoginResponse(
                accessToken,
                rawRefreshToken,
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.getDepartmentId(),
                jwtService.getAccessTokenExpirationMs()
        );
    }

    /** Generates tokens, persists the hashed refresh token, and returns RefreshResponse. */
    private RefreshResponse buildRefreshResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = persistRefreshToken(user);
        return new RefreshResponse(
                accessToken,
                rawRefreshToken,
                jwtService.getAccessTokenExpirationMs()
        );
    }

    /** Generates a raw refresh token, hashes and persists it, returns the raw token. */
    private String persistRefreshToken(User user) {
        String rawRefreshToken = jwtService.generateRawRefreshToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setTokenHash(hashToken(rawRefreshToken));
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(refreshTokenExpirationDays));
        refreshToken.setRevoked(false);
        refreshTokenRepository.save(refreshToken);
        return rawRefreshToken;
    }

    /** SHA-256 hex hash — raw tokens are never stored in MongoDB. */
    private static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

