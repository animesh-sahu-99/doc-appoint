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
import com.clinic.doc_appointment.mapper.SlotMapper;
import com.clinic.doc_appointment.repository.DoctorAvailabilityRepository;
import com.clinic.doc_appointment.repository.DoctorRepository;
import com.clinic.doc_appointment.util.EntityFinder;
import com.clinic.doc_appointment.validation.CompositeValidator;
import com.clinic.doc_appointment.validation.slot.SlotCreationValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SlotService {

    private final DoctorAvailabilityRepository slotRepository;
    private final DoctorRepository doctorRepository;
    private final SlotMapper slotMapper;
    private final List<SlotCreationValidator> slotCreationValidators;

    @Transactional
    public SlotResponse createSlot(CreateSlotRequest request) {
        log.info("Creating slot for doctor: {} on {}", request.getDoctorId(), request.getSlotDate());

        // Validate doctor exists
        Doctor doctor = EntityFinder.findOrThrow(doctorRepository, request.getDoctorId(),
                "Doctor not found with ID: " + request.getDoctorId());

        // Validate via composed slot-creation rules (date-not-past -> time-order -> overlap).
        new CompositeValidator<CreateSlotRequest>(slotCreationValidators).validate(request);

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

        return slotMapper.toResponse(savedSlot);
    }

    @Transactional
    public List<SlotResponse> createBulkSlots(BulkSlotRequest request) {
        log.info("Creating bulk slots for doctor: {} on {}",
                request.getDoctorId(), request.getSlotDate());

        // Validate doctor exists
        Doctor doctor = EntityFinder.findOrThrow(doctorRepository, request.getDoctorId(),
                "Doctor not found with ID: " + request.getDoctorId());

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

        return slotMapper.toResponseList(savedSlots);
    }

    public SlotResponse getSlotById(String slotId) {
        DoctorAvailability slot = EntityFinder.findOrThrow(slotRepository, slotId,
                "Slot not found with ID: " + slotId);
        return slotMapper.toResponse(slot);
    }

    public List<SlotResponse> getAvailableSlotsByDoctor(String doctorId) {
        // Validate doctor exists
        if (!doctorRepository.existsById(doctorId)) {
            throw new ResourceNotFoundException("Doctor not found with ID: " + doctorId);
        }

        return slotMapper.toResponseList(slotRepository.findAvailableSlotsByDoctor(doctorId));
    }

    public List<SlotResponse> getAvailableSlotsByDoctorAndDate(String doctorId, LocalDate date) {
        // Validate doctor exists
        if (!doctorRepository.existsById(doctorId)) {
            throw new ResourceNotFoundException("Doctor not found with ID: " + doctorId);
        }

        return slotMapper.toResponseList(
                slotRepository.findByDoctorDoctorIdAndSlotDateAndIsAvailableTrue(doctorId, date));
    }

    public List<SlotResponse> getAvailableSlotsByDoctorAndDateRange(
            String doctorId, LocalDate startDate, LocalDate endDate) {

        // Validate doctor exists
        if (!doctorRepository.existsById(doctorId)) {
            throw new ResourceNotFoundException("Doctor not found with ID: " + doctorId);
        }

        return slotMapper.toResponseList(
                slotRepository.findAvailableSlotsByDoctorAndDateRange(doctorId, startDate, endDate));
    }

    public List<SlotResponse> getAllSlotsByDoctorAndDate(String doctorId, LocalDate date) {
        // Validate doctor exists
        if (!doctorRepository.existsById(doctorId)) {
            throw new ResourceNotFoundException("Doctor not found with ID: " + doctorId);
        }

        return slotMapper.toResponseList(slotRepository.findByDoctorDoctorIdAndSlotDate(doctorId, date));
    }

    @Transactional
    public void deleteSlot(String slotId) {
        log.info("Deleting slot: {}", slotId);

        DoctorAvailability slot = EntityFinder.findOrThrow(slotRepository, slotId,
                "Slot not found with ID: " + slotId);

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
}
