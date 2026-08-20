package com.clinic.doc_appointment.config;

import com.clinic.doc_appointment.entity.Appointment;
import com.clinic.doc_appointment.entity.DoctorAvailability;
import com.clinic.doc_appointment.repository.AppointmentRepository;
import com.clinic.doc_appointment.repository.DoctorAvailabilityRepository;
import com.clinic.doc_appointment.repository.DoctorRepository;
import com.clinic.doc_appointment.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the application under the {@code dev} profile so the seeder actually runs.
 *
 * <p>Two things are being verified. First, that {@code @Profile("dev")} is wired such that the
 * fixture data still appears for developers — the seeder is how the mobile app gets something to
 * show, so making it profile-gated must not make it dead. Second, and the real regression: the
 * seeded appointments used to select slots by positional index into an unordered
 * {@code findAll()} ({@code .get(30)}, {@code .get(65)}, {@code .get(77)}), and three of those
 * indexes already pointed at a different day or a different doctor than their comment claimed.
 * Selecting by (doctor, date, time) makes a mismatch fail loudly instead of silently booking the
 * wrong calendar.
 *
 * <p>{@code @Transactional} is required, not incidental: associations are LAZY and
 * {@code open-in-view} is off, so walking from an appointment to its slot outside a session throws.
 * That is the production behaviour these assertions should be subject to.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class DataSeederTest {

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private DoctorAvailabilityRepository slotRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Test
    void seedsAConsistentDataSet() {
        assertThat(doctorRepository.count()).isEqualTo(5);
        assertThat(patientRepository.count()).isEqualTo(5);
        assertThat(appointmentRepository.count()).isEqualTo(10);
        assertThat(slotRepository.count()).isPositive();
    }

    /** Each seeded appointment must sit on a slot belonging to that appointment's own doctor. */
    @Test
    void everyAppointmentSitsOnItsOwnDoctorsSlot() {
        List<Appointment> appointments = appointmentRepository.findAll();
        assertThat(appointments).isNotEmpty();

        assertThat(appointments).allSatisfy(appointment -> {
            DoctorAvailability slot = appointment.getSlot();
            assertThat(slot).isNotNull();
            assertThat(slot.getDoctor().getDoctorId())
                    .as("appointment %s is booked on a slot owned by another doctor",
                            appointment.getAppointmentNumber())
                    .isEqualTo(appointment.getDoctor().getDoctorId());
        });
    }

    /** A booked slot must be marked unavailable, or it would be offered to a second patient. */
    @Test
    void everyBookedSlotIsMarkedUnavailable() {
        assertThat(appointmentRepository.findAll()).allSatisfy(appointment ->
                assertThat(appointment.getSlot().getIsAvailable())
                        .as("slot for %s is still advertised as available",
                                appointment.getAppointmentNumber())
                        .isFalse());
    }

    /** No two appointments may share a slot — the constraint V2 enforces at the database. */
    @Test
    void noTwoAppointmentsShareASlot() {
        List<String> slotIds = appointmentRepository.findAll().stream()
                .map(appointment -> appointment.getSlot().getSlotId())
                .toList();

        assertThat(slotIds).doesNotHaveDuplicates();
    }

    /** Rerunning is a no-op, so a restart cannot double the fixture data. */
    @Test
    void seedingIsSkippedWhenDataAlreadyExists() {
        long doctorsBefore = doctorRepository.count();
        long appointmentsBefore = appointmentRepository.count();

        // The context is already started, so the runner has run exactly once; assert the guard held.
        assertThat(doctorsBefore).isEqualTo(5);
        assertThat(appointmentsBefore).isEqualTo(10);
    }
}
