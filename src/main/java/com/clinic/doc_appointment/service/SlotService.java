package com.clinic.doc_appointment.service;

import com.clinic.doc_appointment.dto.request.BulkSlotRequest;
import com.clinic.doc_appointment.dto.request.CreateSlotRequest;
import com.clinic.doc_appointment.dto.response.SlotResponse;
import com.clinic.doc_appointment.entity.Doctor;
import com.clinic.doc_appointment.entity.DoctorAvailability;
import com.clinic.doc_appointment.exception.ResourceNotFoundException;
import com.clinic.doc_appointment.repository.DoctorAvailabilityRepository;
import com.clinic.doc_appointment.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SlotService {

    private final DoctorAvailabilityRepository slotRepository;
    private final DoctorRepository doctorRepository;

    @Transactional
    public DoctorAvailability createSlot(CreateSlotRequest request) {
        Doctor doctor = doctorRepository.findById(request.getDoctorId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found"));

        // Check for overlapping slots
        List<DoctorAvailability> overlapping = slotRepository.findOverlappingSlots(
                request.getDoctorId(),
                request.getSlotDate(),
                request.getStartTime(),
                request.getEndTime()
        );

        if (!overlapping.isEmpty()) {
            throw new RuntimeException("Slot overlaps with existing slots");
        }

        DoctorAvailability slot = new DoctorAvailability()
                .setDoctor(doctor)
                .setSlotDate(request.getSlotDate())
                .setStartTime(request.getStartTime())
                .setEndTime(request.getEndTime())
                .setDurationMinutes(request.getDurationMinutes())
                .setIsAvailable(true);

        return slotRepository.save(slot);
    }

    @Transactional
    public List<DoctorAvailability> createBulkSlots(BulkSlotRequest request) {
        Doctor doctor = doctorRepository.findById(request.getDoctorId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found"));

        List<DoctorAvailability> slots = new ArrayList<>();
        LocalTime currentStart = request.getDayStartTime();

        while (currentStart.plusMinutes(request.getSlotDurationMinutes())
                .isBefore(request.getDayEndTime().plusSeconds(1))) {

            LocalTime currentEnd = currentStart.plusMinutes(request.getSlotDurationMinutes());

            DoctorAvailability slot = new DoctorAvailability()
                    .setDoctor(doctor)
                    .setSlotDate(request.getSlotDate())
                    .setStartTime(currentStart)
                    .setEndTime(currentEnd)
                    .setDurationMinutes(request.getSlotDurationMinutes())
                    .setIsAvailable(true);

            slots.add(slot);

            // Move to next slot start time (including break)
            currentStart = currentEnd.plusMinutes(request.getBreakDurationMinutes());
        }

        return slotRepository.saveAll(slots);
    }

    public List<SlotResponse> getAvailableSlots(Long doctorId, LocalDate date) {
        List<DoctorAvailability> slots = slotRepository
                .findByDoctorDoctorIdAndSlotDateAndIsAvailableTrue(doctorId, date);

        return slots.stream()
                .map(this::mapToSlotResponse)
                .collect(Collectors.toList());
    }

    public List<SlotResponse> getAvailableSlotsInRange(String doctorId, LocalDate startDate, LocalDate endDate) {
        List<DoctorAvailability> slots = slotRepository
                .findAvailableSlotsByDoctorAndDateRange(doctorId, startDate, endDate);

        return slots.stream()
                .map(this::mapToSlotResponse)
                .collect(Collectors.toList());
    }

    public DoctorAvailability getSlotById(String slotId) {
        return slotRepository.findById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Slot not found with id: " + slotId));
    }

    private SlotResponse mapToSlotResponse(DoctorAvailability slot) {
        return new SlotResponse()
                .setSlotId(slot.getSlotId())
                .setDoctorId(slot.getDoctor().getDoctorId())
                .setDoctorName(slot.getDoctor().getFirstName())
                .setSlotDate(slot.getSlotDate())
                .setStartTime(slot.getStartTime())
                .setEndTime(slot.getEndTime())
                .setDurationMinutes(slot.getDurationMinutes())
                .setIsAvailable(slot.getIsAvailable());
    }
}
