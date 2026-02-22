package com.clinic.doc_appointment.entity;

import com.clinic.doc_appointment.enums.AppointmentStatus;
import com.clinic.doc_appointment.repository.DoctorAvailabilityRepository;
import jakarta.persistence.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * JPA Entity Listener for Appointment lifecycle events
 * Automatically frees slots when appointments are cancelled or deleted
 */
@Component
@Slf4j
public class AppointmentEntityListener {

    private static DoctorAvailabilityRepository slotRepository;

    @Autowired
    public void setSlotRepository(DoctorAvailabilityRepository repository) {
        AppointmentEntityListener.slotRepository = repository;
    }

    /**
     * Called before appointment is deleted from database
     * Frees the associated slot automatically
     */
    @PreRemove
    public void preRemove(Appointment appointment) {
        log.info("PreRemove: Freeing slot for deleted appointment: {}", 
                appointment.getAppointmentId());
        freeSlot(appointment.getSlot());
    }

    /**
     * Called after appointment is updated
     * Frees slot if status changed to CANCELLED
     */
    @PostUpdate
    public void postUpdate(Appointment appointment) {
        // Check if status changed to CANCELLED
        if (appointment.getPreviousStatus() != AppointmentStatus.CANCELLED &&
            appointment.getStatus() == AppointmentStatus.CANCELLED) {

            log.info("PostUpdate: Status changed to CANCELLED for appointment: {}, freeing slot",
                    appointment.getAppointmentId());

            // ✅ Null-safety: guard against orphaned appointments
            if (appointment.getSlot() != null) {
                freeSlot(appointment.getSlot());
            } else {
                log.warn("Appointment {} has no slot attached — skipping slot freeing", appointment.getAppointmentId());
            }
        }
    }

    /**
     * Frees a slot by marking it as available
     * Includes safety checks to prevent errors
     */
    private void freeSlot(DoctorAvailability slot) {
        if (slot == null) {
            log.warn("Cannot free slot: slot is null");
            return;
        }

        if (slot.getIsAvailable()) {
            log.debug("Slot {} is already available, skipping", slot.getSlotId());
            return;
        }

        try {
            slot.setIsAvailable(true);
            if (slotRepository != null) {
                slotRepository.save(slot);
                log.info("Slot {} freed successfully", slot.getSlotId());
            } else {
                log.error("SlotRepository is null, cannot free slot");
            }
        } catch (Exception e) {
            log.error("Error freeing slot {}: {}", slot.getSlotId(), e.getMessage(), e);
        }
    }
}
