package com.clinic.doc_appointment.service;

import com.clinic.doc_appointment.dto.request.BookAppointmentRequest;
import com.clinic.doc_appointment.dto.response.AppointmentResponse;
import com.clinic.doc_appointment.entity.Appointment;
import com.clinic.doc_appointment.entity.Doctor;
import com.clinic.doc_appointment.entity.DoctorAvailability;
import com.clinic.doc_appointment.entity.Patient;
import com.clinic.doc_appointment.enums.AppointmentStatus;
import com.clinic.doc_appointment.exception.BookingConflictException;
import com.clinic.doc_appointment.exception.ResourceNotFoundException;
import com.clinic.doc_appointment.exception.SlotAlreadyBookedException;
import com.clinic.doc_appointment.repository.AppointmentRepository;
import com.clinic.doc_appointment.repository.DoctorAvailabilityRepository;
import com.clinic.doc_appointment.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final DoctorAvailabilityRepository slotRepository;

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
    public AppointmentResponse confirmAppointment(String appointmentId) {
        log.info("Confirming appointment: {}", appointmentId);

        Appointment appointment = findAppointmentById(appointmentId);

        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new RuntimeException("Only pending appointments can be confirmed");
        }

        appointment.setStatus(AppointmentStatus.CONFIRMED);
        Appointment saved = appointmentRepository.save(appointment);

        log.info("Appointment confirmed: {}", appointmentId);
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
            throw new RuntimeException("Appointment is already cancelled");
        }

        if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new RuntimeException("Cannot cancel a completed appointment");
        }

        // Update status
        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointmentRepository.save(appointment);

        // Make slot available again
        DoctorAvailability slot = appointment.getSlot();
        slot.setIsAvailable(true);
        slotRepository.save(slot);

        log.info("Appointment cancelled: {}", appointmentId);
        return mapToResponse(appointment);
    }

    @Transactional
    public AppointmentResponse completeAppointment(String appointmentId) {
        log.info("Completing appointment: {}", appointmentId);

        Appointment appointment = findAppointmentById(appointmentId);

        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new RuntimeException("Only confirmed appointments can be completed");
        }

        appointment.setStatus(AppointmentStatus.COMPLETED);
        Appointment saved = appointmentRepository.save(appointment);

        log.info("Appointment completed: {}", appointmentId);
        return mapToResponse(saved);
    }

    @Transactional
    public AppointmentResponse markNoShow(String appointmentId) {
        log.info("Marking appointment as no-show: {}", appointmentId);

        Appointment appointment = findAppointmentById(appointmentId);

        if (appointment.getStatus() == AppointmentStatus.CANCELLED ||
                appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new RuntimeException("Cannot mark cancelled/completed appointment as no-show");
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
        String random = String.format("%04d", new Random().nextInt(10000));
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