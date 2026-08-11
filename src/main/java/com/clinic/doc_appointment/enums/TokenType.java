package com.clinic.doc_appointment.enums;

/**
 * Discriminates the kind of credential a token represents.
 *
 * <p>Only {@link #ACCESS} is ever stamped into a JWT (as the {@code typ} payload claim).
 * Refresh tokens are deliberately <em>opaque random strings</em>, not JWTs, so the two kinds
 * are separated by format rather than by a claim someone might forget to check:
 * an access JWT posted to {@code /api/auth/refresh} hashes to no stored row, and an opaque
 * refresh token sent as a {@code Bearer} credential fails JWT parsing outright.
 *
 * <p>{@link #REFRESH} exists so the distinction is nameable in code and so the claim check
 * remains correct if refresh tokens ever become JWTs.
 */
public enum TokenType {
    ACCESS("access"),
    REFRESH("refresh");

    private final String claimValue;

    TokenType(String claimValue) {
        this.claimValue = claimValue;
    }

    /** Value written to / read from the JWT {@code typ} payload claim. */
    public String getClaimValue() {
        return claimValue;
    }
}
