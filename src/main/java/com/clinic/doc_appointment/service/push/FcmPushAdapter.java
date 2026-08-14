package com.clinic.doc_appointment.service.push;

import com.clinic.doc_appointment.util.TokenMasker;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Delivers push notifications via Firebase Cloud Messaging. All Firebase coupling lives here,
 * behind the {@link PushNotificationProvider} port.
 *
 * <p><strong>This class holds no repository and is not transactional, deliberately.</strong> It
 * is called from {@link PushOutboxWorker} with no transaction open, so that a multi-second FCM
 * fan-out never holds a database connection. Re-introducing a repository here would recreate the
 * exact defect this redesign removed — dead tokens are reported through
 * {@link PushSendReport#deadTokens()} for the worker to act on.
 */
@Service
@Slf4j
public class FcmPushAdapter implements PushNotificationProvider {

    /** FCM's own hard cap; the SDK throws above it. */
    static final int MAX_TOKENS_PER_MULTICAST = 500;

    /** Null when no credentials are configured — the app still boots and this no-ops. */
    private final FirebaseMessaging messaging;
    private final FcmErrorClassifier classifier;
    private final FcmProperties properties;

    /** Makes the first "push is not configured" skip loud, and every one after it quiet. */
    private final AtomicBoolean unconfiguredWarningEmitted = new AtomicBoolean(false);

    /**
     * Written by hand rather than with {@code @RequiredArgsConstructor}: Lombok will not propagate
     * {@link Nullable} onto the generated parameter, and without it Spring treats the missing
     * {@link FirebaseMessaging} bean as a hard dependency and refuses to start.
     */
    public FcmPushAdapter(@Nullable FirebaseMessaging messaging,
                          FcmErrorClassifier classifier,
                          FcmProperties properties) {
        this.messaging = messaging;
        this.classifier = classifier;
        this.properties = properties;
    }

    @Override
    public PushSendReport send(List<String> tokens, PushMessage message) {
        if (tokens == null || tokens.isEmpty()) {
            return PushSendReport.empty();
        }

        List<PushSendResult> results = new ArrayList<>(tokens.size());
        List<String> sendable = partitionSendable(tokens, results);

        if (sendable.isEmpty()) {
            return new PushSendReport(results);
        }

        if (messaging == null) {
            reportUnconfigured(sendable, results);
            return new PushSendReport(results);
        }

        for (int start = 0; start < sendable.size(); start += chunkSize()) {
            List<String> chunk = sendable.subList(start, Math.min(start + chunkSize(), sendable.size()));
            results.addAll(sendChunk(chunk, message));
        }

        logSummary(results);
        return new PushSendReport(results);
    }

    /**
     * Splits out tokens we must not hand to the SDK, recording them as skipped.
     *
     * <p>Filtering blanks is mandatory, not defensive: {@code MulticastMessage.getMessageList()}
     * rejects the <em>entire</em> batch if any single token is null or empty, so one junk row in
     * {@code user_devices} would otherwise block every other device's notification.
     * Deduplication is belt-and-braces — {@code fcm_token} is unique — but a duplicate would
     * produce two conflicting results for one token.
     */
    private List<String> partitionSendable(List<String> tokens, List<PushSendResult> results) {
        LinkedHashSet<String> sendable = new LinkedHashSet<>();
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                results.add(PushSendResult.failure(token, PushOutcome.SKIPPED,
                        FcmErrorClassifier.BLANK_TOKEN, "blank device token ignored"));
            } else {
                sendable.add(token);
            }
        }
        return new ArrayList<>(sendable);
    }

    private void reportUnconfigured(List<String> sendable, List<PushSendResult> results) {
        if (unconfiguredWarningEmitted.compareAndSet(false, true)) {
            log.warn("FCM is not configured on this instance — {} push notification(s) will not be "
                    + "delivered. Set fcm.credentials-location to enable push. "
                    + "(Further occurrences log at DEBUG.)", sendable.size());
        } else {
            log.debug("Skipping FCM send for {} token(s): provider not configured", sendable.size());
        }
        for (String token : sendable) {
            results.add(PushSendResult.failure(token, PushOutcome.PROVIDER_UNCONFIGURED,
                    FcmErrorClassifier.NO_PROVIDER, "Firebase credentials not configured"));
        }
    }

    private List<PushSendResult> sendChunk(List<String> chunk, PushMessage message) {
        try {
            BatchResponse batch = messaging.sendEachForMulticast(buildMulticast(chunk, message));
            return mapResponses(chunk, batch);

        } catch (FirebaseMessagingException e) {
            // A batch-level failure describes the HTTP exchange, not any device, so the classifier
            // never lets this destroy a token.
            PushOutcome outcome = classifier.classifyBatchLevel(e);
            log.warn("FCM batch of {} failed [{}] — {}", chunk.size(), classifier.codeOf(e), e.getMessage());
            return failWholeChunk(chunk, outcome, classifier.codeOf(e), e.getMessage());

        } catch (IllegalArgumentException e) {
            // An SDK precondition tripped — chunking and blank-filtering should make this
            // unreachable, so it means a bug here rather than anything about the devices.
            log.error("FCM precondition violated; chunk of {} not sent", chunk.size(), e);
            return failWholeChunk(chunk, PushOutcome.RETRY, FcmErrorClassifier.SDK_PRECONDITION, e.getMessage());

        } catch (RuntimeException e) {
            log.error("Unexpected FCM failure; chunk of {} not sent", chunk.size(), e);
            return failWholeChunk(chunk, PushOutcome.RETRY, FcmErrorClassifier.UNEXPECTED, e.getMessage());
        }
    }

    private MulticastMessage buildMulticast(List<String> chunk, PushMessage message) {
        return MulticastMessage.builder()
                .setNotification(Notification.builder()
                        .setTitle(message.title())
                        .setBody(message.body())
                        .build())
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(AndroidNotification.builder()
                                .setSound(properties.getSound())
                                .setChannelId(properties.getAndroidChannelId())
                                .build())
                        .build())
                .putData("type", nullToEmpty(message.type()))
                .putData("relatedEntityId", nullToEmpty(message.relatedEntityId()))
                .addAllTokens(chunk)
                .build();
    }

    /**
     * Maps each response back to its token by index.
     *
     * <p>The SDK returns responses in the same order as the input tokens, and that ordering is the
     * <em>only</em> link between a failure and a device. If the sizes ever disagree the mapping is
     * untrustworthy, and acting on it would deactivate the wrong user's device — so the whole
     * chunk is failed as retryable instead. This should never fire; it exists because the
     * consequence of it firing silently is severe.
     */
    private List<PushSendResult> mapResponses(List<String> chunk, BatchResponse batch) {
        List<SendResponse> responses = batch.getResponses();
        if (responses == null || responses.size() != chunk.size()) {
            log.error("FCM returned {} response(s) for {} token(s); refusing to map them. "
                            + "All {} token(s) will be retried and none deactivated.",
                    responses == null ? 0 : responses.size(), chunk.size(), chunk.size());
            return failWholeChunk(chunk, PushOutcome.RETRY,
                    FcmErrorClassifier.RESPONSE_SIZE_MISMATCH, "batch response size mismatch");
        }

        List<PushSendResult> results = new ArrayList<>(chunk.size());
        for (int i = 0; i < chunk.size(); i++) {
            String token = chunk.get(i);
            SendResponse response = responses.get(i);
            if (response.isSuccessful()) {
                results.add(PushSendResult.delivered(token, response.getMessageId()));
            } else {
                results.add(classifier.classifyPerToken(token, response.getException(), chunk.size()));
            }
        }
        return classifier.reconcileInvalidArgument(results);
    }

    private List<PushSendResult> failWholeChunk(List<String> chunk, PushOutcome outcome,
                                                String errorCode, String detail) {
        return chunk.stream()
                .map(token -> PushSendResult.failure(token, outcome, errorCode, detail))
                .toList();
    }

    private void logSummary(List<PushSendResult> results) {
        long delivered = results.stream().filter(PushSendResult::isDelivered).count();
        if (delivered == results.size()) {
            log.info("FCM: delivered {}/{}", delivered, results.size());
            return;
        }
        List<String> dead = results.stream()
                .filter(r -> r.outcome() == PushOutcome.DEAD_TOKEN)
                .map(r -> TokenMasker.mask(r.token()))
                .toList();
        log.warn("FCM: delivered {}/{} (dead tokens: {})", delivered, results.size(), dead);
    }

    private int chunkSize() {
        return Math.max(1, Math.min(properties.getBatchSize(), MAX_TOKENS_PER_MULTICAST));
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
