package com.clinic.doc_appointment.service;

import com.clinic.doc_appointment.entity.Appointment;
import com.clinic.doc_appointment.entity.Doctor;
import com.clinic.doc_appointment.entity.DoctorAvailability;
import com.clinic.doc_appointment.entity.Patient;
import com.clinic.doc_appointment.enums.AppointmentStatus;
import com.clinic.doc_appointment.enums.Role;
import com.clinic.doc_appointment.exception.BookingConflictException;
import com.clinic.doc_appointment.exception.ForbiddenOperationException;
import com.clinic.doc_appointment.repository.AppointmentRepository;
import com.clinic.doc_appointment.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exhausted optimistic-lock retries must surface as a 409-mapped {@link BookingConflictException}.
 *
 * <p>Runs against the real Spring context on purpose. The bug this guards lived entirely in the
 * proxy: {@code @Recover} is resolved per bean, and Spring Retry's unnamed search picks the closest
 * <em>exception</em> match without checking that the parameters are compatible — parameters only
 * break a tie at equal distance. With a single {@code @Recover} taking a
 * {@code BookAppointmentRequest}, every one of these methods was routed into it and invoked
 * reflectively with an appointment id, so the caller received
 * {@code 400 "argument type mismatch"} instead of a conflict. A plain Mockito unit test cannot see
 * that, because it never goes through the proxy.
 */
@SpringBootTest
class AppointmentServiceRetryTest {

    private static final String DOCTOR_ID = "DOC-1";
    private static final String PATIENT_ID = "PAT-1";
    private static final String APPOINTMENT_ID = "APT-1";

    @Autowired
    private AppointmentService appointmentService;

    @MockitoBean
    private AppointmentRepository appointmentRepository;

    private final UserPrincipal doctor =
            new UserPrincipal(DOCTOR_ID, "doc@clinic.com", "hash", Role.DOCTOR.authority());

    @BeforeEach
    void setUp() {
        when(appointmentRepository.findById(APPOINTMENT_ID))
                .thenAnswer(call -> Optional.of(pendingAppointment()));
    }

    private Appointment pendingAppointment() {
        Doctor doc = new Doctor().setDoctorId(DOCTOR_ID).setFirstName("Anjali").setLastName("Sharma");
        Patient patient = new Patient().setPatientId(PATIENT_ID).setFirstName("Rahul").setLastName("Verma")
                .setCountryCode("+91").setPhoneNumber("9000000000");
        DoctorAvailability slot = new DoctorAvailability()
                .setSlotId("SLOT-1")
                .setDoctor(doc)
                .setSlotDate(LocalDate.now().plusDays(1))
                .setStartTime(LocalTime.of(10, 0))
                .setEndTime(LocalTime.of(10, 30))
                .setDurationMinutes(30)
                .setIsAvailable(false);
        return new Appointment()
                .setAppointmentId(APPOINTMENT_ID)
                .setAppointmentNumber("APT20260819ABCDEF")
                .setDoctor(doc)
                .setPatient(patient)
                .setSlot(slot)
                .setStatus(AppointmentStatus.PENDING);
    }

    @Test
    void confirmReportsAConflictOnceRetriesAreExhausted() {
        when(appointmentRepository.save(any(Appointment.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Appointment.class, APPOINTMENT_ID));

        assertThatThrownBy(() -> appointmentService.confirmAppointment(APPOINTMENT_ID, doctor))
                .isInstanceOf(BookingConflictException.class)
                .hasMessageContaining("changed by someone else");

        // maxAttempts = 3 means the original call plus two retries.
        verify(appointmentRepository, times(3)).save(any(Appointment.class));
    }

    /**
     * {@code updateAppointmentNotes} has a third parameter, so it needs its own recovery signature.
     * It is the case a single shared {@code @Recover} could never have satisfied.
     */
    @Test
    void updatingNotesReportsAConflictOnceRetriesAreExhausted() {
        when(appointmentRepository.save(any(Appointment.class)))
                .thenThrow(new OptimisticLockingFailureException("row changed"));

        assertThatThrownBy(() ->
                appointmentService.updateAppointmentNotes(APPOINTMENT_ID, "ECG normal", doctor))
                .isInstanceOf(BookingConflictException.class)
                .hasMessageContaining("changed by someone else");
    }

    @Test
    void noShowReportsAConflictOnceRetriesAreExhausted() {
        when(appointmentRepository.save(any(Appointment.class)))
                .thenThrow(new OptimisticLockingFailureException("row changed"));

        assertThatThrownBy(() -> appointmentService.markNoShow(APPOINTMENT_ID, doctor))
                .isInstanceOf(BookingConflictException.class);
    }

    /** A transient conflict must still succeed on a later attempt — retry, not just recovery. */
    @Test
    void confirmSucceedsWhenAnEarlierAttemptConflicts() {
        when(appointmentRepository.save(any(Appointment.class)))
                .thenThrow(new OptimisticLockingFailureException("first attempt lost the race"))
                .thenAnswer(call -> call.getArgument(0));

        var response = appointmentService.confirmAppointment(APPOINTMENT_ID, doctor);

        assertThat(response.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        verify(appointmentRepository, times(2)).save(any(Appointment.class));
    }

    /** The access guard runs before anything is written, and is not something retry can bypass. */
    @Test
    void anotherDoctorCannotConfirmThisAppointment() {
        UserPrincipal otherDoctor =
                new UserPrincipal("DOC-2", "other@clinic.com", "hash", Role.DOCTOR.authority());

        assertThatThrownBy(() -> appointmentService.confirmAppointment(APPOINTMENT_ID, otherDoctor))
                .isInstanceOf(ForbiddenOperationException.class);

        verify(appointmentRepository, times(0)).save(any(Appointment.class));
    }

    @Test
    void aPatientCannotReadAnotherPatientsAppointment() {
        UserPrincipal strangerPatient =
                new UserPrincipal("PAT-9", "other@x.com", "hash", Role.PATIENT.authority());

        assertThatThrownBy(() -> appointmentService.getAppointmentById(APPOINTMENT_ID, strangerPatient))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void theOwningPatientCanReadTheirOwnAppointment() {
        UserPrincipal owner =
                new UserPrincipal(PATIENT_ID, "rahul@x.com", "hash", Role.PATIENT.authority());

        assertThat(appointmentService.getAppointmentById(APPOINTMENT_ID, owner).getAppointmentId())
                .isEqualTo(APPOINTMENT_ID);
    }
}
