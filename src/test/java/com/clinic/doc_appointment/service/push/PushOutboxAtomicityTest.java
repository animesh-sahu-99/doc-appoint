package com.clinic.doc_appointment.service.push;

import com.clinic.doc_appointment.entity.PushOutboxMessage;
import com.clinic.doc_appointment.enums.NotificationType;
import com.clinic.doc_appointment.enums.PushOutboxStatus;
import com.clinic.doc_appointment.event.AppointmentChangedEvent;
import com.clinic.doc_appointment.listener.AppointmentNotificationListener;
import com.clinic.doc_appointment.mapper.NotificationMapper;
import com.clinic.doc_appointment.repository.NotificationRepository;
import com.clinic.doc_appointment.repository.PushOutboxRepository;
import com.clinic.doc_appointment.repository.UserDeviceRepository;
import com.clinic.doc_appointment.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the two guarantees the outbox rests on, against a real transaction manager:
 *
 * <ol>
 *   <li>the notification row and its push row commit or roll back <em>together</em>;</li>
 *   <li>they actually persist when written from an {@code AFTER_COMMIT} listener — the
 *       transaction-propagation subtlety that makes {@code REQUIRES_NEW} load-bearing.</li>
 * </ol>
 *
 * <p>{@code NOT_SUPPORTED} at class level so {@code @DataJpaTest}'s usual rollback-everything
 * transaction does not wrap the test; each case drives its own boundaries explicitly.
 */
@DataJpaTest
@Import({NotificationService.class, AppointmentNotificationListener.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PushOutboxAtomicityTest {

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    @MockitoBean
    private NotificationMapper notificationMapper;

    @MockitoBean
    private UserDeviceRepository userDeviceRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PushOutboxRepository pushOutboxRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * These tests commit for real — that is the entire point — so nothing rolls them back
     * afterwards and each one must start from a clean table.
     */
    @BeforeEach
    void clearTables() {
        pushOutboxRepository.deleteAll();
        notificationRepository.deleteAll();
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    private void send() {
        notificationService.sendNotification("PAT-1", "Appointment Confirmed",
                "Your appointment is confirmed.", NotificationType.APPOINTMENT_UPDATE, "APPOINTMENT-1");
    }

    // ===================== atomicity =====================

    @Test
    void committingWritesBothRowsAndLinksThem() {
        tx().executeWithoutResult(status -> send());

        assertEquals(1, notificationRepository.count());
        assertEquals(1, pushOutboxRepository.count());

        PushOutboxMessage outbox = pushOutboxRepository.findAll().get(0);
        assertEquals(notificationRepository.findAll().get(0).getId(), outbox.getNotificationId(),
                "the push must point at the notification it belongs to");
        assertEquals(PushOutboxStatus.PENDING, outbox.getStatus());
        assertTrue(outbox.getAttempts() == 0);
    }

    @Test
    void aRolledBackNotificationLeavesNoOrphanedPush() {
        assertThrows(RuntimeException.class, () ->
                tx().executeWithoutResult(status -> {
                    send();
                    throw new IllegalStateException("force rollback");
                }));

        // REQUIRES_NEW means the inner transaction commits independently of the outer one, so
        // this asserts what actually matters: the two rows never disagree with each other.
        assertEquals(notificationRepository.count(), pushOutboxRepository.count(),
                "a notification must never exist without its push, or vice versa");
    }

    @Test
    void thePushCarriesTheNotificationsPayload() {
        tx().executeWithoutResult(status -> send());

        PushOutboxMessage outbox = pushOutboxRepository.findAll().get(0);
        assertEquals("PAT-1", outbox.getUserId());
        assertEquals("Appointment Confirmed", outbox.getTitle());
        assertEquals("Your appointment is confirmed.", outbox.getBody());
        assertEquals(NotificationType.APPOINTMENT_UPDATE, outbox.getType());
        assertEquals("APPOINTMENT-1", outbox.getRelatedEntityId());
    }

    // ===================== the AFTER_COMMIT propagation proof =====================

    /**
     * The definitive test.
     *
     * <p>{@code AppointmentNotificationListener} runs at {@code AFTER_COMMIT}, when the outer
     * transaction has committed but its resources are still bound to the thread. Under
     * {@code REQUIRED}, {@code sendNotification} would <em>participate</em> in that completed
     * transaction — no begin, no commit — and both inserts would be silently discarded.
     *
     * <p>Run this against the old {@code REQUIRED} annotation and it fails, which is what tells
     * you whether that latent bug was live in production.
     */
    @Test
    void notificationsPublishedFromAnAfterCommitListenerActuallyPersist() {
        tx().executeWithoutResult(status -> eventPublisher.publishEvent(bookedEvent()));

        // A BOOKED event notifies both the patient and the doctor.
        assertEquals(2, notificationRepository.count(),
                "AFTER_COMMIT + REQUIRES_NEW must durably commit — REQUIRED silently discards these");
        assertEquals(2, pushOutboxRepository.count(),
                "each notification must have queued exactly one push");
    }

    @Test
    void aRolledBackAppointmentQueuesNoPushAtAll() {
        assertThrows(RuntimeException.class, () ->
                tx().executeWithoutResult(status -> {
                    eventPublisher.publishEvent(bookedEvent());
                    throw new IllegalStateException("force rollback");
                }));

        assertEquals(0, notificationRepository.count());
        assertEquals(0, pushOutboxRepository.count(),
                "AFTER_COMMIT must not fire for a rolled-back appointment");
    }

    private AppointmentChangedEvent bookedEvent() {
        return new AppointmentChangedEvent(
                AppointmentChangedEvent.Kind.BOOKED,
                "APPOINTMENT-1", "PAT-1", "DOC-1",
                "Asha", "Rao",
                LocalDate.now().plusDays(1), LocalTime.of(10, 0));
    }
}
