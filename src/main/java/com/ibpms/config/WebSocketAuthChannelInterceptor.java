package com.ibpms.config;

import com.ibpms.domain.enums.SystemRole;
import com.ibpms.security.JwtService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * Validates STOMP subscriptions to department topics.
 *
 * <p><strong>CONNECT</strong>: extracts the JWT from the {@code Authorization} header,
 * validates it, and attaches a {@link JwtPrincipal} to the session so that all
 * subsequent frames can read the caller's claims without touching the database.
 *
 * <p><strong>SUBSCRIBE</strong>: for destinations matching
 * {@code /topic/department/{id}}, verifies that the subscriber's {@code departmentId}
 * claim matches {@code {id}}. {@code ADMIN_DESIGNER} users bypass this check and may
 * subscribe to any department topic.
 */
@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final String DEPT_TOPIC_PREFIX = "/topic/department/";

    private final JwtService jwtService;

    public WebSocketAuthChannelInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            handleConnect(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            handleSubscribe(accessor);
        }

        return message;
    }

    // -------------------------------------------------------------------------
    // Handlers
    // -------------------------------------------------------------------------

    private void handleConnect(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // Fix 1: reject connections that carry no token
            throw new AccessDeniedException("Missing Authorization header");
        }
        String token = authHeader.substring(7);
        if (!jwtService.isTokenValid(token)) {
            throw new AccessDeniedException("Invalid JWT token");
        }
        // Fix 3: wrap claim extraction so internal JWT parse details are never exposed
        try {
            accessor.setUser(new JwtPrincipal(
                    jwtService.extractUserId(token),
                    jwtService.extractRole(token),
                    jwtService.extractDepartmentId(token)
            ));
        } catch (Exception e) {
            throw new AccessDeniedException(
                    "Invalid JWT token (" + e.getClass().getSimpleName() + ")", e);
        }
    }

    private void handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(DEPT_TOPIC_PREFIX)) {
            return; // Not a department topic — no restriction
        }

        Principal principal = accessor.getUser();
        if (!(principal instanceof JwtPrincipal jwtPrincipal)) {
            throw new AccessDeniedException(
                    "Not authenticated — cannot subscribe to " + destination);
        }

        // ADMIN_DESIGNER may monitor any department
        if (SystemRole.ADMIN_DESIGNER.name().equals(jwtPrincipal.role())) {
            return;
        }

        String topicDeptId = destination.substring(DEPT_TOPIC_PREFIX.length());
        // Fix 2: prevent path-traversal attacks in the department ID segment
        if (topicDeptId.contains("/") || topicDeptId.contains("..")) {
            throw new AccessDeniedException("Invalid department topic");
        }
        String principalDepartmentId = jwtPrincipal.departmentId();
        if (principalDepartmentId == null ||
                !topicDeptId.equals(principalDepartmentId)) {
            throw new AccessDeniedException(
                    "Forbidden: not a member of department " + topicDeptId);
        }
    }

    // -------------------------------------------------------------------------
    // Principal
    // -------------------------------------------------------------------------

    /**
     * Lightweight principal that carries the three JWT claims needed for
     * WebSocket authorization without an additional database round-trip.
     */
    private record JwtPrincipal(String userId, String role, String departmentId)
            implements Principal {
        @Override
        public String getName() {
            return userId;
        }
    }
}

