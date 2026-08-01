package com.clinic.doc_appointment.service;

import com.clinic.doc_appointment.dto.request.BookAppointmentRequest;
import com.clinic.doc_appointment.dto.response.AppointmentResponse;
import com.clinic.doc_appointment.domain.state.AppointmentTransitionValidator;
import com.clinic.doc_appointment.entity.Appointment;
import com.clinic.doc_appointment.entity.DoctorAvailability;
import com.clinic.doc_appointment.entity.Patient;
import com.clinic.doc_appointment.enums.AppointmentStatus;
import com.clinic.doc_appointment.event.AppointmentChangedEvent;
import com.clinic.doc_appointment.exception.BookingConflictException;
import com.clinic.doc_appointment.exception.SlotAlreadyBookedException;
import com.clinic.doc_appointment.mapper.AppointmentMapper;
import com.clinic.doc_appointment.repository.AppointmentRepository;
import com.clinic.doc_appointment.repository.DoctorAvailabilityRepository;
import com.clinic.doc_appointment.repository.PatientRepository;
import com.clinic.doc_appointment.security.AppointmentAccessGuard;
import com.clinic.doc_appointment.security.UserPrincipal;
import com.clinic.doc_appointment.exception.ForbiddenOperationException;
import com.clinic.doc_appointment.enums.Role;
import com.clinic.doc_appointment.util.EntityFinder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();  // ✅ Thread-safe, no duplicate seeds

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final DoctorAvailabilityRepository slotRepository;
    private final AppointmentMapper appointmentMapper;
    private final AppointmentTransitionValidator transitionValidator;
    private final ApplicationEventPublisher eventPublisher;
    private final AppointmentAccessGuard accessGuard;

    /**
     * Book appointment with Optimistic Locking + Retry
     */
    @Transactional
    @Retryable(
            retryFor = {OptimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2, maxDelay = 1000)
    )
    public AppointmentResponse bookAppointment(BookAppointmentRequest request, UserPrincipal caller) {
        log.info("Booking appointment - Patient: {}, Slot: {}",
                request.getPatientId(), request.getSlotId());

        // 0. A patient may only book for themselves
        boolean isPatient = Role.PATIENT.authority().equals(caller.getRole());
        if (!isPatient || !caller.getId().equals(request.getPatientId())) {
            throw new ForbiddenOperationException("You may only book appointments for your own account.");
        }

        // 1. Validate Patient
        Patient patient = EntityFinder.findOrThrow(patientRepository, request.getPatientId(),
                "Patient not found with ID: " + request.getPatientId());

        // 2. Get Slot
        DoctorAvailability slot = EntityFinder.findOrThrow(slotRepository, request.getSlotId(),
                "Slot not found with ID: " + request.getSlotId());

        // 3. Check Slot Availability
        if (!slot.getIsAvailable()) {
            throw new SlotAlreadyBookedException("This slot is already booked");
        }

        // 4. Generate Appointment Number
        String appointmentNumber = generateAppointmentNumber();

        // 5. Create Appointment
        Appointment appointment = new Appointment()
                .setAppointmentNumber(appointmentNumber)
                .setPatient(patient)
                .setDoctor(slot.getDoctor())
                .setSlot(slot)
                .setStatus(AppointmentStatus.PENDING)
                .setReasonForVisit(request.getReasonForVisit())
                .setNotes(request.getNotes());

        // 6. Mark Slot as Unavailable (Optimistic Lock triggers here)
        slot.setIsAvailable(false);
        slotRepository.save(slot);

        // 7. Save Appointment
        Appointment savedAppointment = appointmentRepository.save(appointment);
        log.info("Appointment booked successfully: {}", appointmentNumber);

        // 8. Notify (Observer): patient + doctor are notified by AppointmentNotificationListener
        eventPublisher.publishEvent(event(AppointmentChangedEvent.Kind.BOOKED, savedAppointment));

        return appointmentMapper.toResponse(savedAppointment);
    }

    /**
     * Recovery method when all retries fail. Typed to the superclass
     * {@link OptimisticLockingFailureException}, so it also covers the
     * {@code ObjectOptimisticLockingFailureException} subclass.
     */
    @Recover
    public AppointmentResponse recoverBooking(OptimisticLockingFailureException ex,
                                              BookAppointmentRequest request,
                                              UserPrincipal caller) {
        log.error("All retry attempts failed for booking - Patient: {}, Slot: {}",
                request.getPatientId(), request.getSlotId());
        throw new BookingConflictException(
                "Unable to book appointment. Slot was booked by another user. Please try a different slot.");
    }

    public AppointmentResponse getAppointmentById(String appointmentId) {
        Appointment appointment = findAppointmentById(appointmentId);
        return appointmentMapper.toResponse(appointment);
    }

    public AppointmentResponse getAppointmentByNumber(String appointmentNumber) {
        Appointment appointment = EntityFinder.orThrow(
                appointmentRepository.findByAppointmentNumber(appointmentNumber),
                "Appointment not found with number: " + appointmentNumber);
        return appointmentMapper.toResponse(appointment);
    }

    public List<AppointmentResponse> getPatientAppointments(String patientId, UserPrincipal caller) {
        accessGuard.assertCanViewPatientHistory(caller, patientId);
        return appointmentMapper.toResponseList(appointmentRepository.findByPatientOrderByDateDesc(patientId));
    }

    public List<AppointmentResponse> getUpcomingPatientAppointments(String patientId, UserPrincipal caller) {
        accessGuard.assertCanViewPatientHistory(caller, patientId);
        return appointmentMapper.toResponseList(appointmentRepository.findUpcomingByPatient(patientId));
    }

    public List<AppointmentResponse> getDoctorAppointments(String doctorId, UserPrincipal caller) {
        accessGuard.assertCanViewDoctorSchedule(caller, doctorId);
        return appointmentMapper.toResponseList(appointmentRepository.findByDoctorDoctorId(doctorId));
    }

    public List<AppointmentResponse> getUpcomingDoctorAppointments(String doctorId, UserPrincipal caller) {
        accessGuard.assertCanViewDoctorSchedule(caller, doctorId);
        return appointmentMapper.toResponseList(appointmentRepository.findUpcomingByDoctor(doctorId));
    }

    public List<AppointmentResponse> getDoctorAppointmentsByDate(String doctorId, LocalDate date, UserPrincipal caller) {
        accessGuard.assertCanViewDoctorSchedule(caller, doctorId);
        return appointmentMapper.toResponseList(appointmentRepository.findByDoctorAndDate(doctorId, date));
    }

    @Transactional
    @Retryable(
            retryFor = {OptimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public AppointmentResponse confirmAppointment(String appointmentId, UserPrincipal caller) {
        log.info("Confirming appointment: {}", appointmentId);

        Appointment appointment = findAppointmentById(appointmentId);
        accessGuard.assertOwnsAppointmentAsDoctor(caller, appointment);
        transitionValidator.assertCanConfirm(appointment.getStatus());

        appointment.setStatus(AppointmentStatus.CONFIRMED);
        Appointment saved = appointmentRepository.save(appointment);

        log.info("Appointment confirmed: {}", appointmentId);
        eventPublisher.publishEvent(event(AppointmentChangedEvent.Kind.CONFIRMED, saved));

        return appointmentMapper.toResponse(saved);
    }

    @Transactional
    @Retryable(
            retryFor = {OptimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public AppointmentResponse cancelAppointment(String appointmentId, UserPrincipal caller) {
        log.info("Cancelling appointment: {}", appointmentId);

        Appointment appointment = findAppointmentById(appointmentId);
        accessGuard.assertCanCancel(caller, appointment);
        transitionValidator.assertCanCancel(appointment.getStatus());

        appointment.setStatus(AppointmentStatus.CANCELLED);

        // Free the slot in the same transaction (mirrors how booking reserves it).
        DoctorAvailability slot = appointment.getSlot();
        if (slot != null && Boolean.FALSE.equals(slot.getIsAvailable())) {
            slot.setIsAvailable(true);
            slotRepository.save(slot);
        }

        Appointment saved = appointmentRepository.save(appointment);

        log.info("Appointment cancelled: {}", appointmentId);
        eventPublisher.publishEvent(event(AppointmentChangedEvent.Kind.CANCELLED, saved));

        return appointmentMapper.toResponse(saved);
    }

    @Transactional
    @Retryable(
            retryFor = {OptimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public AppointmentResponse completeAppointment(String appointmentId, UserPrincipal caller) {
        log.info("Completing appointment: {}", appointmentId);

        Appointment appointment = findAppointmentById(appointmentId);
        accessGuard.assertOwnsAppointmentAsDoctor(caller, appointment);
        transitionValidator.assertCanComplete(appointment.getStatus());

        appointment.setStatus(AppointmentStatus.COMPLETED);
        Appointment saved = appointmentRepository.save(appointment);

        log.info("Appointment completed: {}", appointmentId);
        eventPublisher.publishEvent(event(AppointmentChangedEvent.Kind.COMPLETED, saved));

        return appointmentMapper.toResponse(saved);
    }

    @Transactional
    @Retryable(
            retryFor = {OptimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public AppointmentResponse updateAppointmentNotes(String appointmentId, String notes, UserPrincipal caller) {
        log.info("Updating notes for appointment: {}", appointmentId);

        Appointment appointment = findAppointmentById(appointmentId);
        accessGuard.assertOwnsAppointmentAsDoctor(caller, appointment);
        transitionValidator.assertCanEditNotes(appointment.getStatus());

        appointment.setNotes(notes);
        Appointment saved = appointmentRepository.save(appointment);

        log.info("Successfully updated notes for appointment: {}", appointmentId);
        eventPublisher.publishEvent(event(AppointmentChangedEvent.Kind.NOTES_UPDATED, saved));

        return appointmentMapper.toResponse(saved);
    }

    @Transactional
    @Retryable(
            retryFor = {OptimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public AppointmentResponse markNoShow(String appointmentId, UserPrincipal caller) {
        log.info("Marking appointment as no-show: {}", appointmentId);

        Appointment appointment = findAppointmentById(appointmentId);
        accessGuard.assertOwnsAppointmentAsDoctor(caller, appointment);
        transitionValidator.assertCanMarkNoShow(appointment.getStatus());

        appointment.setStatus(AppointmentStatus.NO_SHOW);
        Appointment saved = appointmentRepository.save(appointment);

        // No notification is sent for a no-show (unchanged behavior).
        log.info("Appointment marked as no-show: {}", appointmentId);
        return appointmentMapper.toResponse(saved);
    }

    // =============== HELPER METHODS ===============

    private Appointment findAppointmentById(String appointmentId) {
        return EntityFinder.findOrThrow(appointmentRepository, appointmentId,
                "Appointment not found with ID: " + appointmentId);
    }

    private String generateAppointmentNumber() {
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String random = String.format("%04d", SECURE_RANDOM.nextInt(10000));
        return "APT" + timestamp + random;
    }

    /** Snapshots the data notification listeners need, captured while the entity is still managed. */
    private AppointmentChangedEvent event(AppointmentChangedEvent.Kind kind, Appointment a) {
        return new AppointmentChangedEvent(
                kind,
                a.getAppointmentId(),
                a.getPatient().getPatientId(),
                a.getDoctor().getDoctorId(),
                a.getPatient().getFirstName(),
                a.getDoctor().getLastName(),
                a.getSlot().getSlotDate(),
                a.getSlot().getStartTime());
    }
}
