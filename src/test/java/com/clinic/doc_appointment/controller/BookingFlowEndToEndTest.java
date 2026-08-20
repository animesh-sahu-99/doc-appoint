package com.clinic.doc_appointment.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole booking lifecycle over HTTP: slots, booking, confirmation, completion, review.
 *
 * <p>Its job is to catch what the unit tests structurally cannot. Associations are now LAZY and
 * {@code spring.jpa.open-in-view} is off, so anything the response needs must be fetched inside the
 * service transaction — by an {@code @EntityGraph} on the query, or by the batched review lookup in
 * {@code AppointmentMapper.toResponseList}. A miss there is not a subtle regression; it is a
 * {@code LazyInitializationException} on the screens the mobile app opens most.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BookingFlowEndToEndTest {

    /** Phone-number range reserved for this test class. */
    private static final String PHONE_PREFIX = "77";

    private static final AtomicLong SEQUENCE = new AtomicLong(1);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String patientToken;
    private String patientId;
    private String doctorToken;
    private String doctorId;

    @BeforeEach
    void signUpAndOpenACalendar() throws Exception {
        long unique = SEQUENCE.getAndIncrement();

        JsonNode patient = dataOf(postJson("/api/auth/patient/register", """
                {"name":"Rahul %1$d","email":"patient%1$d@flow.test","countryCode":"+91",
                 "phoneNumber":"%2$s","password":"password123","gender":"MALE",
                 "dateOfBirth":"1990-01-01","address":"Somewhere"}
                """.formatted(unique, phone(1, unique))));
        patientToken = patient.get("token").asText();
        patientId = patient.get("userId").asText();

        JsonNode doctor = dataOf(postJson("/api/auth/doctor/register", """
                {"name":"Anjali %1$d","email":"doctor%1$d@flow.test","countryCode":"+91",
                 "phoneNumber":"%2$s","password":"password123","specialization":"Cardiologist",
                 "qualification":"MBBS, MD","experienceYears":12,"consultationFee":900.00,"about":"Bio"}
                """.formatted(unique, phone(2, unique))));
        doctorToken = doctor.get("token").asText();
        doctorId = doctor.get("userId").asText();
    }

    /**
     * A ten-digit number inside this class's own range.
     *
     * <p>The test database is shared across test classes and phone numbers are unique per table, so
     * two classes generating from the same range collide and registration returns 409. The prefix is
     * what keeps them apart.
     */
    private static String phone(int who, long unique) {
        return PHONE_PREFIX + String.format("%01d%07d", who, unique % 10_000_000L);
    }

    private static MockHttpServletRequestBuilder postJson(String url, String body) {
        return post(url).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private JsonNode dataOf(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus())
                .as("request failed: %s", result.getResponse().getContentAsString())
                .isIn(200, 201);
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private String asDoctor() {
        return "Bearer " + doctorToken;
    }

    private String asPatient() {
        return "Bearer " + patientToken;
    }

    /** Creates a day of slots on the doctor's own calendar and returns the first slot id. */
    private String openSlots(LocalDate date) throws Exception {
        JsonNode slots = dataOf(postJson("/api/slots/bulk", """
                {"doctorId":"%s","slotDate":"%s","dayStartTime":"09:00:00","dayEndTime":"12:00:00",
                 "slotDurationMinutes":30,"breakDurationMinutes":0}
                """.formatted(doctorId, date)).header("Authorization", asDoctor()));

        assertThat(slots).hasSize(6);   // 09:00-12:00 at 30 minutes
        return slots.get(0).get("slotId").asText();
    }

    private String book(String slotId) throws Exception {
        return dataOf(postJson("/api/appointments/book", """
                {"patientId":"%s","slotId":"%s","reasonForVisit":"Chest pain"}
                """.formatted(patientId, slotId)).header("Authorization", asPatient()))
                .get("appointmentId").asText();
    }

    @Test
    void aPatientBooksAndBothSidesCanSeeTheAppointment() throws Exception {
        String slotId = openSlots(LocalDate.now().plusDays(1));
        String appointmentId = book(slotId);

        // The patient's list: exercises the entity graph plus the batched review lookup.
        mockMvc.perform(get("/api/appointments/patient/{id}", patientId).header("Authorization", asPatient()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].appointmentId").value(appointmentId))
                .andExpect(jsonPath("$.data[0].doctorName").isNotEmpty())
                .andExpect(jsonPath("$.data[0].patientName").isNotEmpty())
                .andExpect(jsonPath("$.data[0].startTime").isNotEmpty());

        mockMvc.perform(get("/api/appointments/patient/{id}/upcoming", patientId)
                        .header("Authorization", asPatient()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].appointmentId").value(appointmentId));

        mockMvc.perform(get("/api/appointments/doctor/{id}", doctorId).header("Authorization", asDoctor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].appointmentId").value(appointmentId));

        // Single-appointment read, the notification deep-link path, for both parties.
        mockMvc.perform(get("/api/appointments/{id}", appointmentId).header("Authorization", asPatient()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/appointments/{id}", appointmentId).header("Authorization", asDoctor()))
                .andExpect(status().isOk());
    }

    /** Booking a slot takes it out of circulation. */
    @Test
    void aBookedSlotIsNoLongerOffered() throws Exception {
        LocalDate date = LocalDate.now().plusDays(1);
        String slotId = openSlots(date);
        book(slotId);

        MvcResult available = mockMvc.perform(
                        get("/api/slots/doctor/{id}/date/{date}", doctorId, date)
                                .header("Authorization", asPatient()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode slots = objectMapper.readTree(available.getResponse().getContentAsString()).get("data");
        assertThat(slots).hasSize(5);
        assertThat(slots.toString()).doesNotContain(slotId);
    }

    /** Booking the same slot twice must conflict, not succeed twice. */
    @Test
    void thesameSlotCannotBeBookedTwice() throws Exception {
        String slotId = openSlots(LocalDate.now().plusDays(1));
        book(slotId);

        mockMvc.perform(postJson("/api/appointments/book", """
                        {"patientId":"%s","slotId":"%s","reasonForVisit":"Again"}
                        """.formatted(patientId, slotId)).header("Authorization", asPatient()))
                .andExpect(status().isConflict());
    }

    /** A patient may not book on someone else's behalf. */
    @Test
    void aPatientCannotBookForAnotherPatient() throws Exception {
        String slotId = openSlots(LocalDate.now().plusDays(1));

        mockMvc.perform(postJson("/api/appointments/book", """
                        {"patientId":"PAT-someone-else","slotId":"%s","reasonForVisit":"Nope"}
                        """.formatted(slotId)).header("Authorization", asPatient()))
                .andExpect(status().isForbidden());
    }

    /**
     * Confirm, complete, review. The review path is what recomputes the doctor's rating with a single
     * atomic statement, and the completed appointment is the one whose list rendering attaches a
     * review — the case the batched lookup exists for.
     */
    @Test
    void theFullLifecycleThroughToAReviewAndRating() throws Exception {
        String slotId = openSlots(LocalDate.now().plusDays(1));
        String appointmentId = book(slotId);

        mockMvc.perform(put("/api/appointments/{id}/confirm", appointmentId).header("Authorization", asDoctor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        mockMvc.perform(put("/api/appointments/{id}/notes", appointmentId)
                        .header("Authorization", asDoctor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"ECG normal. Advised stress test.\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/appointments/{id}/complete", appointmentId).header("Authorization", asDoctor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        dataOf(postJson("/api/reviews", """
                {"appointmentId":"%s","rating":4,"comment":"Attentive and thorough"}
                """.formatted(appointmentId)).header("Authorization", asPatient()));

        // The rating is recomputed from the review rows, and rounded only for display.
        mockMvc.perform(get("/api/doctors/{id}", doctorId).header("Authorization", asPatient()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalReviews").value(1))
                .andExpect(jsonPath("$.data.averageRating").value(4.0));

        // A completed appointment carries its review through the batched list path.
        mockMvc.perform(get("/api/appointments/patient/{id}", patientId).header("Authorization", asPatient()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].rating").value(4))
                .andExpect(jsonPath("$.data[0].comment").value("Attentive and thorough"))
                .andExpect(jsonPath("$.data[0].notes").value("ECG normal. Advised stress test."));

        mockMvc.perform(get("/api/reviews/doctor/{id}", doctorId)
                        .header("Authorization", asPatient())
                        .param("sort", "recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].rating").value(4))
                .andExpect(jsonPath("$.data.content[0].patientName").isNotEmpty());
    }

    /** A slot whose start time has passed must not be offered, and must not be bookable. */
    @Test
    void slotsInThePastAreNeitherOfferedNorBookable() throws Exception {
        LocalDate today = LocalDate.now();

        // A 00:00-00:30 window is only in the past once the clock is past it. Reported as skipped
        // rather than silently returning, so a run between midnight and 00:30 does not look like a pass.
        assumeTrue(LocalTime.now().isAfter(LocalTime.of(0, 30)),
                "needs the clock to be past 00:30 for a same-day past slot to exist");

        dataOf(postJson("/api/slots/bulk", """
                {"doctorId":"%s","slotDate":"%s","dayStartTime":"00:00:00","dayEndTime":"00:30:00",
                 "slotDurationMinutes":30,"breakDurationMinutes":0}
                """.formatted(doctorId, today)).header("Authorization", asDoctor()));

        // Bookable listing excludes it...
        mockMvc.perform(get("/api/slots/doctor/{id}/date/{date}", doctorId, today)
                        .header("Authorization", asPatient()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        // ...while the doctor's own schedule view still shows it.
        mockMvc.perform(get("/api/slots/doctor/{id}/date/{date}/all", doctorId, today)
                        .header("Authorization", asDoctor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].startTime").isNotEmpty());
    }

    /** A bulk range where every candidate clashes is a conflict, not "0 slots created". */
    @Test
    void resubmittingTheSameBulkRangeConflicts() throws Exception {
        LocalDate date = LocalDate.now().plusDays(2);
        openSlots(date);

        mockMvc.perform(postJson("/api/slots/bulk", """
                        {"doctorId":"%s","slotDate":"%s","dayStartTime":"09:00:00","dayEndTime":"12:00:00",
                         "slotDurationMinutes":30,"breakDurationMinutes":0}
                        """.formatted(doctorId, date)).header("Authorization", asDoctor()))
                .andExpect(status().isConflict());
    }

    /** A day ending at 23:59 used to pin the request thread in an endless loop. */
    @Test
    void aDayEndingJustBeforeMidnightCompletes() throws Exception {
        JsonNode slots = dataOf(postJson("/api/slots/bulk", """
                {"doctorId":"%s","slotDate":"%s","dayStartTime":"09:00:00","dayEndTime":"23:59:00",
                 "slotDurationMinutes":60,"breakDurationMinutes":0}
                """.formatted(doctorId, LocalDate.now().plusDays(3))).header("Authorization", asDoctor()));

        assertThat(slots).hasSize(14);
    }
}
