package com.clinic.doc_appointment.security;

import com.clinic.doc_appointment.entity.Appointment;
import com.clinic.doc_appointment.entity.Doctor;
import com.clinic.doc_appointment.entity.Patient;
import com.clinic.doc_appointment.enums.Role;
import com.clinic.doc_appointment.exception.ForbiddenOperationException;
import com.clinic.doc_appointment.repository.AppointmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The ownership rules that stand between a signed-in user and someone else's data.
 *
 * <p>{@code SecurityConfig} ends at {@code anyRequest().authenticated()}, so for anything without a
 * declarative role rule these guards <em>are</em> the authorization. Both fail closed.
 */
class AccessGuardTest {

    private static final String DOCTOR_ID = "DOC-1";
    private static final String PATIENT_ID = "PAT-1";

    private static UserPrincipal doctor(String id) {
        return new UserPrincipal(id, id + "@clinic.test", "hash", Role.DOCTOR.authority());
    }

    private static UserPrincipal patient(String id) {
        return new UserPrincipal(id, id + "@mail.test", "hash", Role.PATIENT.authority());
    }

    @Nested
    class SelfAccess {

        private final SelfAccessGuard guard = new SelfAccessGuard();

        @Test
        void aDoctorMayActOnTheirOwnRecord() {
            assertThatCode(() -> guard.assertDoctorSelf(doctor(DOCTOR_ID), DOCTOR_ID))
                    .doesNotThrowAnyException();
        }

        @Test
        void aDoctorMayNotActOnAnotherDoctorsRecord() {
            assertThatThrownBy(() -> guard.assertDoctorSelf(doctor("DOC-2"), DOCTOR_ID))
                    .isInstanceOf(ForbiddenOperationException.class);
        }

        /**
         * The role is checked as well as the id. Doctor and patient ids come from different tables,
         * so an id match alone would not prove the caller is the right kind of user.
         */
        @Test
        void aPatientMayNotUseADoctorRule() {
            assertThatThrownBy(() -> guard.assertDoctorSelf(patient(DOCTOR_ID), DOCTOR_ID))
                    .isInstanceOf(ForbiddenOperationException.class);
        }

        @Test
        void aPatientMayActOnTheirOwnRecord() {
            assertThatCode(() -> guard.assertPatientSelf(patient(PATIENT_ID), PATIENT_ID))
                    .doesNotThrowAnyException();
        }

        @Test
        void aPatientMayNotActOnAnotherPatientsRecord() {
            assertThatThrownBy(() -> guard.assertPatientSelf(patient("PAT-9"), PATIENT_ID))
                    .isInstanceOf(ForbiddenOperationException.class);
        }

        @Test
        void anAbsentPrincipalIsDenied() {
            assertThatThrownBy(() -> guard.assertPatientSelf(null, PATIENT_ID))
                    .isInstanceOf(ForbiddenOperationException.class);
        }
    }

    @Nested
    class AppointmentAccess {

        private AppointmentRepository appointmentRepository;
        private AppointmentAccessGuard guard;
        private Appointment appointment;

        @BeforeEach
        void setUp() {
            appointmentRepository = mock(AppointmentRepository.class);
            guard = new AppointmentAccessGuard(appointmentRepository);
            appointment = new Appointment()
                    .setAppointmentId("APT-1")
                    .setDoctor(new Doctor().setDoctorId(DOCTOR_ID))
                    .setPatient(new Patient().setPatientId(PATIENT_ID));
        }

        @Test
        void theOwningPatientMayViewTheAppointment() {
            assertThatCode(() -> guard.assertCanViewAppointment(patient(PATIENT_ID), appointment))
                    .doesNotThrowAnyException();
        }

        @Test
        void theOwningDoctorMayViewTheAppointment() {
            assertThatCode(() -> guard.assertCanViewAppointment(doctor(DOCTOR_ID), appointment))
                    .doesNotThrowAnyException();
        }

        /**
         * The gap that made appointment numbers worth guessing: the response carries clinical notes
         * and the patient's phone number.
         */
        @Test
        void anUnrelatedPatientMayNotViewTheAppointment() {
            assertThatThrownBy(() -> guard.assertCanViewAppointment(patient("PAT-9"), appointment))
                    .isInstanceOf(ForbiddenOperationException.class);
        }

        @Test
        void anUnrelatedDoctorMayNotViewTheAppointment() {
            assertThatThrownBy(() -> guard.assertCanViewAppointment(doctor("DOC-2"), appointment))
                    .isInstanceOf(ForbiddenOperationException.class);
        }

        @Test
        void onlyTheOwningPatientMayPayForTheAppointment() {
            assertThatCode(() -> guard.assertOwnsAppointmentAsPatient(patient(PATIENT_ID), appointment))
                    .doesNotThrowAnyException();

            assertThatThrownBy(() -> guard.assertOwnsAppointmentAsPatient(patient("PAT-9"), appointment))
                    .isInstanceOf(ForbiddenOperationException.class);

            // Even the appointment's own doctor is not the payer.
            assertThatThrownBy(() -> guard.assertOwnsAppointmentAsPatient(doctor(DOCTOR_ID), appointment))
                    .isInstanceOf(ForbiddenOperationException.class);
        }

        @Test
        void aPatientMayReadTheirOwnHistoryOnly() {
            assertThatCode(() -> guard.assertCanViewPatientHistory(patient(PATIENT_ID), PATIENT_ID))
                    .doesNotThrowAnyException();

            assertThatThrownBy(() -> guard.assertCanViewPatientHistory(patient(PATIENT_ID), "PAT-9"))
                    .isInstanceOf(ForbiddenOperationException.class);
        }

        /** This is what lets the doctor's appointment-detail screen show who it is treating. */
        @Test
        void aDoctorWithAnAppointmentMayReadThatPatientsProfile() {
            when(appointmentRepository.existsByDoctorDoctorIdAndPatientPatientId(DOCTOR_ID, PATIENT_ID))
                    .thenReturn(true);

            assertThatCode(() -> guard.assertCanViewPatientHistory(doctor(DOCTOR_ID), PATIENT_ID))
                    .doesNotThrowAnyException();
        }

        @Test
        void aDoctorWithNoAppointmentMayNotReadThatPatientsProfile() {
            when(appointmentRepository.existsByDoctorDoctorIdAndPatientPatientId("DOC-2", PATIENT_ID))
                    .thenReturn(false);

            assertThatThrownBy(() -> guard.assertCanViewPatientHistory(doctor("DOC-2"), PATIENT_ID))
                    .isInstanceOf(ForbiddenOperationException.class);
        }

        @Test
        void aDoctorMayOnlyReadTheirOwnSchedule() {
            assertThatCode(() -> guard.assertCanViewDoctorSchedule(doctor(DOCTOR_ID), DOCTOR_ID))
                    .doesNotThrowAnyException();

            assertThatThrownBy(() -> guard.assertCanViewDoctorSchedule(doctor("DOC-2"), DOCTOR_ID))
                    .isInstanceOf(ForbiddenOperationException.class);

            assertThatThrownBy(() -> guard.assertCanViewDoctorSchedule(patient(PATIENT_ID), DOCTOR_ID))
                    .isInstanceOf(ForbiddenOperationException.class);
        }
    }
}
