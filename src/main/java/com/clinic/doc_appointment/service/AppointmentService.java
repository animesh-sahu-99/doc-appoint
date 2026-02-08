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
     *
     * @Retryable: If OptimisticLockingFailureException occurs, retry up to 3 times
     * @Backoff: Wait 100ms before first retry, then 200ms, then 400ms (multiplier = 2)
     */
    @Transactional
    @Retryable(
            retryFor = {
                    OptimisticLockingFailureException.class,
                    ObjectOptimisticLockingFailureException.class
            },
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2, maxDelay = 1000)
    )
    public AppointmentResponse bookAppointment(BookAppointmentRequest request) {
        log.info("Attempting to book appointment - Patient: {}, Slot: {}",
                request.getPatientId(), request.getSlotId());

        // 1. Validate Patient exists
        Patient patient = patientRepository.findById(request.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Patient not found with ID: " + request.getPatientId()));

        // 2. Get slot and check availability
        DoctorAvailability slot = slotRepository.findById(request.getSlotId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Slot not found with ID: " + request.getSlotId()));

        // 3. Check if slot is available
        if (!slot.getIsAvailable()) {
            throw new SlotAlreadyBookedException(
                    "This slot is already booked. Please select another slot.");
        }

        // 4. Generate unique appointment number
        String appointmentNumber = generateAppointmentNumber();

        // 5. Create appointment
        Appointment appointment = new Appointment()
                .setAppointmentNumber(appointmentNumber)
                .setPatient(patient)
                .setDoctor(slot.getDoctor())
                .setSlot(slot)
                .setStatus(AppointmentStatus.PENDING)
                .setReasonForVisit(request.getReasonForVisit())
                .setNotes(request.getNotes());

        // 6. Mark slot as unavailable
        // ✅ This is where optimistic locking kicks in!
        // If another transaction modified this slot, @Version check will fail
        slot.setIsAvailable(false);
        slotRepository.save(slot);

        // 7. Save appointment
        Appointment savedAppointment = appointmentRepository.save(appointment);

        log.info("Successfully booked appointment: {} for patient: {}",
                appointmentNumber, patient.getFirstName());

        return mapToAppointmentResponse(savedAppointment);
    }

    /**
     * Recovery method - Called when all retries are exhausted
     * Method signature must match the original method + Exception parameter
     */
    @Recover
    public AppointmentResponse recoverFromOptimisticLock(
            OptimisticLockingFailureException ex,
            BookAppointmentRequest request) {

        log.error("All retry attempts failed for booking - Patient: {}, Slot: {}",
                request.getPatientId(), request.getSlotId());

        throw new BookingConflictException(
                "Unable to book appointment. The slot was booked by another user. " +
                        "Please refresh and try a different slot.");
    }

    @Recover
    public AppointmentResponse recoverFromObjectOptimisticLock(
            ObjectOptimisticLockingFailureException ex,
            BookAppointmentRequest request) {

        log.error("All retry attempts failed for booking - Patient: {}, Slot: {}",
                request.getPatientId(), request.getSlotId());

        throw new BookingConflictException(
                "Unable to book appointment. The slot was booked by another user. " +
                        "Please refresh and try a different slot.");
    }

    /**
     * Cancel appointment - Also uses optimistic locking
     */
    @Transactional
    @Retryable(
            retryFor = {OptimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public AppointmentResponse cancelAppointment(String appointmentId) {
        log.info("Cancelling appointment: {}", appointmentId);

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Appointment not found with ID: " + appointmentId));

        // Validate cancellation
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new RuntimeException("Appointment is already cancelled");
        }

        if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new RuntimeException("Cannot cancel a completed appointment");
        }

        // Update appointment status
        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointmentRepository.save(appointment);

        // Make slot available again
        DoctorAvailability slot = appointment.getSlot();
        slot.setIsAvailable(true);
        slotRepository.save(slot);

        log.info("Successfully cancelled appointment: {}", appointmentId);

        return mapToAppointmentResponse(appointment);
    }

    /**
     * Confirm appointment
     */
    @Transactional
    public AppointmentResponse confirmAppointment(String appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Appointment not found with ID: " + appointmentId));

        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new RuntimeException("Only pending appointments can be confirmed");
        }

        appointment.setStatus(AppointmentStatus.CONFIRMED);
        return mapToAppointmentResponse(appointmentRepository.save(appointment));
    }

    /**
     * Complete appointment
     */
    @Transactional
    public AppointmentResponse completeAppointment(String appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Appointment not found with ID: " + appointmentId));

        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new RuntimeException("Only confirmed appointments can be completed");
        }

        appointment.setStatus(AppointmentStatus.COMPLETED);
        return mapToAppointmentResponse(appointmentRepository.save(appointment));
    }

    /**
     * Get appointment by ID
     */
    public AppointmentResponse getAppointmentById(String appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Appointment not found with ID: " + appointmentId));
        return mapToAppointmentResponse(appointment);
    }

    /**
     * Get appointment by number
     */
    public AppointmentResponse getAppointmentByNumber(String appointmentNumber) {
        Appointment appointment = appointmentRepository.findByAppointmentNumber(appointmentNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Appointment not found with number: " + appointmentNumber));
        return mapToAppointmentResponse(appointment);
    }

    /**
     * Get all appointments for a patient
     */
    public List<AppointmentResponse> getPatientAppointments(String patientId) {
        return appointmentRepository.findByPatientPatientId(patientId)
                .stream()
                .map(this::mapToAppointmentResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get all appointments for a doctor
     */
    public List<AppointmentResponse> getDoctorAppointments(String doctorId) {
        return appointmentRepository.findByDoctorDoctorId(doctorId)
                .stream()
                .map(this::mapToAppointmentResponse)
                .collect(Collectors.toList());
    }

    // ==================== PRIVATE HELPER METHODS ====================

    private String generateAppointmentNumber() {
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String random = String.format("%04d", new Random().nextInt(10000));
        return "APT" + timestamp + random;
    }

    private AppointmentResponse mapToAppointmentResponse(Appointment appointment) {
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
                .setAppointmentDate(slot.getSlotDate())
                .setStartTime(slot.getStartTime())
                .setEndTime(slot.getEndTime())
                .setDurationMinutes(slot.getDurationMinutes())
                .setStatus(appointment.getStatus())
                .setReasonForVisit(appointment.getReasonForVisit())
                .setCreatedAt(appointment.getCreatedAt());
    }
}