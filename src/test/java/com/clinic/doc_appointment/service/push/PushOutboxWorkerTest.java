package com.clinic.doc_appointment.service.push;

import com.clinic.doc_appointment.entity.PushOutboxMessage;
import com.clinic.doc_appointment.entity.UserDevice;
import com.clinic.doc_appointment.enums.NotificationType;
import com.clinic.doc_appointment.enums.PushOutboxStatus;
import com.clinic.doc_appointment.repository.PushOutboxRepository;
import com.clinic.doc_appointment.repository.UserDeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PushOutboxWorkerTest {

    private static final String TOKEN = "device-token-aaaaaaaaaaaaaaaaaaaa";
    private static final int MAX_ATTEMPTS = 5;

    private PushOutboxRepository outboxRepository;
    private UserDeviceRepository userDeviceRepository;
    private PushNotificationProvider provider;
    private PushProperties properties;
    private PushOutboxWorker worker;

    @BeforeEach
    void setUp() {
        outboxRepository = mock(PushOutboxRepository.class);
        userDeviceRepository = mock(UserDeviceRepository.class);
        provider = mock(PushNotificationProvider.class);

        properties = new PushProperties();
        properties.setMaxAttempts(MAX_ATTEMPTS);

        worker = new PushOutboxWorker(outboxRepository, userDeviceRepository, provider, properties);

        // Default: the row is due, we win the claim, and the user has one device.
        when(outboxRepository.findDue(anyCollection(), any(), any())).thenReturn(List.of(row(0)));
        when(outboxRepository.claim(anyString(), anyString(), any(), anyCollection(), any(), any()))
                .thenReturn(1);
        when(userDeviceRepository.findByUserIdAndIsActiveTrue(anyString()))
                .thenReturn(List.of(UserDevice.builder().fcmToken(TOKEN).isActive(true).build()));
    }

    private PushOutboxMessage row(int attempts) {
        return PushOutboxMessage.builder()
                .id("outbox-1")
                .notificationId("NOT-1")
                .userId("PAT-1")
                .title("Appointment Confirmed")
                .body("Your appointment is confirmed.")
                .type(NotificationType.APPOINTMENT_UPDATE)
                .status(PushOutboxStatus.PENDING)
                .attempts(attempts)
                .nextAttemptAt(Instant.now())
                .build();
    }

    private void providerReturns(PushSendResult... results) {
        when(provider.send(anyList(), any())).thenReturn(new PushSendReport(List.of(results)));
    }

    private void verifyFinishedWith(PushOutboxStatus expected) {
        ArgumentCaptor<PushOutboxStatus> captor = ArgumentCaptor.forClass(PushOutboxStatus.class);
        verify(outboxRepository).finish(anyString(), anyString(), any(), captor.capture(), any(), any());
        assertEquals(expected, captor.getValue());
    }

    // ===================== happy path =====================

    @Test
    void deliveredPushIsMarkedSent() {
        providerReturns(PushSendResult.delivered(TOKEN, "msg-1"));

        worker.drainDue();

        verifyFinishedWith(PushOutboxStatus.SENT);
        verify(outboxRepository, never()).scheduleRetry(anyString(), anyString(), any(), any(), any(), any(), any());
    }

    // ===================== the no-double-send guarantee =====================

    @Test
    void losingTheClaimMeansTheProviderIsNeverCalled() {
        when(outboxRepository.claim(anyString(), anyString(), any(), anyCollection(), any(), any()))
                .thenReturn(0);

        worker.drainDue();

        // This is the whole point of the CAS: another instance owns the row, so we must not send.
        verifyNoInteractions(provider);
        verify(outboxRepository, never()).finish(anyString(), anyString(), any(), any(), any(), any());
    }

    // ===================== nothing to send =====================

    @Test
    void aUserWithNoActiveDeviceIsSkippedNotFailed() {
        when(userDeviceRepository.findByUserIdAndIsActiveTrue(anyString())).thenReturn(List.of());

        worker.drainDue();

        // SKIPPED, not DEAD — otherwise count(DEAD) stops being a usable alert signal.
        verifyFinishedWith(PushOutboxStatus.SKIPPED);
        verifyNoInteractions(provider);
    }

    @Test
    void anUnconfiguredProviderSkipsRatherThanBurningAttempts() {
        providerReturns(PushSendResult.failure(TOKEN, PushOutcome.PROVIDER_UNCONFIGURED,
                FcmErrorClassifier.NO_PROVIDER, "not configured"));

        worker.drainDue();

        verifyFinishedWith(PushOutboxStatus.SKIPPED);
    }

    // ===================== retry and dead-lettering =====================

    @Test
    void transientFailureSchedulesARetryWithinTheExpectedWindow() {
        providerReturns(PushSendResult.failure(TOKEN, PushOutcome.RETRY, "UNAVAILABLE", "fcm down"));
        Instant before = Instant.now();

        worker.drainDue();

        ArgumentCaptor<Instant> next = ArgumentCaptor.forClass(Instant.class);
        verify(outboxRepository).scheduleRetry(anyString(), anyString(), any(), eq(PushOutboxStatus.PENDING),
                next.capture(), any(), any());

        // A range, not an exact instant — the backoff is jittered on purpose.
        long delayMillis = next.getValue().toEpochMilli() - before.toEpochMilli();
        assertTrue(delayMillis >= 12_000 && delayMillis <= 19_000,
                "expected ~15s ±20% jitter, got " + delayMillis + "ms");
        verify(outboxRepository, never()).finish(anyString(), anyString(), any(), any(), any(), any());
    }

    @Test
    void theLastAllowedAttemptDeadLettersInsteadOfRetrying() {
        when(outboxRepository.findDue(anyCollection(), any(), any()))
                .thenReturn(List.of(row(MAX_ATTEMPTS - 1)));   // claim makes this the Nth attempt
        providerReturns(PushSendResult.failure(TOKEN, PushOutcome.RETRY, "UNAVAILABLE", "fcm down"));

        worker.drainDue();

        verifyFinishedWith(PushOutboxStatus.DEAD);
        verify(outboxRepository, never()).scheduleRetry(anyString(), anyString(), any(), any(), any(), any(), any());
    }

    @Test
    void brokenCredentialsRetryWithoutConsumingTheAttemptBudget() {
        when(outboxRepository.findDue(anyCollection(), any(), any()))
                .thenReturn(List.of(row(MAX_ATTEMPTS + 3)));   // already well past the cap
        providerReturns(PushSendResult.failure(TOKEN, PushOutcome.PROVIDER_UNCONFIGURED,
                "THIRD_PARTY_AUTH_ERROR", "bad APNs key"));

        worker.drainDue();

        // A weekend of expired credentials must not dead-letter the whole queue.
        verify(outboxRepository).scheduleRetry(anyString(), anyString(), any(), any(), any(), any(), any());
        verify(outboxRepository, never()).finish(anyString(), anyString(), any(), any(), any(), any());
    }

    // ===================== dead tokens =====================

    @Test
    void deadTokensAreDeactivatedAndTheRowIsDeadLettered() {
        providerReturns(PushSendResult.failure(TOKEN, PushOutcome.DEAD_TOKEN, "UNREGISTERED", "gone"));

        worker.drainDue();

        verify(userDeviceRepository).deactivateTokens(eq(List.of(TOKEN)), any());
        verifyFinishedWith(PushOutboxStatus.DEAD);
    }

    @Test
    void aRejectedPayloadDeadLettersWithoutTouchingAnyDevice() {
        providerReturns(PushSendResult.failure(TOKEN, PushOutcome.INVALID_MESSAGE,
                "INVALID_ARGUMENT", "malformed payload"));

        worker.drainDue();

        // The devices are fine; our message is not. Deactivating here would be the mass-wipe bug.
        verify(userDeviceRepository, never()).deactivateTokens(anyCollection(), any());
        verifyFinishedWith(PushOutboxStatus.DEAD);
    }

    @Test
    void oneDeliveryAmongFailuresStillCountsAsSent() {
        providerReturns(
                PushSendResult.delivered("good-token", "msg-1"),
                PushSendResult.failure(TOKEN, PushOutcome.DEAD_TOKEN, "UNREGISTERED", "gone"));

        worker.drainDue();

        verifyFinishedWith(PushOutboxStatus.SENT);
        verify(userDeviceRepository).deactivateTokens(eq(List.of(TOKEN)), any());
    }

    // ===================== robustness =====================

    @Test
    void aPoisonedRowDoesNotAbortTheRestOfTheBatch() {
        PushOutboxMessage first = row(0);
        PushOutboxMessage second = PushOutboxMessage.builder()
                .id("outbox-2").notificationId("NOT-2").userId("PAT-2")
                .title("t").body("b").type(NotificationType.GENERAL_ALERT)
                .status(PushOutboxStatus.PENDING).attempts(0).nextAttemptAt(Instant.now()).build();
        when(outboxRepository.findDue(anyCollection(), any(), any())).thenReturn(List.of(first, second));
        when(provider.send(anyList(), any()))
                .thenThrow(new IllegalStateException("boom"))
                .thenReturn(new PushSendReport(List.of(PushSendResult.delivered(TOKEN, "msg-2"))));

        worker.drainDue();

        // The second row must still be attempted despite the first blowing up.
        verify(provider, times(2)).send(anyList(), any());
        verifyFinishedWith(PushOutboxStatus.SENT);
    }

    @Test
    void disablingTheWorkerStopsItTouchingAnything() {
        properties.setEnabled(false);

        worker.drainDue();

        verifyNoInteractions(outboxRepository, userDeviceRepository, provider);
    }

    @Test
    void anEmptyQueueIsAQuietNoOp() {
        when(outboxRepository.findDue(anyCollection(), any(), any())).thenReturn(List.of());

        worker.drainDue();

        verifyNoInteractions(provider);
        verify(outboxRepository, never()).claim(anyString(), anyString(), any(), anyCollection(), any(), any());
    }

    // ===================== the pure decision function =====================

    @Test
    void decideMapsEachReportShapeToTheRightTerminalState() {
        PushSendReport delivered = new PushSendReport(List.of(PushSendResult.delivered(TOKEN, "m")));
        PushSendReport retryable = new PushSendReport(List.of(
                PushSendResult.failure(TOKEN, PushOutcome.RETRY, "UNAVAILABLE", "d")));
        PushSendReport permanent = new PushSendReport(List.of(
                PushSendResult.failure(TOKEN, PushOutcome.DEAD_TOKEN, "UNREGISTERED", "d")));
        PushSendReport unconfigured = new PushSendReport(List.of(
                PushSendResult.failure(TOKEN, PushOutcome.PROVIDER_UNCONFIGURED,
                        FcmErrorClassifier.NO_PROVIDER, "d")));

        assertEquals(PushOutboxWorker.Decision.SENT, PushOutboxWorker.decide(delivered, 1, MAX_ATTEMPTS));
        assertEquals(PushOutboxWorker.Decision.RETRY, PushOutboxWorker.decide(retryable, 1, MAX_ATTEMPTS));
        assertEquals(PushOutboxWorker.Decision.DEAD, PushOutboxWorker.decide(retryable, MAX_ATTEMPTS, MAX_ATTEMPTS));
        assertEquals(PushOutboxWorker.Decision.DEAD, PushOutboxWorker.decide(permanent, 1, MAX_ATTEMPTS));
        assertEquals(PushOutboxWorker.Decision.SKIPPED, PushOutboxWorker.decide(unconfigured, 1, MAX_ATTEMPTS));
    }

    @Test
    void backoffGrowsExponentiallyAndIsCapped() {
        Instant now = Instant.now();

        long first = delayOf(PushOutboxWorker.nextAttempt(1, 15_000, 2.0, 900_000, now), now);
        long third = delayOf(PushOutboxWorker.nextAttempt(3, 15_000, 2.0, 900_000, now), now);
        long far = delayOf(PushOutboxWorker.nextAttempt(20, 15_000, 2.0, 900_000, now), now);

        assertTrue(first >= 12_000 && first <= 18_000, "attempt 1 ~15s, got " + first);
        assertTrue(third >= 48_000 && third <= 72_000, "attempt 3 ~60s, got " + third);
        assertTrue(far <= 900_000, "must never exceed the cap, got " + far);
    }

    @Test
    void backoffIsJitteredSoARecoveringOutageDoesNotStampede() {
        Instant now = Instant.now();
        long distinct = java.util.stream.IntStream.range(0, 40)
                .mapToLong(i -> PushOutboxWorker.nextAttempt(3, 15_000, 2.0, 900_000, now).toEpochMilli())
                .distinct()
                .count();

        assertTrue(distinct > 1, "identical delays would make every queued row retry in lockstep");
    }

    private long delayOf(Instant next, Instant now) {
        return next.toEpochMilli() - now.toEpochMilli();
    }
}
