package com.clinic.doc_appointment.mapper;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps a persistence entity to its response DTO. Centralizes the
 * {@code stream().map(...).collect(toList())} idiom that every service repeated.
 *
 * @param <E> entity type
 * @param <R> response DTO type
 */
public interface EntityMapper<E, R> {

    R toResponse(E entity);

    default List<R> toResponseList(Collection<E> entities) {
        return entities.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
}
