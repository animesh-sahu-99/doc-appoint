package com.clinic.doc_appointment.service;

import com.clinic.doc_appointment.dto.request.BulkSlotRequest;
import com.clinic.doc_appointment.dto.request.CreateSlotRequest;
import com.clinic.doc_appointment.dto.response.SlotResponse;
import com.clinic.doc_appointment.entity.Doctor;
import com.clinic.doc_appointment.entity.DoctorAvailability;
import com.clinic.doc_appointment.enums.Role;
import com.clinic.doc_appointment.enums.Specialization;
import com.clinic.doc_appointment.exception.ForbiddenOperationException;
import com.clinic.doc_appointment.exception.InvalidSlotDateException;
import com.clinic.doc_appointment.exception.SlotOverlapException;
import com.clinic.doc_appointment.mapper.SlotMapper;
import com.clinic.doc_appointment.repository.DoctorAvailabilityRepository;
import com.clinic.doc_appointment.repository.DoctorRepository;
import com.clinic.doc_appointment.security.SelfAccessGuard;
import com.clinic.doc_appointment.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlotServiceTest {

    private static final String DOCTOR_ID = "DOC-1";
    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    private DoctorAvailabilityRepository slotRepository;
    private DoctorRepository doctorRepository;
    private SlotService slotService;

    private final UserPrincipal doctorCaller =
            new UserPrincipal(DOCTOR_ID, "doc@clinic.com", "hash", Role.DOCTOR.authority());

    @BeforeEach
    void setUp() {
        slotRepository = mock(DoctorAvailabilityRepository.class);
        doctorRepository = mock(DoctorRepository.class);

        Doctor doctor = new Doctor().setDoctorId(DOCTOR_ID).setFirstName("Anjali").setLastName("Sharma")
                .setSpecialization(Specialization.CARDIOLOGIST);
        when(doctorRepository.findById(DOCTOR_ID)).thenReturn(Optional.of(doctor));
        when(doctorRepository.existsById(DOCTOR_ID)).thenReturn(true);
        when(slotRepository.saveAll(any())).thenAnswer(call -> call.getArgument(0));

        slotService = new SlotService(slotRepository, doctorRepository, new SlotMapper(),
                new SelfAccessGuard(), List.of());
    }

    private BulkSlotRequest bulkRequest(String dayStart, String dayEnd, int duration, int breakMinutes) {
        BulkSlotRequest request = new BulkSlotRequest();
        request.setDoctorId(DOCTOR_ID);
        request.setSlotDate(TOMORROW);
        request.setDayStartTime(LocalTime.parse(dayStart));
        request.setDayEndTime(LocalTime.parse(dayEnd));
        request.setSlotDurationMinutes(duration);
        request.setBreakDurationMinutes(breakMinutes);
        return request;
    }

    /**
     * The regression that matters most in this class.
     *
     * <p>The previous loop advanced with {@code LocalTime.plusMinutes}, which wraps at midnight. With
     * a day ending at 23:59 it reached 23:00, computed an end of 00:00, found 00:00 is not after
     * 23:59, and walked the whole day again — forever, appending to a list and issuing a query every
     * iteration. Against that code this test never returns, which is exactly the failure a request
     * thread suffered.
     */
    @Test
    @Timeout(10)
    void bulkSlotsTerminateWhenTheDayEndsJustBeforeMidnight() {
        when(slotRepository.findByDoctorDoctorIdAndSlotDate(DOCTOR_ID, TOMORROW)).thenReturn(List.of());

        List<SlotResponse> created = slotService.createBulkSlots(
                bulkRequest("09:00", "23:59", 60, 0), doctorCaller);

        // 09:00 through 23:00 inclusive is 15 one-hour slots; 23:00-00:00 would wrap and is excluded.
        assertThat(created).hasSize(14);
        assertThat(created).allSatisfy(slot ->
                assertThat(slot.getEndTime()).isAfter(slot.getStartTime()));
        assertThat(created.get(created.size() - 1).getEndTime()).isEqualTo(LocalTime.of(23, 0));
    }

    @ParameterizedTest(name = "{0}-{1} at {2}min/{3}min break -> {4} slots")
    @CsvSource({
            "09:00, 17:00, 30,  0, 16",
            "09:00, 17:00, 60,  0,  8",
            "09:00, 17:00, 30, 30,  8",
            "09:00, 12:00, 10,  0, 18",
            "09:00, 09:30, 30,  0,  1",
            "09:00, 09:20, 30,  0,  0",   // range too short for even one slot
    })
    void bulkSlotCountsAreBoundedAndCorrect(String start, String end, int duration, int gap, int expected) {
        when(slotRepository.findByDoctorDoctorIdAndSlotDate(DOCTOR_ID, TOMORROW)).thenReturn(List.of());
        BulkSlotRequest request = bulkRequest(start, end, duration, gap);

        if (expected == 0) {
            assertThatThrownBy(() -> slotService.createBulkSlots(request, doctorCaller))
                    .isInstanceOf(SlotOverlapException.class)
                    .hasMessageContaining("too short");
            return;
        }
        assertThat(slotService.createBulkSlots(request, doctorCaller)).hasSize(expected);
    }

    /** One query for the day, not one per candidate slot. */
    @Test
    void bulkSlotsQueryTheDayExactlyOnce() {
        when(slotRepository.findByDoctorDoctorIdAndSlotDate(DOCTOR_ID, TOMORROW)).thenReturn(List.of());

        slotService.createBulkSlots(bulkRequest("09:00", "17:00", 30, 0), doctorCaller);

        verify(slotRepository).findByDoctorDoctorIdAndSlotDate(DOCTOR_ID, TOMORROW);
        verify(slotRepository, never()).findOverlappingSlots(anyString(), any(), any(), any());
    }

    @Test
    void bulkSlotsSkipCandidatesThatClashButStillCreateTheRest() {
        DoctorAvailability existing = new DoctorAvailability()
                .setSlotDate(TOMORROW)
                .setStartTime(LocalTime.of(9, 0))
                .setEndTime(LocalTime.of(9, 30));
        when(slotRepository.findByDoctorDoctorIdAndSlotDate(DOCTOR_ID, TOMORROW)).thenReturn(List.of(existing));

        List<SlotResponse> created = slotService.createBulkSlots(
                bulkRequest("09:00", "11:00", 30, 0), doctorCaller);

        assertThat(created).hasSize(3);
        assertThat(created).noneSatisfy(slot ->
                assertThat(slot.getStartTime()).isEqualTo(LocalTime.of(9, 0)));
    }

    /**
     * A request where every candidate clashes used to return {@code 201 "0 slots created
     * successfully"} — a success status for an operation that did nothing.
     */
    @Test
    void bulkSlotsReportAConflictWhenEveryCandidateClashes() {
        DoctorAvailability wholeDay = new DoctorAvailability()
                .setSlotDate(TOMORROW)
                .setStartTime(LocalTime.of(8, 0))
                .setEndTime(LocalTime.of(18, 0));
        when(slotRepository.findByDoctorDoctorIdAndSlotDate(DOCTOR_ID, TOMORROW)).thenReturn(List.of(wholeDay));

        assertThatThrownBy(() -> slotService.createBulkSlots(
                bulkRequest("09:00", "17:00", 30, 0), doctorCaller))
                .isInstanceOf(SlotOverlapException.class)
                .hasMessageContaining("overlaps");
    }

    @Test
    void bulkSlotsRejectPastDates() {
        BulkSlotRequest request = bulkRequest("09:00", "17:00", 30, 0);
        request.setSlotDate(LocalDate.now().minusDays(1));

        assertThatThrownBy(() -> slotService.createBulkSlots(request, doctorCaller))
                .isInstanceOf(InvalidSlotDateException.class);
    }

    // ===================== authorization =====================

    @Test
    void aPatientCannotCreateSlotsOnADoctorsCalendar() {
        UserPrincipal patient = new UserPrincipal("PAT-9", "p@x.com", "hash", Role.PATIENT.authority());
        CreateSlotRequest request = new CreateSlotRequest();
        request.setDoctorId(DOCTOR_ID);
        request.setSlotDate(TOMORROW);
        request.setStartTime(LocalTime.of(9, 0));
        request.setEndTime(LocalTime.of(9, 30));
        request.setDurationMinutes(30);

        assertThatThrownBy(() -> slotService.createSlot(request, patient))
                .isInstanceOf(ForbiddenOperationException.class);

        verify(slotRepository, never()).save(any());
    }

    @Test
    void oneDoctorCannotCreateSlotsOnAnothersCalendar() {
        UserPrincipal otherDoctor =
                new UserPrincipal("DOC-2", "other@clinic.com", "hash", Role.DOCTOR.authority());

        assertThatThrownBy(() -> slotService.createBulkSlots(
                bulkRequest("09:00", "17:00", 30, 0), otherDoctor))
                .isInstanceOf(ForbiddenOperationException.class);

        verify(slotRepository, never()).saveAll(any());
    }

    @Test
    void oneDoctorCannotClearAnothersDay() {
        UserPrincipal otherDoctor =
                new UserPrincipal("DOC-2", "other@clinic.com", "hash", Role.DOCTOR.authority());

        assertThatThrownBy(() ->
                slotService.deleteSlotsByDoctorAndDate(DOCTOR_ID, TOMORROW, otherDoctor))
                .isInstanceOf(ForbiddenOperationException.class);

        verify(slotRepository, never()).deleteAll(any());
    }

    /** Ownership for a single-slot delete comes from the slot itself, not from client input. */
    @Test
    void deletingASlotChecksTheSlotsOwnDoctor() {
        Doctor otherDoctor = new Doctor().setDoctorId("DOC-2");
        DoctorAvailability slot = new DoctorAvailability()
                .setSlotId("SLOT-1")
                .setDoctor(otherDoctor)
                .setIsAvailable(true);
        when(slotRepository.findById("SLOT-1")).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> slotService.deleteSlot("SLOT-1", doctorCaller))
                .isInstanceOf(ForbiddenOperationException.class);

        verify(slotRepository, never()).delete(any());
    }
}
