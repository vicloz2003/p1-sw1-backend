package com.ibpms.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Initializes the Firebase Admin SDK on startup (RF-28).
 *
 * <p>Supply the path to a Firebase service-account JSON file via the
 * {@code FIREBASE_SERVICE_ACCOUNT_PATH} environment variable (or
 * {@code firebase.service-account-path} in {@code application.properties}).
 *
 * <p>If the path is empty or the file cannot be read, Firebase is NOT
 * initialised and push notifications are silently skipped (graceful
 * degradation for development environments where Firebase is not configured).
 */
@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${firebase.service-account-path:}")
    private String serviceAccountPath;

    @PostConstruct
    public void initializeFirebase() {
        if (serviceAccountPath == null || serviceAccountPath.isBlank()) {
            log.warn("[Firebase] FIREBASE_SERVICE_ACCOUNT_PATH is not set — " +
                     "push notifications will be disabled.");
            return;
        }

        // Avoid double-initialisation (happens during tests or hot-reload)
        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("[Firebase] FirebaseApp already initialised — skipping.");
            return;
        }

        try (InputStream serviceAccount = new FileInputStream(serviceAccountPath)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();
            FirebaseApp.initializeApp(options);
            log.info("[Firebase] FirebaseApp initialised successfully from '{}'.", serviceAccountPath);
        } catch (IOException e) {
            log.error("[Firebase] Failed to initialise FirebaseApp from '{}': {}. " +
                      "Push notifications will be disabled.", serviceAccountPath, e.getMessage());
        }
    }
}
