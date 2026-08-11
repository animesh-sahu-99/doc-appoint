package com.clinic.doc_appointment.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * The single place tokens are minted.
 *
 * <p>Access tokens were previously generated at three independent sites — both logins and the
 * registration template — each building its own response. Routing all of them through here keeps
 * the pairing of access and refresh tokens in one place, so a new issuance path cannot silently
 * forget to start a refresh session.
 */
@Component
@RequiredArgsConstructor
public class TokenIssuer {

    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    /** Starts a new device session: a fresh access token and a brand-new refresh family. */
    public IssuedTokens issueNewSession(UserPrincipal principal, TokenContext context) {
        RefreshTokenService.IssuedRefreshToken refreshToken =
                refreshTokenService.issueNewFamily(principal, context);
        return pairWith(principal, refreshToken);
    }

    /**
     * Mints an access token to accompany a refresh token that already exists — either just
     * created for a new session, or just rotated by {@code /api/auth/refresh}.
     */
    public IssuedTokens pairWith(UserPrincipal principal,
                                 RefreshTokenService.IssuedRefreshToken refreshToken) {
        String accessToken = jwtService.generateToken(principal);
        long refreshExpiresIn = Duration.between(Instant.now(), refreshToken.expiresAt()).toSeconds();
        return new IssuedTokens(
                accessToken,
                refreshToken.rawToken(),
                jwtService.getAccessExpirationSeconds(),
                Math.max(0, refreshExpiresIn));
    }
}
