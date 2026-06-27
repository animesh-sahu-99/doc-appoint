package com.clinic.doc_appointment.util;

import com.clinic.doc_appointment.exception.ResourceNotFoundException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * DRY helper for the "load by id or throw {@link ResourceNotFoundException}" idiom
 * that was repeated across the service layer.
 *
 * <p>The not-found message is supplied by the caller so the existing, entity-specific
 * messages (e.g. {@code "Doctor not found with id: ..."}) are preserved verbatim.
 */
public final class EntityFinder {

    private EntityFinder() {
        // utility class — no instances
    }

    /** Finds an entity by id or throws {@link ResourceNotFoundException} with the given message. */
    public static <T, ID> T findOrThrow(JpaRepository<T, ID> repository, ID id, String notFoundMessage) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(notFoundMessage));
    }

    /** Unwraps an {@link Optional} or throws {@link ResourceNotFoundException} with the given message. */
    public static <T> T orThrow(Optional<T> optional, String notFoundMessage) {
        return optional.orElseThrow(() -> new ResourceNotFoundException(notFoundMessage));
    }
}
