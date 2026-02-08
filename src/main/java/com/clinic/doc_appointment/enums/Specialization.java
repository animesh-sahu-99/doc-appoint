package com.clinic.doc_appointment.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

public enum Specialization {

    GENERAL_PRACTITIONER("General Practitioner", "Primary care and general health"),
    CARDIOLOGIST("Cardiologist", "Heart and cardiovascular system"),
    DERMATOLOGIST("Dermatologist", "Skin, hair, and nails"),
    NEUROLOGIST("Neurologist", "Brain and nervous system"),
    ORTHOPEDIC_SURGEON("Orthopedic Surgeon", "Bones, joints, and muscles"),
    PEDIATRICIAN("Pediatrician", "Infants, children, and adolescents"),
    PSYCHIATRIST("Psychiatrist", "Mental health and behavioral disorders"),
    RADIOLOGIST("Radiologist", "Medical imaging and diagnosis"),
    ONCOLOGIST("Oncologist", "Cancer treatment"),
    GYNECOLOGIST("Gynecologist", "Female reproductive system"),
    OPHTHALMOLOGIST("Ophthalmologist", "Eyes and vision"),
    ENT_SPECIALIST("ENT Specialist", "Ear, nose, and throat"),
    ANESTHESIOLOGIST("Anesthesiologist", "Anesthesia and pain management"),
    ENDOCRINOLOGIST("Endocrinologist", "Hormones and metabolism"),
    GASTROENTEROLOGIST("Gastroenterologist", "Digestive system"),
    UROLOGIST("Urologist", "Urinary tract and male reproductive system"),
    NEPHROLOGIST("Nephrologist", "Kidneys"),
    PULMONOLOGIST("Pulmonologist", "Lungs and respiratory system"),
    DENTIST("Dentist", "Teeth and oral health"),
    PHYSIOTHERAPIST("Physiotherapist", "Physical rehabilitation");

    private final String displayName;
    @Getter
    private final String description;

    Specialization(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    @JsonValue  // This ensures JSON serialization uses displayName
    public String getDisplayName() {
        return displayName;
    }

    // Find enum by display name
    public static Specialization fromDisplayName(String displayName) {
        for (Specialization spec : values()) {
            if (spec.displayName.equalsIgnoreCase(displayName)) {
                return spec;
            }
        }
        throw new IllegalArgumentException("Unknown specialization: " + displayName);
    }
}