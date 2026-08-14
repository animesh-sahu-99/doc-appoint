package com.clinic.doc_appointment.service.push;

import com.google.firebase.ErrorCode;
import com.google.firebase.IncomingHttpResponse;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Decides what an FCM failure means. Pure — no I/O, no state, no Spring dependencies — because
 * this is the riskiest logic in the push path and that makes it exhaustively unit-testable.
 *
 * <p>Previously this lived as {@code if} statements inside a catch block, recognised exactly one
 * error code, and silently retried everything else forever.
 *
 * <p><strong>Governing principle: an unrecognised signal always means {@link PushOutcome#RETRY},
 * never {@link PushOutcome#DEAD_TOKEN}.</strong> Retrying wrongly costs one wasted HTTPS call.
 * Deactivating wrongly silently ends a real patient's notifications forever — no error anywhere,
 * and no way for them to recover short of reinstalling the app. The asymmetry is enormous, so
 * every default here leans toward retry.
 */
@Component
@Slf4j
public class FcmErrorClassifier {

    /** Marks "this instance has no push provider configured at all". */
    public static final String NO_PROVIDER = "NO_PROVIDER";

    static final String UNKNOWN_CODE = "UNKNOWN";
    static final String BLANK_TOKEN = "BLANK_TOKEN";
    static final String RESPONSE_SIZE_MISMATCH = "RESPONSE_SIZE_MISMATCH";
    static final String SDK_PRECONDITION = "SDK_PRECONDITION";
    static final String UNEXPECTED = "UNEXPECTED";

    /**
     * Classifies a failure reported for a single token inside a batch response.
     *
     * @param chunkSize how many tokens shared this exact payload — used to disambiguate
     *                  {@link MessagingErrorCode#INVALID_ARGUMENT}, see
     *                  {@link #reconcileInvalidArgument}
     */
    public PushSendResult classifyPerToken(String token, FirebaseMessagingException exception, int chunkSize) {
        if (exception == null) {
            return PushSendResult.failure(token, PushOutcome.RETRY, UNKNOWN_CODE,
                    "failed with no exception attached");
        }

        MessagingErrorCode code = exception.getMessagingErrorCode();
        String detail = safeDetail(exception);

        if (code == null) {
            return PushSendResult.failure(token, fromTransport(exception), codeOf(exception), detail);
        }

        PushOutcome outcome = switch (code) {
            case UNREGISTERED, SENDER_ID_MISMATCH -> PushOutcome.DEAD_TOKEN;
            case THIRD_PARTY_AUTH_ERROR -> PushOutcome.PROVIDER_UNCONFIGURED;
            case QUOTA_EXCEEDED, UNAVAILABLE, INTERNAL -> PushOutcome.RETRY;
            // Ambiguous: FCM returns this for a bad token AND a bad payload. Provisionally treat
            // it as a dead token; reconcileInvalidArgument revisits it with chunk-wide context.
            case INVALID_ARGUMENT -> PushOutcome.DEAD_TOKEN;
        };

        if (code == MessagingErrorCode.INVALID_ARGUMENT && chunkSize == 1) {
            // A single-token send gives no context to disambiguate with. Google's guidance is to
            // treat it as a bad token, but log loudly: if a payload bug is the real cause it will
            // also hit multi-token chunks, where reconcileInvalidArgument catches it properly.
            log.warn("FCM INVALID_ARGUMENT on a single-token send — assuming a dead token, but this "
                    + "is also what a malformed payload looks like. Detail: {}", detail);
        }

        return PushSendResult.failure(token, outcome, code.name(), detail);
    }

    /**
     * Second pass over one chunk's results, resolving the {@link MessagingErrorCode#INVALID_ARGUMENT}
     * ambiguity using the fact that every token in a chunk shared an identical payload.
     *
     * <p><strong>This is the most consequential rule in the class.</strong> Mapping
     * {@code INVALID_ARGUMENT} straight to a dead token — the obvious reading — means that the day
     * someone ships a payload bug (a null title, an oversized data value), the very first drain
     * deactivates every device in the table. An error handler must not be capable of that.
     *
     * <ul>
     *   <li>Every response in a multi-token chunk failed with INVALID_ARGUMENT ⇒ the constant is
     *       the payload, not the tokens. Reclassify as {@link PushOutcome#INVALID_MESSAGE} and
     *       deactivate nothing.</li>
     *   <li>Mixed results ⇒ the payload demonstrably works for someone, so the failing token is
     *       genuinely bad. Leave it as {@link PushOutcome#DEAD_TOKEN}.</li>
     * </ul>
     *
     * @return the reconciled results
     */
    public List<PushSendResult> reconcileInvalidArgument(List<PushSendResult> chunkResults) {
        if (chunkResults == null || chunkResults.size() <= 1) {
            return chunkResults;
        }

        boolean allInvalidArgument = chunkResults.stream()
                .allMatch(r -> MessagingErrorCode.INVALID_ARGUMENT.name().equals(r.errorCode()));

        if (!allInvalidArgument) {
            return chunkResults;
        }

        log.error("Every one of {} tokens rejected with INVALID_ARGUMENT — this is a malformed "
                + "PAYLOAD, not {} dead devices. No token will be deactivated. Detail: {}",
                chunkResults.size(), chunkResults.size(),
                chunkResults.get(0).errorDetail());

        return chunkResults.stream()
                .map(r -> r.withOutcome(PushOutcome.INVALID_MESSAGE))
                .toList();
    }

    /**
     * Classifies a failure of the batch call itself.
     *
     * <p>Such an exception describes the HTTP exchange, not any individual device, so there is by
     * definition no per-token evidence. Any classification that would destroy a token is therefore
     * <strong>downgraded to {@link PushOutcome#RETRY}</strong> — without this rule a single
     * transport-level 400 would deactivate up to 500 devices at once.
     */
    public PushOutcome classifyBatchLevel(FirebaseMessagingException exception) {
        PushOutcome outcome = classifyPerToken("", exception, Integer.MAX_VALUE).outcome();
        return switch (outcome) {
            case DEAD_TOKEN, INVALID_MESSAGE -> PushOutcome.RETRY;
            default -> outcome;
        };
    }

    /**
     * Fallback when {@code getMessagingErrorCode()} is null — a normal, reachable path: the SDK
     * wraps whole-batch failures as {@code ErrorCode.CANCELLED} with no messaging code, and pure
     * transport failures never produce an FCM error body at all.
     */
    private PushOutcome fromTransport(FirebaseMessagingException exception) {
        IncomingHttpResponse response = exception.getHttpResponse();

        // Null when no HTTP exchange happened at all: connection refused, DNS failure,
        // TLS handshake failure, interrupted thread.
        if (response != null) {
            int status = response.getStatusCode();
            if (status == 401 || status == 403 || status == 404) {
                // 404 here is about the PROJECT resource in the request path, not the device —
                // deliberately not a dead token.
                return PushOutcome.PROVIDER_UNCONFIGURED;
            }
            if (status == 400) {
                return PushOutcome.INVALID_MESSAGE;
            }
            if (status == 429 || status >= 500) {
                return PushOutcome.RETRY;
            }
            if (status >= 400) {
                log.warn("Unrecognised HTTP {} from FCM — retrying rather than assuming a dead token", status);
                return PushOutcome.RETRY;
            }
        }

        return fromErrorCode(exception.getErrorCode());
    }

    /** Last resort: the transport-agnostic Firebase error code. Defaults to retry. */
    private PushOutcome fromErrorCode(ErrorCode errorCode) {
        if (errorCode == null) {
            return PushOutcome.RETRY;
        }
        return switch (errorCode) {
            case UNAUTHENTICATED, PERMISSION_DENIED -> PushOutcome.PROVIDER_UNCONFIGURED;
            case INVALID_ARGUMENT, OUT_OF_RANGE -> PushOutcome.INVALID_MESSAGE;
            // NOT_FOUND is deliberately NOT a dead token: only MessagingErrorCode.UNREGISTERED is
            // authoritative evidence that a device is gone.
            default -> PushOutcome.RETRY;
        };
    }

    /** A stable code for storage, preferring the messaging code and falling back sensibly. */
    public String codeOf(FirebaseMessagingException exception) {
        if (exception == null) {
            return UNKNOWN_CODE;
        }
        if (exception.getMessagingErrorCode() != null) {
            return exception.getMessagingErrorCode().name();
        }
        IncomingHttpResponse response = exception.getHttpResponse();
        if (response != null) {
            return "HTTP_" + response.getStatusCode();
        }
        return exception.getErrorCode() != null ? exception.getErrorCode().name() : UNKNOWN_CODE;
    }

    /** Exception message only — never the payload or anything token-derived. */
    private String safeDetail(FirebaseMessagingException exception) {
        String message = exception.getMessage();
        return message == null ? exception.getClass().getSimpleName() : message;
    }
}
