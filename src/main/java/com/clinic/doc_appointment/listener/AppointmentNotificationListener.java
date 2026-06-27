package com.clinic.doc_appointment.listener;

import com.clinic.doc_appointment.enums.NotificationType;
import com.clinic.doc_appointment.event.AppointmentChangedEvent;
import com.clinic.doc_appointment.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Observer that turns appointment lifecycle events into notifications, decoupling
 * {@code AppointmentService} from notification delivery.
 *
 * <p>Reproduces the exact recipients/types/messages of the previous inline calls — note that
 * COMPLETED uses {@link NotificationType#GENERAL_ALERT}, and a no-show intentionally sends nothing
 * (so there is no handler for it). Runs {@code AFTER_COMMIT} of the appointment transaction, so
 * notifications are never sent for a booking attempt that rolls back and retries, and a notification
 * failure no longer rolls back the appointment itself.
 */
@Component
@RequiredArgsConstructor
public class AppointmentNotificationListener {

    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentChanged(AppointmentChangedEvent e) {
        switch (e.kind()) {
            case BOOKED -> {
                notificationService.sendNotification(
                        e.patientId(),
                        "Appointment Requested",
                        "Your appointment request for " + e.slotDate() + " at " + e.startTime() + " is pending confirmation.",
                        NotificationType.APPOINTMENT_UPDATE,
                        e.appointmentId());
                notificationService.sendNotification(
                        e.doctorId(),
                        "New Appointment Request",
                        e.patientFirstName() + " has requested an appointment for " + e.slotDate() + " at " + e.startTime() + ".",
                        NotificationType.APPOINTMENT_UPDATE,
                        e.appointmentId());
            }
            case CONFIRMED -> notificationService.sendNotification(
                    e.patientId(),
                    "Appointment Confirmed",
                    "Your appointment for " + e.slotDate() + " has been confirmed by the doctor.",
                    NotificationType.APPOINTMENT_UPDATE,
                    e.appointmentId());
            case CANCELLED -> {
                notificationService.sendNotification(
                        e.patientId(),
                        "Appointment Cancelled",
                        "Your appointment for " + e.slotDate() + " has been cancelled.",
                        NotificationType.APPOINTMENT_UPDATE,
                        e.appointmentId());
                notificationService.sendNotification(
                        e.doctorId(),
                        "Appointment Cancelled",
                        "The appointment for " + e.patientFirstName() + " on " + e.slotDate() + " has been cancelled.",
                        NotificationType.APPOINTMENT_UPDATE,
                        e.appointmentId());
            }
            case COMPLETED -> notificationService.sendNotification(
                    e.patientId(),
                    "Appointment Completed",
                    "Thank you for visiting! Hope your consultation went well.",
                    NotificationType.GENERAL_ALERT,
                    e.appointmentId());
            case NOTES_UPDATED -> notificationService.sendNotification(
                    e.patientId(),
                    "Clinical Notes Updated",
                    "Dr. " + e.doctorLastName() + " has added notes/prescriptions to your recent consultation.",
                    NotificationType.APPOINTMENT_UPDATE,
                    e.appointmentId());
        }
    }
}
