package com.clinic.doc_appointment.validation.slot;

import com.clinic.doc_appointment.dto.request.CreateSlotRequest;
import com.clinic.doc_appointment.exception.InvalidSlotDateException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Rule 1: a slot may not be created for a past date. */
@Component
@Order(1)
public class SlotDateNotInPastValidator implements SlotCreationValidator {

    @Override
    public void validate(CreateSlotRequest request) {
        if (request.getSlotDate().isBefore(LocalDate.now())) {
            throw new InvalidSlotDateException("Cannot create slots for past dates");
        }
    }
}
