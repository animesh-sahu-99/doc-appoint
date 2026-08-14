package com.clinic.doc_appointment.service.push;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Firebase Cloud Messaging settings.
 *
 * <p>Uses {@code @ConfigurationProperties} rather than the constructor-{@code @Value} style used
 * elsewhere in this codebase ({@code JwtService}, {@code LoginRateLimiter}). That is a deliberate
 * departure, not drift: the push settings are numerous enough that threading them through
 * constructors would trade one kind of readability for a worse one.
 */
@Component
@ConfigurationProperties(prefix = "fcm")
@Getter
@Setter
public class FcmProperties {

    /**
     * Where the service-account JSON lives. Accepts any Spring resource location, so it works
     * whether the file is packaged on the classpath or mounted into the container at deploy time
     * — e.g. {@code file:/etc/secrets/firebase-service-account.json}.
     */
    private String credentialsLocation = "classpath:firebase-service-account.json";

    /**
     * Tokens per multicast call, clamped to FCM's hard cap of 500. Note each token is still its
     * own HTTP request issued concurrently by the SDK — this bounds fan-out, it does not batch
     * at the HTTP level.
     */
    private int batchSize = 500;

    /** Must match the notification channel the Android client creates, or alerts are silent. */
    private String androidChannelId = "medibook_channel";

    private String sound = "default";

    /**
     * Whether unreadable credentials should stop startup.
     *
     * <p>Default true: a credentials file that is present but corrupt is a broken deploy, not a
     * local-dev state, and booting anyway is how a production instance ends up silently
     * delivering nothing. A missing file is different — that always degrades gracefully.
     */
    private boolean failOnInvalidCredentials = true;
}
