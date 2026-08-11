package com.clinic.doc_appointment.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.doc_appointment.dto.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class JwtAuthEntryPoint implements AuthenticationEntryPoint {

    /** Request attribute {@link JwtAuthFilter} uses to classify why authentication failed. */
    public static final String JWT_ERROR_ATTR = "jwt_error";

    /** The access token was well-formed but past its expiry — the client should refresh. */
    public static final String ERROR_EXPIRED = "expired";

    /** Malformed, wrongly signed, wrong token type, or unknown user — refreshing will not help. */
    public static final String ERROR_INVALID = "invalid";

    /**
     * Header telling the client this 401 is recoverable by refreshing rather than by logging out.
     * Without it every 401 looks alike, and a client must either retry unrecoverable failures
     * forever or log users out on ones it could have fixed.
     */
    public static final String TOKEN_EXPIRED_HEADER = "X-Token-Expired";

    /**
     * Injected rather than {@code new ObjectMapper()}: a hand-built mapper registers no
     * modules, so {@link ApiResponse#getTimestamp()} (a {@code LocalDateTime}) would not
     * serialize as the ISO-8601 string every {@code @RestControllerAdvice} response emits.
     * Using Boot's configured bean keeps 401 bodies identical in shape to every other error.
     */
    private final ObjectMapper objectMapper;

    public JwtAuthEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("Unauthorized request to {}: {}", request.getRequestURI(), authException.getMessage());

        boolean expired = ERROR_EXPIRED.equals(request.getAttribute(JWT_ERROR_ATTR));

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        if (expired) {
            response.setHeader(TOKEN_EXPIRED_HEADER, "true");
        }

        // The framework's message is internal detail and of no use to a client, so it is logged
        // above rather than echoed back.
        ApiResponse<Void> apiResponse = ApiResponse.error(
                expired ? "Access token expired" : "Unauthorized");
        objectMapper.writeValue(response.getOutputStream(), apiResponse);
    }
}
