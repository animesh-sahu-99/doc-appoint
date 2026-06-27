package com.clinic.doc_appointment.mapper;

import com.clinic.doc_appointment.dto.response.SlotResponse;
import com.clinic.doc_appointment.entity.Doctor;
import com.clinic.doc_appointment.entity.DoctorAvailability;
import com.clinic.doc_appointment.util.NameUtils;
import org.springframework.stereotype.Component;

@Component
public class SlotMapper implements EntityMapper<DoctorAvailability, SlotResponse> {

    @Override
    public SlotResponse toResponse(DoctorAvailability slot) {
        Doctor doctor = slot.getDoctor();
        return SlotResponse.builder()
                .slotId(slot.getSlotId())
                .doctorId(doctor.getDoctorId())
                .doctorName(NameUtils.fullName(doctor.getFirstName(), doctor.getLastName()))
                .slotDate(slot.getSlotDate())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .durationMinutes(slot.getDurationMinutes())
                .isAvailable(slot.getIsAvailable())
                .createdAt(slot.getCreatedAt())
                .build();
    }
}
