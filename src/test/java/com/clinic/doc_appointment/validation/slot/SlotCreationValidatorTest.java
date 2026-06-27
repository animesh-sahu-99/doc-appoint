package com.clinic.doc_appointment.validation.slot;

import com.clinic.doc_appointment.dto.request.CreateSlotRequest;
import com.clinic.doc_appointment.exception.InvalidSlotDateException;
import com.clinic.doc_appointment.exception.InvalidSlotTimeException;
import com.clinic.doc_appointment.validation.CompositeValidator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SlotCreationValidatorTest {

    private final SlotDateNotInPastValidator dateValidator = new SlotDateNotInPastValidator();
    private final SlotTimeOrderValidator timeValidator = new SlotTimeOrderValidator();

    private CreateSlotRequest request(LocalDate date, LocalTime start, LocalTime end) {
        CreateSlotRequest r = new CreateSlotRequest();
        r.setDoctorId("DOC-1");
        r.setSlotDate(date);
        r.setStartTime(start);
        r.setEndTime(end);
        r.setDurationMinutes(30);
        return r;
    }

    @Test
    void rejectsPastDate() {
        CreateSlotRequest r = request(LocalDate.now().minusDays(1), LocalTime.of(9, 0), LocalTime.of(9, 30));
        assertThrows(InvalidSlotDateException.class, () -> dateValidator.validate(r));
    }

    @Test
    void rejectsEndTimeNotAfterStart() {
        CreateSlotRequest r = request(LocalDate.now().plusDays(1), LocalTime.of(9, 30), LocalTime.of(9, 0));
        assertThrows(InvalidSlotTimeException.class, () -> timeValidator.validate(r));
    }

    @Test
    void acceptsValidRequest() {
        CreateSlotRequest r = request(LocalDate.now().plusDays(1), LocalTime.of(9, 0), LocalTime.of(9, 30));
        assertDoesNotThrow(() -> dateValidator.validate(r));
        assertDoesNotThrow(() -> timeValidator.validate(r));
    }

    @Test
    void compositeShortCircuitsOnFirstFailingRuleInOrder() {
        // Past date AND bad time order — the date rule (registered first) must win.
        CreateSlotRequest r = request(LocalDate.now().minusDays(1), LocalTime.of(9, 30), LocalTime.of(9, 0));
        List<SlotCreationValidator> rules = List.of(dateValidator, timeValidator);
        CompositeValidator<CreateSlotRequest> composite = new CompositeValidator<>(rules);
        assertThrows(InvalidSlotDateException.class, () -> composite.validate(r));
    }
}
