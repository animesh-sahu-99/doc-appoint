package com.clinic.doc_appointment.service;

import com.clinic.doc_appointment.dto.request.BulkSlotRequest;
import com.clinic.doc_appointment.dto.request.CreateSlotRequest;
import com.clinic.doc_appointment.dto.response.SlotResponse;
import com.clinic.doc_appointment.entity.Doctor;
import com.clinic.doc_appointment.entity.DoctorAvailability;
import com.clinic.doc_appointment.exception.BookedSlotException;
import com.clinic.doc_appointment.exception.InvalidSlotDateException;
import com.clinic.doc_appointment.exception.InvalidSlotTimeException;
import com.clinic.doc_appointment.exception.ResourceNotFoundException;
import com.clinic.doc_appointment.exception.SlotOverlapException;
import com.clinic.doc_appointment.repository.DoctorAvailabilityRepository;
import com.clinic.doc_appointment.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SlotService {

    private final DoctorAvailabilityRepository slotRepository;
    private final DoctorRepository doctorRepository;

    @Transactional
    public SlotResponse createSlot(CreateSlotRequest request) {
        log.info("Creating slot for doctor: {} on {}", request.getDoctorId(), request.getSlotDate());

        // Validate doctor exists
        Doctor doctor = doctorRepository.findById(request.getDoctorId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Doctor not found with ID: " + request.getDoctorId()));

        // Validate slot date is not in the past
        if (request.getSlotDate().isBefore(LocalDate.now())) {
            throw new InvalidSlotDateException("Cannot create slots for past dates");
        }

        // Validate time range
        if (request.getEndTime().isBefore(request.getStartTime()) ||
                request.getEndTime().equals(request.getStartTime())) {
            throw new InvalidSlotTimeException("End time must be after start time");
        }

        // Check for overlapping slots
        List<DoctorAvailability> overlapping = slotRepository.findOverlappingSlots(
                request.getDoctorId(),
                request.getSlotDate(),
                request.getStartTime(),
                request.getEndTime());

        if (!overlapping.isEmpty()) {
            throw new SlotOverlapException("Slot overlaps with existing slots");
        }

        // Create slot
        DoctorAvailability slot = new DoctorAvailability()
                .setDoctor(doctor)
                .setSlotDate(request.getSlotDate())
                .setStartTime(request.getStartTime())
                .setEndTime(request.getEndTime())
                .setDurationMinutes(request.getDurationMinutes())
                .setIsAvailable(true);

        DoctorAvailability savedSlot = slotRepository.save(slot);
        log.info("Slot created successfully: {}", savedSlot.getSlotId());

        return mapToResponse(savedSlot);
    }

    @Transactional
    public List<SlotResponse> createBulkSlots(BulkSlotRequest request) {
        log.info("Creating bulk slots for doctor: {} on {}",
                request.getDoctorId(), request.getSlotDate());

        // Validate doctor exists
        Doctor doctor = doctorRepository.findById(request.getDoctorId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Doctor not found with ID: " + request.getDoctorId()));

        // Validate slot date is not in the past
        if (request.getSlotDate().isBefore(LocalDate.now())) {
            throw new InvalidSlotDateException("Cannot create slots for past dates");
        }

        // Validate time range
        if (request.getDayEndTime().isBefore(request.getDayStartTime()) ||
                request.getDayEndTime().equals(request.getDayStartTime())) {
            throw new InvalidSlotTimeException("Day end time must be after start time");
        }

        List<DoctorAvailability> slots = new ArrayList<>();
        LocalTime currentStart = request.getDayStartTime();

        // Fixed: Use proper comparison instead of plusSeconds(1) hack
        while (!currentStart.plusMinutes(request.getSlotDurationMinutes())
                .isAfter(request.getDayEndTime())) {

            LocalTime currentEnd = currentStart.plusMinutes(request.getSlotDurationMinutes());

            // Check for overlapping slots
            List<DoctorAvailability> overlapping = slotRepository.findOverlappingSlots(
                    request.getDoctorId(),
                    request.getSlotDate(),
                    currentStart,
                    currentEnd);

            if (overlapping.isEmpty()) {
                DoctorAvailability slot = new DoctorAvailability()
                        .setDoctor(doctor)
                        .setSlotDate(request.getSlotDate())
                        .setStartTime(currentStart)
                        .setEndTime(currentEnd)
                        .setDurationMinutes(request.getSlotDurationMinutes())
                        .setIsAvailable(true);

                slots.add(slot);
            }

            // Move to next slot (including break)
            currentStart = currentEnd.plusMinutes(request.getBreakDurationMinutes());
        }

        List<DoctorAvailability> savedSlots = slotRepository.saveAll(slots);
        log.info("Created {} slots for doctor: {}", savedSlots.size(), request.getDoctorId());

        return mapToResponseList(savedSlots);
    }

    public SlotResponse getSlotById(String slotId) {
        DoctorAvailability slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Slot not found with ID: " + slotId));
        return mapToResponse(slot);
    }

    public List<SlotResponse> getAvailableSlotsByDoctor(String doctorId) {
        // Validate doctor exists
        if (!doctorRepository.existsById(doctorId)) {
            throw new ResourceNotFoundException("Doctor not found with ID: " + doctorId);
        }

        return mapToResponseList(slotRepository.findAvailableSlotsByDoctor(doctorId));
    }

    public List<SlotResponse> getAvailableSlotsByDoctorAndDate(String doctorId, LocalDate date) {
        // Validate doctor exists
        if (!doctorRepository.existsById(doctorId)) {
            throw new ResourceNotFoundException("Doctor not found with ID: " + doctorId);
        }

        return mapToResponseList(
                slotRepository.findByDoctorDoctorIdAndSlotDateAndIsAvailableTrue(doctorId, date));
    }

    public List<SlotResponse> getAvailableSlotsByDoctorAndDateRange(
            String doctorId, LocalDate startDate, LocalDate endDate) {

        // Validate doctor exists
        if (!doctorRepository.existsById(doctorId)) {
            throw new ResourceNotFoundException("Doctor not found with ID: " + doctorId);
        }

        return mapToResponseList(
                slotRepository.findAvailableSlotsByDoctorAndDateRange(doctorId, startDate, endDate));
    }

    public List<SlotResponse> getAllSlotsByDoctorAndDate(String doctorId, LocalDate date) {
        // Validate doctor exists
        if (!doctorRepository.existsById(doctorId)) {
            throw new ResourceNotFoundException("Doctor not found with ID: " + doctorId);
        }

        return mapToResponseList(slotRepository.findByDoctorDoctorIdAndSlotDate(doctorId, date));
    }

    @Transactional
    public void deleteSlot(String slotId) {
        log.info("Deleting slot: {}", slotId);

        DoctorAvailability slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Slot not found with ID: " + slotId));

        if (!slot.getIsAvailable()) {
            throw new BookedSlotException("Cannot delete a booked slot");
        }

        slotRepository.delete(slot);
        log.info("Slot deleted successfully: {}", slotId);
    }

    @Transactional
    public void deleteSlotsByDoctorAndDate(String doctorId, LocalDate date) {
        log.info("Deleting slots for doctor: {} on date: {}", doctorId, date);

        // Check if any slots are booked
        List<DoctorAvailability> slots = slotRepository.findByDoctorDoctorIdAndSlotDate(doctorId, date);
        boolean hasBookedSlots = slots.stream().anyMatch(slot -> !slot.getIsAvailable());

        if (hasBookedSlots) {
            throw new BookedSlotException("Cannot delete slots that are already booked");
        }

        slotRepository.deleteByDoctorDoctorIdAndSlotDate(doctorId, date);
        log.info("Deleted all slots for doctor: {} on date: {}", doctorId, date);
    }

    // =============== HELPER METHODS ===============

    private SlotResponse mapToResponse(DoctorAvailability slot) {
        Doctor doctor = slot.getDoctor();
        String doctorName = doctor.getFirstName() +
                (doctor.getLastName() != null ? " " + doctor.getLastName() : "");

        return new SlotResponse()
                .setSlotId(slot.getSlotId())
                .setDoctorId(doctor.getDoctorId())
                .setDoctorName(doctorName)
                .setSlotDate(slot.getSlotDate())
                .setStartTime(slot.getStartTime())
                .setEndTime(slot.getEndTime())
                .setDurationMinutes(slot.getDurationMinutes())
                .setIsAvailable(slot.getIsAvailable())
                .setCreatedAt(slot.getCreatedAt());
    }

    private List<SlotResponse> mapToResponseList(List<DoctorAvailability> slots) {
        return slots.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
}