package com.clinic.doc_appointment.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAuthEntryPointTest {

    private static final AuthenticationException AUTH_EXCEPTION =
            new InsufficientAuthenticationException("Full authentication is required to access this resource");

    private JwtAuthEntryPoint entryPoint;
    private ObjectMapper objectMapper;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        // Mirrors Spring Boot's JacksonAutoConfiguration: JavaTimeModule registered and
        // timestamps written as ISO strings rather than numeric arrays.
        objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        entryPoint = new JwtAuthEntryPoint(objectMapper);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    private JsonNode body() throws Exception {
        return objectMapper.readTree(response.getContentAsString());
    }

    @Test
    void expiredTokenSignalsThatRefreshingWillHelp() throws Exception {
        request.setAttribute(JwtAuthEntryPoint.JWT_ERROR_ATTR, JwtAuthEntryPoint.ERROR_EXPIRED);

        entryPoint.commence(request, response, AUTH_EXCEPTION);

        assertEquals(401, response.getStatus());
        assertEquals("true", response.getHeader(JwtAuthEntryPoint.TOKEN_EXPIRED_HEADER));
        assertEquals("Access token expired", body().get("message").asText());
        assertFalse(body().get("success").asBoolean());
    }

    @Test
    void invalidTokenOmitsTheRefreshHint() throws Exception {
        request.setAttribute(JwtAuthEntryPoint.JWT_ERROR_ATTR, JwtAuthEntryPoint.ERROR_INVALID);

        entryPoint.commence(request, response, AUTH_EXCEPTION);

        assertNull(response.getHeader(JwtAuthEntryPoint.TOKEN_EXPIRED_HEADER),
                "refreshing cannot fix a malformed token — the client must not be told to try");
        assertEquals("Unauthorized", body().get("message").asText());
    }

    @Test
    void missingTokenOmitsTheRefreshHint() throws Exception {
        entryPoint.commence(request, response, AUTH_EXCEPTION);

        assertNull(response.getHeader(JwtAuthEntryPoint.TOKEN_EXPIRED_HEADER));
        assertEquals("Unauthorized", body().get("message").asText());
    }

    @Test
    void doesNotLeakTheFrameworkExceptionMessage() throws Exception {
        entryPoint.commence(request, response, AUTH_EXCEPTION);

        assertFalse(response.getContentAsString().contains("Full authentication is required"),
                "internal detail belongs in the log, not the response");
    }

    /**
     * Regression test for injecting Boot's ObjectMapper instead of {@code new ObjectMapper()}:
     * a hand-built mapper registers no modules, so the LocalDateTime timestamp came out shaped
     * unlike every other error response in the API.
     */
    @Test
    void timestampSerializesAsAnIsoStringLikeEveryOtherErrorResponse() throws Exception {
        entryPoint.commence(request, response, AUTH_EXCEPTION);

        JsonNode timestamp = body().get("timestamp");
        assertTrue(timestamp.isTextual(),
                "expected an ISO-8601 string, got: " + timestamp.getNodeType() + " " + timestamp);
        assertDoesNotThrow(() -> LocalDateTime.parse(timestamp.asText()));
    }

    @Test
    void respondsAsJsonInUtf8() throws Exception {
        entryPoint.commence(request, response, AUTH_EXCEPTION);

        assertTrue(response.getContentType().startsWith("application/json"));
        assertEquals("UTF-8", response.getCharacterEncoding());
    }
}
