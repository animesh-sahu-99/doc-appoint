package com.clinic.doc_appointment.validation;

import java.util.List;

/**
 * Composite of {@link Validator} rules, run in order. The first failing rule short-circuits by
 * throwing, so adding a new rule is just adding a leaf — never editing existing logic (OCP).
 *
 * @param <T> the type being validated
 */
public class CompositeValidator<T> implements Validator<T> {

    private final List<? extends Validator<T>> rules;

    public CompositeValidator(List<? extends Validator<T>> rules) {
        this.rules = rules;
    }

    @Override
    public void validate(T target) {
        rules.forEach(rule -> rule.validate(target));
    }
}
