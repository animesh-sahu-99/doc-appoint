package com.clinic.doc_appointment.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NameUtilsTest {

    @Test
    void joinsFirstAndLastWithSingleSpace() {
        assertEquals("John Doe", NameUtils.fullName("John", "Doe"));
    }

    @Test
    void omitsNullLastName() {
        assertEquals("John", NameUtils.fullName("John", null));
    }

    @Test
    void preservesTrailingSpaceForEmptyLastName() {
        // Locks in the historic behavior: a non-null but empty last name still appends a space.
        assertEquals("John ", NameUtils.fullName("John", ""));
    }
}
