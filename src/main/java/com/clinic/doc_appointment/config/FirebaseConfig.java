package com.clinic.doc_appointment.config;

import com.clinic.doc_appointment.service.push.FcmProperties;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.lang.Nullable;

import java.io.InputStream;

/**
 * Initializes Firebase, if credentials are available.
 *
 * <p>Two changes from the previous {@code @PostConstruct} version, both about making failure
 * visible:
 *
 * <ol>
 *   <li><strong>Real beans instead of a static side effect.</strong> {@link FirebaseMessaging} is
 *       now injectable, so {@code FcmPushAdapter} no longer reaches for a static singleton and
 *       can be unit-tested without static mocking.</li>
 *   <li><strong>Missing and broken credentials are no longer the same thing.</strong> Absent
 *       credentials warn and degrade, which is correct for a developer machine. Credentials that
 *       are present but unreadable fail startup, because that is a broken deploy — the old
 *       blanket {@code catch (Exception)} treated a corrupt production key exactly like a missing
 *       local file, which is precisely how a push-less instance ships unnoticed.</li>
 * </ol>
 *
 * <p>The location is a Spring resource string, so the same code works whether the file is
 * packaged on the classpath or mounted into the container ({@code file:/etc/secrets/...}).
 */
@Configuration
@Slf4j
public class FirebaseConfig {

    /**
     * @return null when no credentials are configured; consumers must treat that as "push
     *         disabled" rather than an error.
     */
    @Bean
    @Nullable
    public FirebaseApp firebaseApp(FcmProperties properties, ResourceLoader resourceLoader) {
        String location = properties.getCredentialsLocation();
        Resource resource = resourceLoader.getResource(location);

        if (!resource.exists()) {
            log.warn("FCM DISABLED: no Firebase credentials at '{}'. Push notifications will be "
                    + "queued and skipped, not delivered. Set fcm.credentials-location to enable.", location);
            return null;
        }

        // Guard against devtools/context restarts leaving the static default app registered.
        for (FirebaseApp existing : FirebaseApp.getApps()) {
            if (FirebaseApp.DEFAULT_APP_NAME.equals(existing.getName())) {
                log.info("Reusing already-initialized FirebaseApp.");
                return existing;
            }
        }

        try (InputStream credentials = resource.getInputStream()) {
            FirebaseApp app = FirebaseApp.initializeApp(FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentials))
                    .build());
            log.info("Firebase initialized from '{}'. FCM push delivery is ENABLED.", location);
            return app;
        } catch (Exception e) {
            // The file EXISTS and we still could not use it — a deploy problem, not a dev machine.
            if (properties.isFailOnInvalidCredentials()) {
                throw new IllegalStateException(
                        "Firebase credentials at '" + location + "' could not be read. Fix the file or set "
                                + "fcm.fail-on-invalid-credentials=false to boot without push.", e);
            }
            log.error("FCM DISABLED: Firebase credentials at '{}' are present but unreadable.", location, e);
            return null;
        }
    }

    /** Absent whenever {@link #firebaseApp} yielded null, which is what disables push. */
    @Bean
    @Nullable
    public FirebaseMessaging firebaseMessaging(@Nullable FirebaseApp firebaseApp) {
        return firebaseApp == null ? null : FirebaseMessaging.getInstance(firebaseApp);
    }
}
