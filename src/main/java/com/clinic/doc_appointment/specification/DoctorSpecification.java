package com.clinic.doc_appointment.specification;

import com.clinic.doc_appointment.dto.request.DoctorFilterRequest;
import com.clinic.doc_appointment.entity.Doctor;
import com.clinic.doc_appointment.enums.Specialization;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA Specification builder for dynamic doctor search and filtering.
 * Each filter criterion is optional — null values are skipped.
 */
public class DoctorSpecification {

    private DoctorSpecification() {}

    public static Specification<Doctor> withFilters(DoctorFilterRequest filters) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Always filter to active doctors only
            predicates.add(cb.isTrue(root.get("isActive")));

            // Name: case-insensitive partial match on firstName
            String name = filters.getName();
            if (name != null && !name.isBlank()) {
                String pattern = "%" + name.trim().toLowerCase() + "%";
                Predicate firstNameMatch = cb.like(cb.lower(root.get("firstName")), pattern);
                Predicate lastNameMatch  = cb.like(cb.lower(root.get("lastName")), pattern);
                predicates.add(cb.or(firstNameMatch, lastNameMatch));
            }

            // Specialization: exact enum match
            Specialization spec = filters.getSpecialization();
            if (spec != null) {
                predicates.add(cb.equal(root.get("specialization"), spec));
            }

            // Fee range
            BigDecimal minFee = filters.getMinFee();
            BigDecimal maxFee = filters.getMaxFee();
            if (minFee != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("consultationFee"), minFee));
            }
            if (maxFee != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("consultationFee"), maxFee));
            }

            // Minimum experience
            Integer minExp = filters.getMinExperience();
            if (minExp != null && minExp > 0) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("experienceYears"), minExp));
            }

            // Available only: doctor must have at least one available slot
            Boolean availableOnly = filters.getAvailableOnly();
            if (Boolean.TRUE.equals(availableOnly)) {
                // Use EXISTS subquery to avoid duplicates from JOIN
                Subquery<Long> sub = query.subquery(Long.class);
                Root<Doctor> subRoot = sub.from(Doctor.class);
                Join<Object, Object> slots = subRoot.join("availabilitySlots");
                sub.select(cb.literal(1L))
                        .where(
                                cb.equal(subRoot.get("doctorId"), root.get("doctorId")),
                                cb.isTrue(slots.get("isAvailable"))
                        );
                predicates.add(cb.exists(sub));
            }

            // Ensure no duplicates from any joins
            query.distinct(true);

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
