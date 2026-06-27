package com.clinic.doc_appointment.validation.slot;

import com.clinic.doc_appointment.dto.request.CreateSlotRequest;
import com.clinic.doc_appointment.entity.DoctorAvailability;
import com.clinic.doc_appointment.exception.SlotOverlapException;
import com.clinic.doc_appointment.repository.DoctorAvailabilityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/** Rule 3: a new slot must not overlap an existing slot for the same doctor/date. */
@Component
@Order(3)
@RequiredArgsConstructor
public class SlotOverlapValidator implements SlotCreationValidator {

    private final DoctorAvailabilityRepository slotRepository;

    @Override
    public void validate(CreateSlotRequest request) {
        List<DoctorAvailability> overlapping = slotRepository.findOverlappingSlots(
                request.getDoctorId(),
                request.getSlotDate(),
                request.getStartTime(),
                request.getEndTime());

        if (!overlapping.isEmpty()) {
            throw new SlotOverlapException("Slot overlaps with existing slots");
        }
    }
}
