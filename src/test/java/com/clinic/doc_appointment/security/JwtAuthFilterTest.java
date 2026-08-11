package com.clinic.doc_appointment.security;

import com.clinic.doc_appointment.enums.Role;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The filter must classify <em>why</em> a token failed (so the entry point can tell the client
 * whether refreshing would help) and must always continue the chain — public endpoints still
 * have to be served when a request happens to carry a stale token.
 */
class JwtAuthFilterTest {

    private static final String TOKEN = "some.jwt.token";

    private JwtService jwtService;
    private CustomUserDetailsService userDetailsService;
    private JwtAuthFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        userDetailsService = mock(CustomUserDetailsService.class);
        filter = new JwtAuthFilter(jwtService, userDetailsService);

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);

        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void withBearerToken() {
        request.addHeader("Authorization", "Bearer " + TOKEN);
    }

    private Object errorAttribute() {
        return request.getAttribute(JwtAuthEntryPoint.JWT_ERROR_ATTR);
    }

    private UserPrincipal principal() {
        return new UserPrincipal("PAT-1", "asha@example.com", "hashed", Role.PATIENT.authority());
    }

    @Test
    void validTokenAuthenticatesAndFlagsNoError() throws Exception {
        UserPrincipal user = principal();
        withBearerToken();
        when(jwtService.extractEmail(TOKEN)).thenReturn(user.getEmail());
        when(userDetailsService.loadUserByUsername(user.getEmail())).thenReturn(user);
        when(jwtService.isTokenValid(eq(TOKEN), any())).thenReturn(true);

        filter.doFilter(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(errorAttribute());
        verify(chain).doFilter(request, response);
    }

    @Test
    void expiredTokenIsFlaggedAsRecoverable() throws Exception {
        withBearerToken();
        when(jwtService.extractEmail(TOKEN))
                .thenThrow(new ExpiredJwtException(null, null, "token expired"));

        filter.doFilter(request, response, chain);

        assertEquals(JwtAuthEntryPoint.ERROR_EXPIRED, errorAttribute());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @Test
    void malformedTokenIsFlaggedAsUnrecoverable() throws Exception {
        withBearerToken();
        when(jwtService.extractEmail(TOKEN)).thenThrow(new MalformedJwtException("not a jwt"));

        filter.doFilter(request, response, chain);

        assertEquals(JwtAuthEntryPoint.ERROR_INVALID, errorAttribute());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @Test
    void unknownUserIsFlaggedAsUnrecoverable() throws Exception {
        withBearerToken();
        when(jwtService.extractEmail(TOKEN)).thenReturn("ghost@example.com");
        when(userDetailsService.loadUserByUsername(anyString()))
                .thenThrow(new UsernameNotFoundException("no such user"));

        filter.doFilter(request, response, chain);

        assertEquals(JwtAuthEntryPoint.ERROR_INVALID, errorAttribute());
        verify(chain).doFilter(request, response);
    }

    /** A refresh-typed or wrong-owner token parses fine but must not authenticate. */
    @Test
    void tokenRejectedByValidationIsFlaggedAsUnrecoverable() throws Exception {
        UserPrincipal user = principal();
        withBearerToken();
        when(jwtService.extractEmail(TOKEN)).thenReturn(user.getEmail());
        when(userDetailsService.loadUserByUsername(user.getEmail())).thenReturn(user);
        when(jwtService.isTokenValid(eq(TOKEN), any())).thenReturn(false);

        filter.doFilter(request, response, chain);

        assertEquals(JwtAuthEntryPoint.ERROR_INVALID, errorAttribute());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @Test
    void requestWithoutAuthorizationHeaderIsPassedThroughUntouched() throws Exception {
        filter.doFilter(request, response, chain);

        // Anonymous requests to public endpoints must not be labelled as auth failures.
        assertNull(errorAttribute());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @Test
    void nonBearerAuthorizationHeaderIsPassedThroughUntouched() throws Exception {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        filter.doFilter(request, response, chain);

        assertNull(errorAttribute());
        verify(chain).doFilter(request, response);
    }
}
