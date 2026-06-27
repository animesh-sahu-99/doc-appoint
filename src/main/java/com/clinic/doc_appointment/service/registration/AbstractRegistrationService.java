package com.clinic.doc_appointment.service.registration;

import com.clinic.doc_appointment.dto.response.AuthResponse;
import com.clinic.doc_appointment.security.JwtService;
import com.clinic.doc_appointment.security.UserPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Template Method for the doctor/patient registration flow. The skeleton is fixed —
 * check duplicates → build entity → persist → issue JWT → build response — while subclasses supply
 * the role-specific steps. This removes the near-duplicate registration logic that previously lived
 * in {@code AuthService}.
 *
 * <p>{@code register} is intentionally non-final so Spring's transactional CGLIB proxy can advise it.
 *
 * @param <R> registration request type
 * @param <E> persisted entity type
 */
public abstract class AbstractRegistrationService<R, E> {

    protected final PasswordEncoder passwordEncoder;
    protected final JwtService jwtService;

    protected AbstractRegistrationService(PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(R request) {
        validateDuplicates(request);
        E entity = buildEntity(request);
        E saved = persist(entity);
        UserPrincipal principal = toPrincipal(saved);
        String token = jwtService.generateToken(principal);
        return toAuthResponse(saved, token);
    }

    /** Reject duplicate email/phone per the role's rules (throws DuplicateResourceException). */
    protected abstract void validateDuplicates(R request);

    /** Build the entity from the request, encoding the password. */
    protected abstract E buildEntity(R request);

    /** Persist via the role's repository. */
    protected abstract E persist(E entity);

    /** Build the security principal (with the role's authority) for token issuance. */
    protected abstract UserPrincipal toPrincipal(E saved);

    /** Build the auth response (token + role + identity) returned to the client. */
    protected abstract AuthResponse toAuthResponse(E saved, String token);
}
