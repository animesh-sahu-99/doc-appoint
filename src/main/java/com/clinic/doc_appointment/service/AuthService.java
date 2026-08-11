package com.clinic.doc_appointment.service;

import com.clinic.doc_appointment.dto.request.DoctorRegistrationRequest;
import com.clinic.doc_appointment.dto.request.LoginRequest;
import com.clinic.doc_appointment.dto.request.PatientRegistrationRequest;
import com.clinic.doc_appointment.dto.response.AuthResponse;
import com.clinic.doc_appointment.entity.Doctor;
import com.clinic.doc_appointment.entity.Patient;
import com.clinic.doc_appointment.enums.RevocationReason;
import com.clinic.doc_appointment.enums.Role;
import com.clinic.doc_appointment.exception.InvalidRefreshTokenException;
import com.clinic.doc_appointment.exception.ResourceNotFoundException;
import com.clinic.doc_appointment.repository.DoctorRepository;
import com.clinic.doc_appointment.repository.PatientRepository;
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

    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final TokenIssuer tokenIssuer;
    private final RefreshTokenService refreshTokenService;
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

    // ===================== DOCTOR AUTH =====================

    public AuthResponse registerDoctor(DoctorRegistrationRequest request, TokenContext context) {
        log.info("Registering doctor with email: {}", request.getEmail());
        return doctorRegistrationService.register(request, context);
    }

    public AuthResponse loginDoctor(LoginRequest request, TokenContext context) {
        log.info("Doctor login attempt: {}", request.getEmail());

        Authentication authentication = authenticate(request.getEmail(), request.getPassword(), context.ip());

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        if (!Role.DOCTOR.authority().equals(principal.getRole())) {
            throw new BadCredentialsException("Invalid credentials for doctor login");
        }

        IssuedTokens tokens = tokenIssuer.issueNewSession(principal, context);

        Doctor doctor = doctorRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found with email: " + request.getEmail()));

        return tokens.decorate(AuthResponse.builder())
                .role(Role.DOCTOR.authority())
                .userId(principal.getId())
                .email(principal.getEmail())
                .name(doctor.getFirstName())
                .build();
    }

    // ===================== PATIENT AUTH =====================

    public AuthResponse registerPatient(PatientRegistrationRequest request, TokenContext context) {
        log.info("Registering patient with email: {}", request.getEmail());
        return patientRegistrationService.register(request, context);
    }

    public AuthResponse loginPatient(LoginRequest request, TokenContext context) {
        log.info("Patient login attempt: {}", request.getEmail());

        Authentication authentication = authenticate(request.getEmail(), request.getPassword(), context.ip());

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        if (!Role.PATIENT.authority().equals(principal.getRole())) {
            throw new BadCredentialsException("Invalid credentials for patient login");
        }

        IssuedTokens tokens = tokenIssuer.issueNewSession(principal, context);

        Patient patient = patientRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found with email: " + request.getEmail()));

        return tokens.decorate(AuthResponse.builder())
                .role(Role.PATIENT.authority())
                .userId(principal.getId())
                .email(principal.getEmail())
                .name(patient.getFirstName())
                .build();
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
