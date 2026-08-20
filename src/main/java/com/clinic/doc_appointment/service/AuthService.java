package com.clinic.doc_appointment.service;

import com.clinic.doc_appointment.dto.request.DoctorRegistrationRequest;
import com.clinic.doc_appointment.dto.request.LoginRequest;
import com.clinic.doc_appointment.dto.request.PatientRegistrationRequest;
import com.clinic.doc_appointment.dto.response.AuthResponse;
import com.clinic.doc_appointment.enums.RevocationReason;
import com.clinic.doc_appointment.enums.Role;
import com.clinic.doc_appointment.exception.InvalidRefreshTokenException;
import com.clinic.doc_appointment.security.IssuedTokens;
import com.clinic.doc_appointment.security.LoginRateLimiter;
import com.clinic.doc_appointment.security.RefreshPrincipalResolver;
import com.clinic.doc_appointment.security.RefreshRateLimiter;
import com.clinic.doc_appointment.security.RefreshTokenService;
import com.clinic.doc_appointment.security.TokenContext;
import com.clinic.doc_appointment.security.TokenIssuer;
import com.clinic.doc_appointment.security.UserPrincipal;
import com.clinic.doc_appointment.service.registration.DoctorRegistrationService;
import com.clinic.doc_appointment.service.registration.PatientRegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

/**
 * Authentication facade. Registration is delegated to role-specific
 * {@link com.clinic.doc_appointment.service.registration.AbstractRegistrationService} subclasses
 * (Template Method); login lives here because its flow genuinely differs from registration.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final TokenIssuer tokenIssuer;
    private final RefreshTokenService refreshTokenService;
    private final RefreshPrincipalResolver refreshPrincipalResolver;
    private final AuthenticationManager authenticationManager;
    private final DoctorRegistrationService doctorRegistrationService;
    private final PatientRegistrationService patientRegistrationService;
    private final LoginRateLimiter loginRateLimiter;
    private final RefreshRateLimiter refreshRateLimiter;

    /**
     * Authenticate through the brute-force guard: reject blocked IPs up front, count a
     * failed attempt on bad credentials, and reset the IP's counter on success.
     */
    private Authentication authenticate(String email, String password, String clientIp) {
        loginRateLimiter.assertNotBlocked(clientIp);
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password));
            loginRateLimiter.recordSuccess(clientIp);
            return authentication;
        } catch (AuthenticationException ex) {
            loginRateLimiter.recordFailure(clientIp);
            throw ex;
        }
    }

    /**
     * The whole login flow, parameterised by the role the endpoint is for.
     *
     * <p>This was two methods differing only in a {@link Role}, a repository and a message — the
     * branch-on-type smell, spread across methods instead of an {@code if}. A third role would have
     * meant a third copy.
     *
     * <p>The display name comes from {@link RefreshPrincipalResolver}, which already maps
     * {@code (id, role)} to a principal plus a name in one lookup and is what the refresh path uses.
     * Reusing it also removes a redundant query: the previous {@code findByEmail} re-read the row
     * that {@code authenticationManager.authenticate} had just loaded via
     * {@link com.clinic.doc_appointment.security.CustomUserDetailsService}.
     */
    private AuthResponse login(LoginRequest request, TokenContext context, Role expectedRole) {
        log.info("{} login attempt: {}", expectedRole, request.getEmail());

        Authentication authentication = authenticate(request.getEmail(), request.getPassword(), context.ip());

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        // A patient's credentials must not open a doctor session, and vice-versa. The message stays
        // deliberately vague, matching the generic 401 a wrong password produces.
        if (!expectedRole.authority().equals(principal.getRole())) {
            throw new BadCredentialsException("Invalid credentials for this login");
        }

        IssuedTokens tokens = tokenIssuer.issueNewSession(principal, context);

        // Unreachable-not-found in practice: authentication just proved the row exists. If it
        // somehow raced a deletion, the resolver's exception is also a 401, so the client still sees
        // a coherent answer rather than a 500.
        String displayName = refreshPrincipalResolver.resolve(principal.getId(), expectedRole).displayName();

        return tokens.decorate(AuthResponse.builder())
                .role(expectedRole.authority())
                .userId(principal.getId())
                .email(principal.getEmail())
                .name(displayName)
                .build();
    }

    // ===================== DOCTOR AUTH =====================

    public AuthResponse registerDoctor(DoctorRegistrationRequest request, TokenContext context) {
        log.info("Registering doctor with email: {}", request.getEmail());
        return doctorRegistrationService.register(request, context);
    }

    public AuthResponse loginDoctor(LoginRequest request, TokenContext context) {
        return login(request, context, Role.DOCTOR);
    }

    // ===================== PATIENT AUTH =====================

    public AuthResponse registerPatient(PatientRegistrationRequest request, TokenContext context) {
        log.info("Registering patient with email: {}", request.getEmail());
        return patientRegistrationService.register(request, context);
    }

    public AuthResponse loginPatient(LoginRequest request, TokenContext context) {
        return login(request, context, Role.PATIENT);
    }

    // ===================== SESSION =====================

    /**
     * Exchanges a refresh token for a new access token and a rotated refresh token.
     *
     * <p>Failures feed the per-IP guard, so a client grinding through guesses is throttled while
     * a working client — whose refreshes succeed — never accumulates a count.
     */
    public AuthResponse refresh(String rawRefreshToken, TokenContext context) {
        refreshRateLimiter.assertNotBlocked(context.ip());

        RefreshTokenService.RotationResult rotation;
        try {
            rotation = refreshTokenService.rotate(rawRefreshToken, context);
        } catch (InvalidRefreshTokenException ex) {
            refreshRateLimiter.recordFailure(context.ip());
            throw ex;
        }
        refreshRateLimiter.recordSuccess(context.ip());

        RefreshPrincipalResolver.ResolvedUser user = rotation.user();
        UserPrincipal principal = user.principal();
        IssuedTokens tokens = tokenIssuer.pairWith(principal, rotation.refreshToken());

        return tokens.decorate(AuthResponse.builder())
                .role(principal.getRole())
                .userId(principal.getId())
                .email(principal.getEmail())
                .name(user.displayName())
                .build();
    }

    /** Ends the device session the refresh token belongs to. Idempotent. */
    public void logout(String rawRefreshToken) {
        refreshTokenService.revokeFamilyOf(rawRefreshToken, RevocationReason.LOGOUT);
    }

    /**
     * Ends every session the token's owner holds.
     *
     * <p>Deliberately requires only the refresh token, not a live access token: possession is
     * the proof, and demanding a valid access token would make "log out everywhere" impossible
     * from precisely the situation where it matters most — a session already gone bad.
     */
    public void logoutAllDevices(String rawRefreshToken) {
        refreshTokenService.revokeAllForUserOf(rawRefreshToken, RevocationReason.LOGOUT_ALL);
    }
}
