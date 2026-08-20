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
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The access-control fixes, driven through the real filter chain against a real database.
 *
 * <p>The repository had no web-layer test at all, which is why these holes survived: nothing
 * exercised {@code @PreAuthorize}, and nothing checked what happened when a signed-in user put
 * somebody else's id in a path. Every case below returned 200 before this change — a full patient
 * PII read, an arbitrary profile overwrite, an arbitrary account deletion, and the ability to
 * fabricate or wipe another doctor's calendar.
 *
 * <p>It doubles as the smoke test for {@code open-in-view: false}: these are complete request/
 * response cycles, so a lazy association touched during response rendering would fail here.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String patientToken;
    private String patientId;
    private String otherPatientId;
    private String doctorId;
    private String otherDoctorId;

    /** Phone-number range reserved for this test class. */
    private static final String PHONE_PREFIX = "99";

    private static final AtomicLong SEQUENCE = new AtomicLong(1);

    @BeforeEach
    void signEveryoneUp() throws Exception {
        long unique = SEQUENCE.getAndIncrement();

        JsonNode patient = registerPatient("patient-a-" + unique, phone(1, unique));
        patientToken = patient.get("token").asText();
        patientId = patient.get("userId").asText();

        otherPatientId = registerPatient("patient-b-" + unique, phone(2, unique)).get("userId").asText();
        doctorId = registerDoctor("doctor-a-" + unique, phone(3, unique)).get("userId").asText();
        otherDoctorId = registerDoctor("doctor-b-" + unique, phone(4, unique)).get("userId").asText();
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

    private JsonNode registerPatient(String tag, String phone) throws Exception {
        String body = """
                {"name":"%s","email":"%s@patient.test","countryCode":"+91","phoneNumber":"%s",
                 "password":"password123","gender":"MALE","dateOfBirth":"1990-01-01","address":"Somewhere"}
                """.formatted(tag, tag, phone);
        return dataOf(postJson("/api/auth/patient/register", body));
    }

    private JsonNode registerDoctor(String tag, String phone) throws Exception {
        String body = """
                {"name":"%s","email":"%s@doctor.test","countryCode":"+91","phoneNumber":"%s",
                 "password":"password123","specialization":"Cardiologist","qualification":"MBBS",
                 "experienceYears":5,"consultationFee":500.00,"about":"Bio"}
                """.formatted(tag, tag, phone);
        return dataOf(postJson("/api/auth/doctor/register", body));
    }

    /** Performs a setup request, surfacing the response body if it did not succeed. */
    private JsonNode dataOf(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus())
                .as("setup request failed: %s", result.getResponse().getContentAsString())
                .isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private static MockHttpServletRequestBuilder postJson(String url, String body) {
        return post(url).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String bearer() {
        return "Bearer " + patientToken;
    }

    // ===================== patient records =====================

    @Test
    void aPatientCanReadTheirOwnProfile() throws Exception {
        mockMvc.perform(get("/api/patients/{id}", patientId).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.patientId").value(patientId));
    }

    @Test
    void aPatientCannotReadAnotherPatientsProfile() throws Exception {
        mockMvc.perform(get("/api/patients/{id}", otherPatientId).header("Authorization", bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void aPatientCannotOverwriteAnotherPatientsProfile() throws Exception {
        mockMvc.perform(put("/api/patients/{id}", otherPatientId)
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Hijacked\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aPatientCannotDeleteAnotherPatient() throws Exception {
        mockMvc.perform(delete("/api/patients/{id}", otherPatientId).header("Authorization", bearer()))
                .andExpect(status().isForbidden());
    }

    /** The full-PII dump and the phone-number lookup are gone entirely. */
    @Test
    void thereIsNoEndpointThatListsEveryPatient() throws Exception {
        mockMvc.perform(get("/api/patients").header("Authorization", bearer()))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(get("/api/patients/phone")
                        .header("Authorization", bearer())
                        .param("countryCode", "+91")
                        .param("phoneNumber", "9876543210"))
                .andExpect(status().is4xxClientError());
    }

    // ===================== doctor records =====================

    @Test
    void aPatientCannotRewriteADoctorsFee() throws Exception {
        mockMvc.perform(put("/api/doctors/{id}", doctorId)
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consultationFee\":1.00}"))
                .andExpect(status().isForbidden());
    }

    /** Discovery stays open to any authenticated user — patients have to browse to book. */
    @Test
    void aPatientCanStillBrowseDoctors() throws Exception {
        mockMvc.perform(get("/api/doctors").header("Authorization", bearer()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/doctors/{id}", doctorId).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.doctorId").value(doctorId));
    }

    /**
     * The duplicate registration route is gone. It required a token — so while it existed, any
     * signed-in patient could create doctor accounts through it.
     */
    @Test
    void theDuplicateDoctorRegistrationRouteIsGone() throws Exception {
        mockMvc.perform(postJson("/api/doctors/register", "{}")
                        .header("Authorization", bearer()))
                .andExpect(status().is4xxClientError());
    }

    // ===================== calendars =====================

    @Test
    void aPatientCannotCreateSlotsOnADoctorsCalendar() throws Exception {
        String body = """
                {"doctorId":"%s","slotDate":"%s","startTime":"09:00:00","endTime":"09:30:00","durationMinutes":30}
                """.formatted(doctorId, LocalDate.now().plusDays(1));

        mockMvc.perform(postJson("/api/slots", body)
                        .header("Authorization", bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void aPatientCannotWipeADoctorsDay() throws Exception {
        mockMvc.perform(delete("/api/slots/doctor/{id}/date/{date}", doctorId, LocalDate.now().plusDays(1))
                        .header("Authorization", bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void oneDoctorCannotWipeAnothersDay() throws Exception {
        String doctorToken = tokenForDoctorLogin();

        mockMvc.perform(delete("/api/slots/doctor/{id}/date/{date}", otherDoctorId, LocalDate.now().plusDays(1))
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
    }

    private String tokenForDoctorLogin() throws Exception {
        // The doctor registered in setUp; log in to get a fresh token for that account.
        MvcResult doctors = mockMvc.perform(get("/api/doctors/{id}", doctorId).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn();
        String email = objectMapper.readTree(doctors.getResponse().getContentAsString())
                .get("data").get("email").asText();

        JsonNode login = dataOf(postJson("/api/auth/doctor/login",
                "{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)));
        assertThat(login.get("role").asText()).isEqualTo("ROLE_DOCTOR");
        return login.get("token").asText();
    }

    // ===================== unauthenticated =====================

    @Test
    void protectedEndpointsRejectAnAbsentToken() throws Exception {
        mockMvc.perform(get("/api/patients/{id}", patientId)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/doctors")).andExpect(status().isUnauthorized());
    }

    // ===================== validation =====================

    /** {@code DoctorUpdateRequest} previously carried no constraints at all. */
    @Test
    void aNegativeConsultationFeeIsRejectedOnTheUpdatePath() throws Exception {
        String doctorToken = tokenForDoctorLogin();

        mockMvc.perform(put("/api/doctors/{id}", doctorId)
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consultationFee\":-100.00,\"experienceYears\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    /** An unbounded page size went straight into {@code PageRequest.of}. */
    @Test
    void anAbsurdPageSizeIsRejected() throws Exception {
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", bearer())
                        .param("size", "1000000"))
                .andExpect(status().isBadRequest());
    }

    /** An unparseable path variable is a client error, not a server fault. */
    @Test
    void anUnparseableDateReportsBadRequestNotServerError() throws Exception {
        mockMvc.perform(get("/api/slots/doctor/{id}/date/{date}", doctorId, "not-a-date")
                        .header("Authorization", bearer()))
                .andExpect(status().isBadRequest());
    }
}
