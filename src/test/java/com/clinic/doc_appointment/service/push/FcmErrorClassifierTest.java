package com.clinic.doc_appointment.service.push;

import com.google.firebase.ErrorCode;
import com.google.firebase.IncomingHttpResponse;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exhaustive because this is the one place where a wrong answer destroys data rather than wasting
 * a request: misclassifying a live device as dead silently ends a real patient's notifications.
 */
class FcmErrorClassifierTest {

    private static final String TOKEN = "device-token-aaaaaaaaaaaaaaaaaaaa";
    private static final int MULTI = 5;

    private FcmErrorClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new FcmErrorClassifier();
    }

    private FirebaseMessagingException messagingError(MessagingErrorCode code) {
        FirebaseMessagingException e = mock(FirebaseMessagingException.class);
        when(e.getMessagingErrorCode()).thenReturn(code);
        when(e.getMessage()).thenReturn("simulated " + code);
        return e;
    }

    private FirebaseMessagingException transportError(Integer httpStatus, ErrorCode errorCode) {
        FirebaseMessagingException e = mock(FirebaseMessagingException.class);
        when(e.getMessagingErrorCode()).thenReturn(null);
        when(e.getErrorCode()).thenReturn(errorCode);
        when(e.getMessage()).thenReturn("transport failure");
        if (httpStatus != null) {
            IncomingHttpResponse response = mock(IncomingHttpResponse.class);
            when(response.getStatusCode()).thenReturn(httpStatus);
            when(e.getHttpResponse()).thenReturn(response);
        } else {
            when(e.getHttpResponse()).thenReturn(null);
        }
        return e;
    }

    private PushOutcome classify(FirebaseMessagingException e) {
        return classifier.classifyPerToken(TOKEN, e, MULTI).outcome();
    }

    // ===================== the 7 messaging error codes =====================

    @Test
    void unregisteredTokenIsDead() {
        assertEquals(PushOutcome.DEAD_TOKEN, classify(messagingError(MessagingErrorCode.UNREGISTERED)));
    }

    @Test
    void senderIdMismatchIsDead() {
        assertEquals(PushOutcome.DEAD_TOKEN, classify(messagingError(MessagingErrorCode.SENDER_ID_MISMATCH)));
    }

    @Test
    void transientFcmFailuresRetry() {
        assertEquals(PushOutcome.RETRY, classify(messagingError(MessagingErrorCode.UNAVAILABLE)));
        assertEquals(PushOutcome.RETRY, classify(messagingError(MessagingErrorCode.INTERNAL)));
        assertEquals(PushOutcome.RETRY, classify(messagingError(MessagingErrorCode.QUOTA_EXCEEDED)));
    }

    @Test
    void thirdPartyAuthErrorIsAConfigurationProblem() {
        assertEquals(PushOutcome.PROVIDER_UNCONFIGURED,
                classify(messagingError(MessagingErrorCode.THIRD_PARTY_AUTH_ERROR)));
    }

    /** Guards the "unrecognised means retry, never dead" principle across the whole enum. */
    @ParameterizedTest
    @EnumSource(MessagingErrorCode.class)
    void onlyTokenSpecificCodesAreEverAllowedToKillADevice(MessagingErrorCode code) {
        PushOutcome outcome = classify(messagingError(code));

        boolean tokenSpecific = code == MessagingErrorCode.UNREGISTERED
                || code == MessagingErrorCode.SENDER_ID_MISMATCH
                || code == MessagingErrorCode.INVALID_ARGUMENT;

        if (!tokenSpecific) {
            assertNotEquals(PushOutcome.DEAD_TOKEN, outcome,
                    code + " must never deactivate a device");
        }
    }

    @Test
    void recordsTheProviderCodeForStorage() {
        PushSendResult result = classifier.classifyPerToken(
                TOKEN, messagingError(MessagingErrorCode.UNAVAILABLE), MULTI);

        assertEquals("UNAVAILABLE", result.errorCode());
        assertTrue(result.errorDetail().contains("UNAVAILABLE"));
    }

    // ===================== the INVALID_ARGUMENT ambiguity =====================

    @Test
    void everyTokenFailingWithInvalidArgumentMeansThePayloadIsBad() {
        List<PushSendResult> chunk = List.of(
                classifier.classifyPerToken("a", messagingError(MessagingErrorCode.INVALID_ARGUMENT), 3),
                classifier.classifyPerToken("b", messagingError(MessagingErrorCode.INVALID_ARGUMENT), 3),
                classifier.classifyPerToken("c", messagingError(MessagingErrorCode.INVALID_ARGUMENT), 3));

        List<PushSendResult> reconciled = classifier.reconcileInvalidArgument(chunk);

        // The critical assertion: a payload bug must NOT deactivate the entire device table.
        assertTrue(reconciled.stream().allMatch(r -> r.outcome() == PushOutcome.INVALID_MESSAGE),
                "a uniformly rejected payload must not be read as N dead devices");
    }

    @Test
    void aMixedChunkProvesThePayloadWorksSoTheTokenIsBad() {
        List<PushSendResult> chunk = List.of(
                PushSendResult.delivered("a", "msg-1"),
                classifier.classifyPerToken("b", messagingError(MessagingErrorCode.INVALID_ARGUMENT), 2));

        List<PushSendResult> reconciled = classifier.reconcileInvalidArgument(chunk);

        assertEquals(PushOutcome.DEAD_TOKEN, reconciled.get(1).outcome());
    }

    @Test
    void singleTokenChunkFallsBackToTreatingItAsADeadToken() {
        PushSendResult single =
                classifier.classifyPerToken(TOKEN, messagingError(MessagingErrorCode.INVALID_ARGUMENT), 1);

        assertEquals(PushOutcome.DEAD_TOKEN, single.outcome());
        // Reconciliation cannot help with one sample; it must leave the result untouched.
        assertEquals(PushOutcome.DEAD_TOKEN,
                classifier.reconcileInvalidArgument(List.of(single)).get(0).outcome());
    }

    // ===================== null messaging code: HTTP fallback =====================

    @ParameterizedTest
    @CsvSource({
            "401, PROVIDER_UNCONFIGURED",
            "403, PROVIDER_UNCONFIGURED",
            "404, PROVIDER_UNCONFIGURED",
            "400, INVALID_MESSAGE",
            "429, RETRY",
            "500, RETRY",
            "503, RETRY",
            "418, RETRY"
    })
    void fallsBackToHttpStatusWhenThereIsNoMessagingCode(int status, PushOutcome expected) {
        assertEquals(expected, classify(transportError(status, null)));
    }

    @Test
    void http404IsAboutTheProjectNotTheDevice() {
        // Easy mistake: 404 looks like "device not found" but refers to the project resource.
        assertNotEquals(PushOutcome.DEAD_TOKEN, classify(transportError(404, null)));
    }

    // ===================== null messaging code and no HTTP exchange =====================

    @ParameterizedTest
    @CsvSource({
            "UNAUTHENTICATED, PROVIDER_UNCONFIGURED",
            "PERMISSION_DENIED, PROVIDER_UNCONFIGURED",
            "INVALID_ARGUMENT, INVALID_MESSAGE",
            "OUT_OF_RANGE, INVALID_MESSAGE",
            "CANCELLED, RETRY",
            "UNAVAILABLE, RETRY",
            "DEADLINE_EXCEEDED, RETRY",
            "NOT_FOUND, RETRY",
            "UNKNOWN, RETRY"
    })
    void fallsBackToTheGenericErrorCodeWhenNoHttpExchangeHappened(ErrorCode code, PushOutcome expected) {
        assertEquals(expected, classify(transportError(null, code)));
    }

    /** Every remaining ErrorCode must be safe, not merely the ones enumerated above. */
    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void noGenericErrorCodeEverKillsADevice(ErrorCode code) {
        assertNotEquals(PushOutcome.DEAD_TOKEN, classify(transportError(null, code)));
    }

    @Test
    void handlesACompletelyBareException() {
        assertEquals(PushOutcome.RETRY, classify(transportError(null, null)));
    }

    @Test
    void handlesAMissingExceptionAltogether() {
        assertEquals(PushOutcome.RETRY, classifier.classifyPerToken(TOKEN, null, MULTI).outcome());
    }

    // ===================== batch-level downgrade =====================

    @Test
    void batchLevelFailuresNeverDeactivateTokens() {
        // A batch-level exception describes the HTTP call, not any device — without the downgrade
        // rule a single transport-level 400 would kill up to 500 devices at once.
        assertEquals(PushOutcome.RETRY,
                classifier.classifyBatchLevel(messagingError(MessagingErrorCode.UNREGISTERED)));
        assertEquals(PushOutcome.RETRY, classifier.classifyBatchLevel(transportError(400, null)));
    }

    @Test
    void batchLevelStillSurfacesConfigurationProblems() {
        assertEquals(PushOutcome.PROVIDER_UNCONFIGURED, classifier.classifyBatchLevel(transportError(401, null)));
    }

    @Test
    void codeOfPrefersMessagingCodeThenHttpThenGeneric() {
        assertEquals("UNREGISTERED", classifier.codeOf(messagingError(MessagingErrorCode.UNREGISTERED)));
        assertEquals("HTTP_503", classifier.codeOf(transportError(503, null)));
        assertEquals("CANCELLED", classifier.codeOf(transportError(null, ErrorCode.CANCELLED)));
        assertEquals("UNKNOWN", classifier.codeOf(null));
    }
}
