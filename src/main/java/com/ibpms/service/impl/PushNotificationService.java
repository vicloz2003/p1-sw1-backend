package com.ibpms.service.impl;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Sends Firebase Cloud Messaging (FCM) push notifications asynchronously
 * so the workflow engine thread is never blocked (RF-29, RF-30).
 *
 * <p>All methods are no-ops if the Firebase SDK has not been initialised
 * (i.e. {@code FIREBASE_SERVICE_ACCOUNT_PATH} was not provided).
 */
@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    // ── Single-device notification ────────────────────────────────────────────

    /**
     * Sends a push notification to a single FCM token.
     *
     * @param fcmToken     the device registration token
     * @param title        notification title
     * @param body         notification body
     * @param data         optional key-value pairs delivered in the data payload
     */
    @Async
    public void sendToToken(String fcmToken, String title, String body, Map<String, String> data) {
        if (!isFirebaseReady() || fcmToken == null || fcmToken.isBlank()) return;

        try {
            Message.Builder builder = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build());
            if (data != null) {
                data.forEach(builder::putData);
            }
            String messageId = FirebaseMessaging.getInstance().send(builder.build());
            log.debug("[FCM] Sent to token {}: messageId={}", fcmToken, messageId);
        } catch (FirebaseMessagingException e) {
            log.warn("[FCM] Failed to send to token {}: {}", fcmToken, e.getMessage());
        }
    }

    // ── Multi-device notification ─────────────────────────────────────────────

    /**
     * Sends a push notification to multiple FCM tokens (up to 500 per call).
     * Tokens that have become invalid are logged and silently skipped.
     *
     * @param fcmTokens    device registration tokens (must not be empty)
     * @param title        notification title
     * @param body         notification body
     * @param data         optional key-value pairs delivered in the data payload
     */
    @Async
    public void sendToMultipleTokens(List<String> fcmTokens,
                                     String title,
                                     String body,
                                     Map<String, String> data) {
        if (!isFirebaseReady() || fcmTokens == null || fcmTokens.isEmpty()) return;

        try {
            MulticastMessage.Builder builder = MulticastMessage.builder()
                    .addAllTokens(fcmTokens)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build());
            if (data != null) {
                data.forEach(builder::putData);
            }
            var response = FirebaseMessaging.getInstance().sendEachForMulticast(builder.build());
            log.debug("[FCM] Multicast: {} success, {} failure(s) out of {} tokens.",
                    response.getSuccessCount(), response.getFailureCount(), fcmTokens.size());
        } catch (FirebaseMessagingException e) {
            log.warn("[FCM] Multicast failed: {}", e.getMessage());
        }
    }

    // ── Private helper ────────────────────────────────────────────────────────

    private boolean isFirebaseReady() {
        boolean ready = !FirebaseApp.getApps().isEmpty();
        if (!ready) {
            log.trace("[FCM] Firebase not initialised — skipping push notification.");
        }
        return ready;
    }
}
