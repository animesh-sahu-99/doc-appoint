package com.clinic.doc_appointment.security;

import com.clinic.doc_appointment.util.RequestUtils;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Where a token request came from. Recorded on the refresh-token row for audit, and used for
 * per-IP rate limiting.
 *
 * <p>Only standard headers are read. A bespoke header such as {@code X-Device-Id} would be
 * rejected by the CORS policy in {@code SecurityConfig}, which pins {@code allowedHeaders} to
 * {@code Authorization}, {@code Content-Type} and {@code Accept} — so anything device-specific
 * belongs in the request body instead.
 */
public record TokenContext(String deviceType, String userAgent, String ip) {

    /** Column width of {@code refresh_tokens.user_agent}. */
    private static final int MAX_USER_AGENT_LENGTH = 256;

    /** For call sites with no request in scope (scheduled jobs, tests). */
    public static final TokenContext EMPTY = new TokenContext(null, null, null);

    public static TokenContext from(HttpServletRequest request, boolean trustForwardedFor) {
        if (request == null) {
            return EMPTY;
        }
        return new TokenContext(
                null,
                truncate(request.getHeader("User-Agent")),
                RequestUtils.getClientIp(request, trustForwardedFor));
    }

    /** User-Agent is client-controlled and unbounded; clip it rather than fail the insert. */
    private static String truncate(String userAgent) {
        if (userAgent == null || userAgent.length() <= MAX_USER_AGENT_LENGTH) {
            return userAgent;
        }
        return userAgent.substring(0, MAX_USER_AGENT_LENGTH);
    }
}
