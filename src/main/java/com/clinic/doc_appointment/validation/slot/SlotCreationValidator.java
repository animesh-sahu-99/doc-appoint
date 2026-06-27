package com.clinic.doc_appointment.validation.slot;

import com.clinic.doc_appointment.dto.request.CreateSlotRequest;
import com.clinic.doc_appointment.validation.Validator;

/**
 * Marker for validators that guard single-slot creation. {@code SlotService} injects all beans of
 * this type (ordered via {@link org.springframework.core.annotation.Order}) and runs them as a
 * {@link com.clinic.doc_appointment.validation.CompositeValidator}.
 */
public interface SlotCreationValidator extends Validator<CreateSlotRequest> {
}
