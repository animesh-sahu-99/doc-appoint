package com.clinic.doc_appointment.service.push;

import java.util.List;

/**
 * Port for delivering a push notification to a set of device tokens.
 *
 * <p><strong>Contract — binding on every implementation, not just the Firebase one.</strong>
 * {@link PushOutboxWorker} relies on all of it:
 * <ul>
 *   <li><strong>Never throws.</strong> Every failure mode is expressed as a
 *       {@link PushSendResult}. A provider that throws breaks the worker's ability to record an
 *       outcome, which strands the row.</li>
 *   <li><strong>Must be callable with no transaction open.</strong> This performs blocking
 *       network I/O; holding a database connection across it is what the outbox exists to
 *       prevent.</li>
 *   <li><strong>Performs no database writes.</strong> Dead tokens are <em>reported</em>, not
 *       deactivated — the worker owns that write so it commits alongside the row's outcome.</li>
 *   <li>Returns exactly one result per distinct non-null token. Duplicates are collapsed and
 *       ordering is not guaranteed to match the input.</li>
 *   <li>A null or empty token list yields {@link PushSendReport#empty()}.</li>
 * </ul>
 *
 * <p>No provider-specific type may appear in this signature or in {@link PushSendResult} — that
 * is what lets a second provider (APNs, web push) be added without touching the worker.
 */
public interface PushNotificationProvider {

    PushSendReport send(List<String> tokens, PushMessage message);
}
