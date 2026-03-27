package com.clinic.doc_appointment.service;

import com.clinic.doc_appointment.dto.request.BookAppointmentRequest;
import com.clinic.doc_appointment.dto.response.AppointmentResponse;
import com.clinic.doc_appointment.entity.Appointment;
import com.clinic.doc_appointment.entity.Doctor;
import com.clinic.doc_appointment.entity.DoctorAvailability;
import com.clinic.doc_appointment.entity.Patient;
import com.clinic.doc_appointment.enums.AppointmentStatus;
import com.clinic.doc_appointment.exception.BookingConflictException;
import com.clinic.doc_appointment.exception.InvalidStateException;
import com.clinic.doc_appointment.exception.ResourceNotFoundException;
import com.clinic.doc_appointment.exception.SlotAlreadyBookedException;
import com.clinic.doc_appointment.repository.AppointmentRepository;
import com.clinic.doc_appointment.repository.DoctorAvailabilityRepository;
import com.clinic.doc_appointment.repository.PatientRepository;
import com.clinic.doc_appointment.service.NotificationService;
import com.clinic.doc_appointment.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();  // ✅ Thread-safe, no duplicate seeds

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final DoctorAvailabilityRepository slotRepository;
    private final NotificationService notificationService;

    /**
     * Book appointment with Optimistic Locking + Retry
     */
    @Transactional
    @Retryable(
            retryFor = {OptimisticLockingFailureException.class, ObjectOptimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2, maxDelay = 1000)
    )
    public AppointmentResponse bookAppointment(BookAppointmentRequest request) {
        log.info("Booking appointment - Patient: {}, Slot: {}",
                request.getPatientId(), request.getSlotId());

        // 1. Validate Patient
        Patient patient = patientRepository.findById(request.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Patient not found with ID: " + request.getPatientId()));

        // 2. Get Slot
        DoctorAvailability slot = slotRepository.findById(request.getSlotId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Slot not found with ID: " + request.getSlotId()));

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

        // 8. Notifications
        notificationService.sendNotification(
            patient.getPatientId(),
            "Appointment Requested",
            "Your appointment request for " + slot.getSlotDate() + " at " + slot.getStartTime() + " is pending confirmation.",
            NotificationType.APPOINTMENT_UPDATE,
            savedAppointment.getAppointmentId()
        );
        
        notificationService.sendNotification(
            slot.getDoctor().getDoctorId(),
            "New Appointment Request",
            patient.getFirstName() + " has requested an appointment for " + slot.getSlotDate() + " at " + slot.getStartTime() + ".",
            NotificationType.APPOINTMENT_UPDATE,
            savedAppointment.getAppointmentId()
        );

        return mapToResponse(savedAppointment);
    }

    /**
     * Recovery method when all retries fail
     */
    @Recover
    public AppointmentResponse recoverBooking(OptimisticLockingFailureException ex,
                                              BookAppointmentRequest request) {
        log.error("All retry attempts failed for booking - Patient: {}, Slot: {}",
                request.getPatientId(), request.getSlotId());
        throw new BookingConflictException(
                "Unable to book appointment. Slot was booked by another user. Please try a different slot.");
    }

    @Recover
    public AppointmentResponse recoverBooking(ObjectOptimisticLockingFailureException ex,
                                              BookAppointmentRequest request) {
        log.error("All retry attempts failed for booking - Patient: {}, Slot: {}",
                request.getPatientId(), request.getSlotId());
        throw new BookingConflictException(
                "Unable to book appointment. Slot was booked by another user. Please try a different slot.");
    }

    public AppointmentResponse getAppointmentById(String appointmentId) {
        Appointment appointment = findAppointmentById(appointmentId);
        return mapToResponse(appointment);
    }

    public AppointmentResponse getAppointmentByNumber(String appointmentNumber) {
        Appointment appointment = appointmentRepository.findByAppointmentNumber(appointmentNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Appointment not found with number: " + appointmentNumber));
        return mapToResponse(appointment);
    }

    public List<AppointmentResponse> getPatientAppointments(String patientId) {
        return appointmentRepository.findByPatientOrderByDateDesc(patientId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<AppointmentResponse> getUpcomingPatientAppointments(String patientId) {
        return appointmentRepository.findUpcomingByPatient(patientId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<AppointmentResponse> getDoctorAppointments(String doctorId) {
        return appointmentRepository.findByDoctorDoctorId(doctorId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<AppointmentResponse> getUpcomingDoctorAppointments(String doctorId) {
        return appointmentRepository.findUpcomingByDoctor(doctorId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<AppointmentResponse> getDoctorAppointmentsByDate(String doctorId, LocalDate date) {
        return appointmentRepository.findByDoctorAndDate(doctorId, date)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    @Retryable(
            retryFor = {OptimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public AppointmentResponse confirmAppointment(String appointmentId) {
        log.info("Confirming appointment: {}", appointmentId);

        Appointment appointment = findAppointmentById(appointmentId);

        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new InvalidStateException("Only pending appointments can be confirmed");
        }

        appointment.setStatus(AppointmentStatus.CONFIRMED);
        Appointment saved = appointmentRepository.save(appointment);

        log.info("Appointment confirmed: {}", appointmentId);

        notificationService.sendNotification(
            appointment.getPatient().getPatientId(),
            "Appointment Confirmed",
            "Your appointment for " + appointment.getSlot().getSlotDate() + " has been confirmed by the doctor.",
            NotificationType.APPOINTMENT_UPDATE,
            saved.getAppointmentId()
        );

        return mapToResponse(saved);
    }

    @Transactional
    @Retryable(
            retryFor = {OptimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public AppointmentResponse cancelAppointment(String appointmentId) {
        log.info("Cancelling appointment: {}", appointmentId);

        Appointment appointment = findAppointmentById(appointmentId);

        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new InvalidStateException("Appointment is already cancelled");
        }

        if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new InvalidStateException("Cannot cancel a completed appointment");
        }

        // Update status - slot will be freed automatically by @PostUpdate listener
        appointment.setStatus(AppointmentStatus.CANCELLED);
        Appointment saved = appointmentRepository.save(appointment);

        log.info("Appointment cancelled: {}", appointmentId);

        notificationService.sendNotification(
            appointment.getPatient().getPatientId(),
            "Appointment Cancelled",
            "Your appointment for " + appointment.getSlot().getSlotDate() + " has been cancelled.",
            NotificationType.APPOINTMENT_UPDATE,
            saved.getAppointmentId()
        );

        notificationService.sendNotification(
            appointment.getDoctor().getDoctorId(),
            "Appointment Cancelled",
            "The appointment for " + appointment.getPatient().getFirstName() + " on " + appointment.getSlot().getSlotDate() + " has been cancelled.",
            NotificationType.APPOINTMENT_UPDATE,
            saved.getAppointmentId()
        );

        return mapToResponse(saved);
    }

    @Transactional
    @Retryable(
            retryFor = {OptimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public AppointmentResponse completeAppointment(String appointmentId) {
        log.info("Completing appointment: {}", appointmentId);

        Appointment appointment = findAppointmentById(appointmentId);

        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new InvalidStateException("Only confirmed appointments can be completed");
        }

        appointment.setStatus(AppointmentStatus.COMPLETED);
        Appointment saved = appointmentRepository.save(appointment);

        log.info("Appointment completed: {}", appointmentId);

        notificationService.sendNotification(
            appointment.getPatient().getPatientId(),
            "Appointment Completed",
            "Thank you for visiting! Hope your consultation went well.",
            NotificationType.GENERAL_ALERT,
            saved.getAppointmentId()
        );

        return mapToResponse(saved);
    }

    @Transactional
    @Retryable(
            retryFor = {OptimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public AppointmentResponse updateAppointmentNotes(String appointmentId, String notes) {
        log.info("Updating notes for appointment: {}", appointmentId);

        Appointment appointment = findAppointmentById(appointmentId);

        if (appointment.getStatus() == AppointmentStatus.CANCELLED ||
            appointment.getStatus() == AppointmentStatus.NO_SHOW) {
            throw new InvalidStateException("Cannot add notes to a cancelled or no-show appointment.");
        }

        appointment.setNotes(notes);
        Appointment saved = appointmentRepository.save(appointment);

        log.info("Successfully updated notes for appointment: {}", appointmentId);

        // Notify patient that clinical notes/prescriptions were added
        notificationService.sendNotification(
            appointment.getPatient().getPatientId(),
            "Clinical Notes Updated",
            "Dr. " + appointment.getDoctor().getLastName() + " has added notes/prescriptions to your recent consultation.",
            NotificationType.APPOINTMENT_UPDATE,
            saved.getAppointmentId()
        );

        return mapToResponse(saved);
    }

    @Transactional
    @Retryable(
            retryFor = {OptimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public AppointmentResponse markNoShow(String appointmentId) {
        log.info("Marking appointment as no-show: {}", appointmentId);

        Appointment appointment = findAppointmentById(appointmentId);

        if (appointment.getStatus() == AppointmentStatus.CANCELLED ||
                appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new InvalidStateException("Cannot mark cancelled/completed appointment as no-show");
        }

        appointment.setStatus(AppointmentStatus.NO_SHOW);
        Appointment saved = appointmentRepository.save(appointment);

        log.info("Appointment marked as no-show: {}", appointmentId);
        return mapToResponse(saved);
    }

    // =============== HELPER METHODS ===============

    private Appointment findAppointmentById(String appointmentId) {
        return appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Appointment not found with ID: " + appointmentId));
    }

    private String generateAppointmentNumber() {
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String random = String.format("%04d", SECURE_RANDOM.nextInt(10000));
        return "APT" + timestamp + random;
    }

    private AppointmentResponse mapToResponse(Appointment appointment) {
        Doctor doctor = appointment.getDoctor();
        Patient patient = appointment.getPatient();
        DoctorAvailability slot = appointment.getSlot();

        String patientName = patient.getFirstName() +
                (patient.getLastName() != null ? " " + patient.getLastName() : "");

        String doctorName = doctor.getFirstName() +
                (doctor.getLastName() != null ? " " + doctor.getLastName() : "");

        return new AppointmentResponse()
                .setAppointmentId(appointment.getAppointmentId())
                .setAppointmentNumber(appointment.getAppointmentNumber())
                .setPatientId(patient.getPatientId())
                .setPatientName(patientName)
                .setPatientPhone(patient.getCountryCode() + " " + patient.getPhoneNumber())
                .setDoctorId(doctor.getDoctorId())
                .setDoctorName(doctorName)
                .setSpecialization(String.valueOf(doctor.getSpecialization()))
                .setConsultationFee(doctor.getConsultationFee())
                .setSlotId(slot.getSlotId())
                .setAppointmentDate(slot.getSlotDate())
                .setStartTime(slot.getStartTime())
                .setEndTime(slot.getEndTime())
                .setDurationMinutes(slot.getDurationMinutes())
                .setStatus(appointment.getStatus())
                .setReasonForVisit(appointment.getReasonForVisit())
                .setNotes(appointment.getNotes())
                .setCreatedAt(appointment.getCreatedAt())
                .setUpdatedAt(appointment.getUpdatedAt());
    }

}