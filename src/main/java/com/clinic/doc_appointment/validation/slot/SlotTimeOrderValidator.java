package com.clinic.doc_appointment.validation.slot;

import com.clinic.doc_appointment.dto.request.CreateSlotRequest;
import com.clinic.doc_appointment.exception.InvalidSlotTimeException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Rule 2: a slot's end time must be strictly after its start time. */
@Component
@Order(2)
public class SlotTimeOrderValidator implements SlotCreationValidator {

    @Override
    public void validate(CreateSlotRequest request) {
        if (request.getEndTime().isBefore(request.getStartTime()) ||
                request.getEndTime().equals(request.getStartTime())) {
            throw new InvalidSlotTimeException("End time must be after start time");
        }
    }
}
