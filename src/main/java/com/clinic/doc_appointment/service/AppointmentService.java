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
import com.clinic.doc_appointment.exception.SlotNotAvailableException;
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

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * One retry policy for every conflict-prone mutation in this class. Kept as constants rather
     * than repeated literals so the six {@code @Retryable} declarations cannot drift apart — they
     * previously had, with only {@code bookAppointment} carrying a {@code maxDelay}.
     */
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 100L;
    private static final double RETRY_MULTIPLIER = 2.0;
    private static final long RETRY_MAX_DELAY_MS = 1000L;

    /**
     * Alphabet for the random suffix of an appointment number. Excludes the characters that are
     * easy to confuse when a number is read aloud or typed from a screenshot (0/O, 1/I/L).
     */
    private static final char[] NUMBER_SUFFIX_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();
    private static final int NUMBER_SUFFIX_LENGTH = 6;

    private static final DateTimeFormatter NUMBER_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

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
            maxAttempts = MAX_RETRY_ATTEMPTS,
            backoff = @Backoff(delay = RETRY_DELAY_MS, multiplier = RETRY_MULTIPLIER, maxDelay = RETRY_MAX_DELAY_MS),
            recover = "recoverBooking"
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

        // 4. A slot that has already started cannot be booked. Availability alone is not enough:
        // cancelling an old appointment frees its slot again, and past slots for a doctor stay in
        // the table, so without this check a patient can book a consultation in the past.
        assertSlotIsInTheFuture(slot);

        // 5. Generate Appointment Number
        String appointmentNumber = generateAppointmentNumber();

        // 6. Create Appointment
        Appointment appointment = new Appointment()
                .setAppointmentNumber(appointmentNumber)
                .setPatient(patient)
                .setDoctor(slot.getDoctor())
                .setSlot(slot)
                .setStatus(AppointmentStatus.PENDING)
                .setReasonForVisit(request.getReasonForVisit())
                .setNotes(request.getNotes());

        // 7. Mark Slot as Unavailable (Optimistic Lock triggers here)
        slot.setIsAvailable(false);
        slotRepository.save(slot);

        // 8. Save Appointment
        Appointment savedAppointment = appointmentRepository.save(appointment);
        log.info("Appointment booked successfully: {}", appointmentNumber);

        // 9. Notify (Observer): patient + doctor are notified by AppointmentNotificationListener
        eventPublisher.publishEvent(event(AppointmentChangedEvent.Kind.BOOKED, savedAppointment));

        return appointmentMapper.toResponse(savedAppointment);
    }

    // =============== RECOVERY METHODS ===============
    //
    // Two separate defects lived here, both invisible without a real proxy.
    //
    // 1. @Recover is resolved per BEAN, not per method, and Spring Retry's search picks the closest
    //    EXCEPTION match without checking that the parameters are compatible - parameters only break
    //    a tie at equal distance. With a single @Recover taking a BookAppointmentRequest, the five
    //    id-based methods were routed into it and invoked reflectively with an appointment id, so the
    //    caller got 400 "argument type mismatch" instead of a conflict. Each @Retryable above now
    //    names its recovery method explicitly, and each signature mirrors the method it recovers.
    //
    // 2. Spring Retry invokes recovery for exceptions it never retried. A non-retryable exception
    //    makes canRetry() false, the loop exits, and handleRetryExhausted() calls the recovery
    //    callback anyway - with the original cause. Nothing matched a ForbiddenOperationException, a
    //    SlotAlreadyBookedException or a ResourceNotFoundException, so the handler threw
    //    ExhaustedRetryException("Cannot locate recovery method") and EVERY such failure came back as
    //    500. A patient booking someone else's slot, a doctor confirming an appointment that is not
    //    theirs, an already-booked slot: all 500s. That is why these methods take Throwable and
    //    rethrow anything that is not an optimistic-lock conflict.

    /** Recovery for {@link #bookAppointment}. */
    @Recover
    public AppointmentResponse recoverBooking(Throwable cause,
                                              BookAppointmentRequest request,
                                              UserPrincipal caller) {
        rethrowIfNotAConflict(cause);
        log.error("All retry attempts failed for booking - Patient: {}, Slot: {}",
                request.getPatientId(), request.getSlotId());
        throw new BookingConflictException(
                "Unable to book appointment. Slot was booked by another user. Please try a different slot.");
    }

    /** Recovery for confirm / cancel / complete / no-show, which all take {@code (id, caller)}. */
    @Recover
    public AppointmentResponse recoverStatusChange(Throwable cause,
                                                   String appointmentId,
                                                   UserPrincipal caller) {
        rethrowIfNotAConflict(cause);
        log.error("All retry attempts failed updating appointment {}", appointmentId);
        throw conflictOnAppointment();
    }

    /** Recovery for {@link #updateAppointmentNotes}, whose extra argument needs its own shape. */
    @Recover
    public AppointmentResponse recoverNotesUpdate(Throwable cause,
                                                  String appointmentId,
                                                  String notes,
                                                  UserPrincipal caller) {
        rethrowIfNotAConflict(cause);
        log.error("All retry attempts failed updating notes for appointment {}", appointmentId);
        throw conflictOnAppointment();
    }

    private static BookingConflictException conflictOnAppointment() {
        return new BookingConflictException(
                "This appointment was changed by someone else while you were working on it. "
                        + "Please refresh and try again.");
    }

    /**
     * Passes a non-conflict cause straight through, unchanged.
     *
     * <p>Recovery is reached for every exception that ends the retry loop, including ones that were
     * never retryable. Those already carry the right status - 403 for a denied guard, 404 for a
     * missing row, 409 for an already-booked slot - and translating them into anything else would
     * lose it.
     */
    private static void rethrowIfNotAConflict(Throwable cause) {
        if (cause instanceof OptimisticLockingFailureException) {
            return;
        }
        if (cause instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException(cause);
    }

    // =============== READS ===============

    @Transactional(readOnly = true)
    public AppointmentResponse getAppointmentById(String appointmentId, UserPrincipal caller) {
        Appointment appointment = findAppointmentById(appointmentId);
        accessGuard.assertCanViewAppointment(caller, appointment);
        return appointmentMapper.toResponse(appointment);
    }

    @Transactional(readOnly = true)
    public AppointmentResponse getAppointmentByNumber(String appointmentNumber, UserPrincipal caller) {
        Appointment appointment = EntityFinder.orThrow(
                appointmentRepository.findByAppointmentNumber(appointmentNumber),
                "Appointment not found with number: " + appointmentNumber);
        accessGuard.assertCanViewAppointment(caller, appointment);
        return appointmentMapper.toResponse(appointment);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getPatientAppointments(String patientId, UserPrincipal caller) {
        accessGuard.assertCanViewPatientHistory(caller, patientId);
        return appointmentMapper.toResponseList(appointmentRepository.findByPatientOrderByDateDesc(patientId));
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getUpcomingPatientAppointments(String patientId, UserPrincipal caller) {
        accessGuard.assertCanViewPatientHistory(caller, patientId);
        return appointmentMapper.toResponseList(appointmentRepository.findUpcomingByPatient(patientId));
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getDoctorAppointments(String doctorId, UserPrincipal caller) {
        accessGuard.assertCanViewDoctorSchedule(caller, doctorId);
        return appointmentMapper.toResponseList(appointmentRepository.findByDoctorDoctorId(doctorId));
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getUpcomingDoctorAppointments(String doctorId, UserPrincipal caller) {
        accessGuard.assertCanViewDoctorSchedule(caller, doctorId);
        return appointmentMapper.toResponseList(appointmentRepository.findUpcomingByDoctor(doctorId));
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getDoctorAppointmentsByDate(String doctorId, LocalDate date, UserPrincipal caller) {
        accessGuard.assertCanViewDoctorSchedule(caller, doctorId);
        return appointmentMapper.toResponseList(appointmentRepository.findByDoctorAndDate(doctorId, date));
    }

    // =============== MUTATIONS ===============

    @Transactional
    @Retryable(
            retryFor = {OptimisticLockingFailureException.class},
            maxAttempts = MAX_RETRY_ATTEMPTS,
            backoff = @Backoff(delay = RETRY_DELAY_MS, multiplier = RETRY_MULTIPLIER, maxDelay = RETRY_MAX_DELAY_MS),
            recover = "recoverStatusChange"
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
            maxAttempts = MAX_RETRY_ATTEMPTS,
            backoff = @Backoff(delay = RETRY_DELAY_MS, multiplier = RETRY_MULTIPLIER, maxDelay = RETRY_MAX_DELAY_MS),
            recover = "recoverStatusChange"
    )
    public AppointmentResponse cancelAppointment(String appointmentId, UserPrincipal caller) {
        log.info("Cancelling appointment: {}", appointmentId);

        Appointment appointment = findAppointmentById(appointmentId);
        accessGuard.assertCanCancel(caller, appointment);
        transitionValidator.assertCanCancel(appointment.getStatus());

        appointment.setStatus(AppointmentStatus.CANCELLED);

        // Free the slot in the same transaction (mirrors how booking reserves it). Only a slot that
        // is still in the future is worth re-listing; releasing a past one would advertise a
        // consultation nobody can attend.
        DoctorAvailability slot = appointment.getSlot();
        if (slot != null && Boolean.FALSE.equals(slot.getIsAvailable()) && isInTheFuture(slot)) {
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
            maxAttempts = MAX_RETRY_ATTEMPTS,
            backoff = @Backoff(delay = RETRY_DELAY_MS, multiplier = RETRY_MULTIPLIER, maxDelay = RETRY_MAX_DELAY_MS),
            recover = "recoverStatusChange"
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
            maxAttempts = MAX_RETRY_ATTEMPTS,
            backoff = @Backoff(delay = RETRY_DELAY_MS, multiplier = RETRY_MULTIPLIER, maxDelay = RETRY_MAX_DELAY_MS),
            recover = "recoverNotesUpdate"
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
            maxAttempts = MAX_RETRY_ATTEMPTS,
            backoff = @Backoff(delay = RETRY_DELAY_MS, multiplier = RETRY_MULTIPLIER, maxDelay = RETRY_MAX_DELAY_MS),
            recover = "recoverStatusChange"
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

    private static boolean isInTheFuture(DoctorAvailability slot) {
        return LocalDateTime.of(slot.getSlotDate(), slot.getStartTime()).isAfter(LocalDateTime.now());
    }

    private static void assertSlotIsInTheFuture(DoctorAvailability slot) {
        if (!isInTheFuture(slot)) {
            throw new SlotNotAvailableException(
                    "This slot has already started and can no longer be booked.");
        }
    }

    /**
     * {@code APT} + second-resolution timestamp + a random suffix, against a unique column.
     *
     * <p>The suffix carries ~30 bits, so two bookings landing in the same second collide with
     * probability ~1e-9 rather than the 1-in-10,000 of the previous four decimal digits — which
     * surfaced as a bare 409 "data conflict" on a perfectly valid booking, since a unique-key
     * violation is not something the optimistic-lock retry covers.
     */
    private String generateAppointmentNumber() {
        StringBuilder suffix = new StringBuilder(NUMBER_SUFFIX_LENGTH);
        for (int i = 0; i < NUMBER_SUFFIX_LENGTH; i++) {
            suffix.append(NUMBER_SUFFIX_ALPHABET[SECURE_RANDOM.nextInt(NUMBER_SUFFIX_ALPHABET.length)]);
        }
        return "APT" + LocalDateTime.now().format(NUMBER_TIMESTAMP) + suffix;
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
