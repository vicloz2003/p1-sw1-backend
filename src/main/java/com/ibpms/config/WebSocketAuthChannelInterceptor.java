package com.ibpms.config;

import com.ibpms.domain.ProcessInstance;
import com.ibpms.domain.enums.SystemRole;
import com.ibpms.repository.ProcessInstanceRepository;
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
    private static final String USER_QUEUE_PREFIX = "/queue/user/";
    private static final String PROCESS_TOPIC_PREFIX = "/topic/process/";

    private final JwtService jwtService;
    private final ProcessInstanceRepository processInstanceRepository;

    public WebSocketAuthChannelInterceptor(JwtService jwtService,
                                           ProcessInstanceRepository processInstanceRepository) {
        this.jwtService = jwtService;
        this.processInstanceRepository = processInstanceRepository;
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
        if (destination == null) {
            return;
        }

        // Only the three protected destination families require authorization.
        boolean isProtected = destination.startsWith(DEPT_TOPIC_PREFIX)
                || destination.startsWith(USER_QUEUE_PREFIX)
                || destination.startsWith(PROCESS_TOPIC_PREFIX);
        if (!isProtected) {
            return;
        }

        Principal principal = accessor.getUser();
        if (!(principal instanceof JwtPrincipal jwtPrincipal)) {
            throw new AccessDeniedException(
                    "Not authenticated — cannot subscribe to " + destination);
        }

        if (destination.startsWith(DEPT_TOPIC_PREFIX)) {
            authorizeDepartmentTopic(destination, jwtPrincipal);
        } else if (destination.startsWith(USER_QUEUE_PREFIX)) {
            authorizeUserQueue(destination, jwtPrincipal);
        } else {
            authorizeProcessTopic(destination, jwtPrincipal);
        }
    }

    /** {@code /topic/department/{id}}: caller must belong to {id}; ADMIN bypasses. */
    private void authorizeDepartmentTopic(String destination, JwtPrincipal jwtPrincipal) {
        if (SystemRole.ADMIN_DESIGNER.name().equals(jwtPrincipal.role())) {
            return;
        }
        String topicDeptId = segmentAfter(destination, DEPT_TOPIC_PREFIX);
        String principalDepartmentId = jwtPrincipal.departmentId();
        if (principalDepartmentId == null || !topicDeptId.equals(principalDepartmentId)) {
            throw new AccessDeniedException(
                    "Forbidden: not a member of department " + topicDeptId);
        }
    }

    /** {@code /queue/user/{userId}}: caller may only subscribe to their own queue; ADMIN bypasses. */
    private void authorizeUserQueue(String destination, JwtPrincipal jwtPrincipal) {
        if (SystemRole.ADMIN_DESIGNER.name().equals(jwtPrincipal.role())) {
            return;
        }
        String topicUserId = segmentAfter(destination, USER_QUEUE_PREFIX);
        if (!topicUserId.equals(jwtPrincipal.userId())) {
            throw new AccessDeniedException("Forbidden: cannot subscribe to another user's queue");
        }
    }

    /**
     * {@code /topic/process/{id}}: ADMIN and EMPLOYEE have operational access to any
     * process; a CLIENT may only watch processes they initiated or are the client of.
     */
    private void authorizeProcessTopic(String destination, JwtPrincipal jwtPrincipal) {
        if (SystemRole.ADMIN_DESIGNER.name().equals(jwtPrincipal.role())
                || SystemRole.EMPLOYEE.name().equals(jwtPrincipal.role())) {
            return;
        }
        String instanceId = segmentAfter(destination, PROCESS_TOPIC_PREFIX);
        ProcessInstance instance = processInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new AccessDeniedException(
                        "Forbidden: process not found or not accessible"));
        String userId = jwtPrincipal.userId();
        boolean owns = userId != null
                && (userId.equals(instance.getClientId()) || userId.equals(instance.getInitiatedBy()));
        if (!owns) {
            throw new AccessDeniedException("Forbidden: not your process");
        }
    }

    /** Extracts the path segment after {@code prefix}, rejecting path-traversal attempts. */
    private String segmentAfter(String destination, String prefix) {
        String segment = destination.substring(prefix.length());
        if (segment.isEmpty() || segment.contains("/") || segment.contains("..")) {
            throw new AccessDeniedException("Invalid subscription destination");
        }
        return segment;
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

