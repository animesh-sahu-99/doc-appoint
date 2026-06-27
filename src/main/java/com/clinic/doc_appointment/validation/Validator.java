package com.clinic.doc_appointment.validation;

/**
 * A single validation rule. Implementations throw a domain exception when {@code target} is invalid.
 *
 * @param <T> the type being validated
 */
public interface Validator<T> {
    void validate(T target);
}
