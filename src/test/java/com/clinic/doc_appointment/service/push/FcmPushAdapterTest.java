package com.clinic.doc_appointment.service.push;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FcmPushAdapterTest {

    private static final PushMessage MESSAGE =
            new PushMessage("Appointment Confirmed", "See you tomorrow.", "APPOINTMENT_UPDATE", "APPOINTMENT-1");

    private FirebaseMessaging messaging;
    private FcmPushAdapter adapter;
    private FcmProperties properties;

    @BeforeEach
    void setUp() {
        messaging = mock(FirebaseMessaging.class);
        properties = new FcmProperties();
        adapter = new FcmPushAdapter(messaging, new FcmErrorClassifier(), properties);
    }

    private SendResponse success(String messageId) {
        SendResponse response = mock(SendResponse.class);
        when(response.isSuccessful()).thenReturn(true);
        when(response.getMessageId()).thenReturn(messageId);
        return response;
    }

    private SendResponse failure(MessagingErrorCode code) {
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(code);
        when(exception.getMessage()).thenReturn("simulated " + code);

        SendResponse response = mock(SendResponse.class);
        when(response.isSuccessful()).thenReturn(false);
        when(response.getException()).thenReturn(exception);
        return response;
    }

    private BatchResponse batchOf(SendResponse... responses) {
        BatchResponse batch = mock(BatchResponse.class);
        when(batch.getResponses()).thenReturn(Arrays.asList(responses));
        return batch;
    }

    /**
     * Stubs the send with an already-built response.
     *
     * <p>The batch must be constructed <em>before</em> the outer {@code when(...)} begins: these
     * helpers stub mocks themselves, and Mockito rejects nested stubbing that starts while an
     * outer one is still open.
     */
    private void respondWith(BatchResponse batch) throws Exception {
        when(messaging.sendEachForMulticast(any())).thenReturn(batch);
    }

    // ===================== happy path and mapping =====================

    @Test
    void reportsDeliveryPerToken() throws Exception {
        BatchResponse batch = batchOf(success("m-1"), success("m-2"));
        respondWith(batch);

        PushSendReport report = adapter.send(List.of("token-a", "token-b"), MESSAGE);

        assertEquals(2, report.deliveredCount());
        assertTrue(report.deadTokens().isEmpty());
    }

    @Test
    void mapsEachFailureBackToItsOwnToken() throws Exception {
        // Order is the only link between a response and a device — if this mapping slips, the
        // wrong user's device gets deactivated.
        BatchResponse batch = batchOf(success("m-1"), failure(MessagingErrorCode.UNREGISTERED), success("m-3"));
        respondWith(batch);

        PushSendReport report = adapter.send(List.of("token-a", "token-dead", "token-c"), MESSAGE);

        assertEquals(List.of("token-dead"), report.deadTokens());
        assertEquals(2, report.deliveredCount());
    }

    @Test
    void aResponseCountMismatchRefusesToMapAndDeactivatesNothing() throws Exception {
        BatchResponse batch = batchOf(success("m-1"));
        respondWith(batch);   // 1 for 2 tokens

        PushSendReport report = adapter.send(List.of("token-a", "token-b"), MESSAGE);

        assertTrue(report.deadTokens().isEmpty(), "an untrustworthy mapping must never kill a device");
        assertTrue(report.anyRetryable());
        assertEquals(0, report.deliveredCount());
    }

    // ===================== the blank-token trap =====================

    @Test
    void blankTokensAreFilteredOutBeforeTheBatchIsBuilt() throws Exception {
        BatchResponse batch = batchOf(success("m-1"));
        respondWith(batch);

        PushSendReport report = adapter.send(Arrays.asList("token-a", "", null, "   "), MESSAGE);

        // MulticastMessage rejects the ENTIRE batch if any token is blank, so one junk row in
        // user_devices would otherwise block every other device's notification.
        ArgumentCaptor<MulticastMessage> sent = ArgumentCaptor.forClass(MulticastMessage.class);
        verify(messaging).sendEachForMulticast(sent.capture());
        assertEquals(1, report.deliveredCount());
        assertEquals(3, report.results().stream()
                .filter(r -> r.outcome() == PushOutcome.SKIPPED).count());
    }

    @Test
    void allBlankTokensMeansNothingIsSent() {
        PushSendReport report = adapter.send(Arrays.asList("", null), MESSAGE);

        verifyNoInteractions(messaging);
        assertEquals(0, report.deliveredCount());
    }

    @Test
    void duplicateTokensAreCollapsed() throws Exception {
        BatchResponse batch = batchOf(success("m-1"));
        respondWith(batch);

        PushSendReport report = adapter.send(List.of("token-a", "token-a"), MESSAGE);

        assertEquals(1, report.results().size(), "one result per distinct token");
    }

    @Test
    void emptyInputShortCircuits() {
        assertTrue(adapter.send(List.of(), MESSAGE).isEmpty());
        assertTrue(adapter.send(null, MESSAGE).isEmpty());
        verifyNoInteractions(messaging);
    }

    // ===================== chunking =====================

    /**
     * {@code MulticastMessage.getMessageList()} is package-private, so the chunk size cannot be
     * read off the argument. Feeding the mock an explicit queue of expected sizes is stricter
     * anyway: if the adapter chunks differently the response count stops matching and the
     * size-mismatch guard trips, failing the test.
     */
    private void expectChunks(int... sizes) throws Exception {
        Queue<Integer> expected = new LinkedList<>();
        Arrays.stream(sizes).forEach(expected::add);
        when(messaging.sendEachForMulticast(any())).thenAnswer(invocation -> {
            Integer size = expected.poll();
            assertNotNull(size, "adapter issued more chunks than expected");
            List<SendResponse> responses = new ArrayList<>();
            IntStream.range(0, size).forEach(i -> responses.add(success("m-" + i)));
            BatchResponse batch = mock(BatchResponse.class);
            when(batch.getResponses()).thenReturn(responses);
            return batch;
        });
    }

    @Test
    void splitsAtTheFiveHundredTokenCap() throws Exception {
        List<String> tokens = IntStream.range(0, 750).mapToObj(i -> "token-" + i).toList();
        expectChunks(500, 250);

        PushSendReport report = adapter.send(tokens, MESSAGE);

        verify(messaging, times(2)).sendEachForMulticast(any());
        assertEquals(750, report.deliveredCount());
    }

    @Test
    void anOversizedConfiguredBatchIsClampedToTheProviderLimit() throws Exception {
        properties.setBatchSize(10_000);
        List<String> tokens = IntStream.range(0, 501).mapToObj(i -> "token-" + i).toList();
        expectChunks(500, 1);

        PushSendReport report = adapter.send(tokens, MESSAGE);

        // Misconfiguration must not reach the SDK as an IllegalArgumentException.
        verify(messaging, times(2)).sendEachForMulticast(any());
        assertEquals(501, report.deliveredCount());
    }

    // ===================== failure handling =====================

    @Test
    void aBatchLevelFailureRetriesEveryTokenAndKillsNone() throws Exception {
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(exception.getMessage()).thenReturn("batch blew up");
        when(messaging.sendEachForMulticast(any())).thenThrow(exception);

        PushSendReport report = adapter.send(List.of("token-a", "token-b"), MESSAGE);

        assertTrue(report.anyRetryable());
        assertTrue(report.deadTokens().isEmpty(),
                "a batch-level error describes the HTTP call, not any device");
    }

    @Test
    void neverThrowsWhenTheSdkDoes() throws Exception {
        when(messaging.sendEachForMulticast(any())).thenThrow(new IllegalStateException("boom"));

        // The port's contract is that failures are data, never exceptions — the worker relies on
        // getting a report back so it can record an outcome instead of stranding the row.
        PushSendReport report = adapter.send(List.of("token-a"), MESSAGE);

        assertTrue(report.anyRetryable());
    }

    // ===================== no credentials =====================

    @Test
    void withoutCredentialsItReportsUnavailableRatherThanFailing() {
        FcmPushAdapter unconfigured = new FcmPushAdapter(null, new FcmErrorClassifier(), properties);

        PushSendReport report = unconfigured.send(List.of("token-a", "token-b"), MESSAGE);

        // The worker turns this into SKIPPED, which is what stops a dev machine with no Firebase
        // credentials from filling the outbox with DEAD rows.
        assertTrue(report.providerUnavailable());
        assertFalse(report.deadTokens().size() > 0);
    }
}
