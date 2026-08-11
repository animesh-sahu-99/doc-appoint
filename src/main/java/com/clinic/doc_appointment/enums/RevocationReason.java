package com.clinic.doc_appointment.enums;

/**
 * Why a refresh token row stopped being usable. Persisted as a string
 * ({@code @Enumerated(EnumType.STRING)}), so names are part of the stored data — renaming
 * one orphans existing rows.
 *
 * <p>{@link #ROTATED} is load-bearing for reuse detection: a replayed token is recognised as
 * an attack precisely because its row is still present and marked {@code ROTATED}. That is why
 * revoked rows are retained for a while rather than deleted immediately.
 */
public enum RevocationReason {

    /** Normal consumption — the token was exchanged for a successor in the same family. */
    ROTATED,

    /** Explicit logout of this device session. */
    LOGOUT,

    /** Explicit logout of every session belonging to the user. */
    LOGOUT_ALL,

    /** A consumed token was replayed outside the grace window; the whole family was revoked. */
    REUSE_DETECTED,

    /** The oldest session was evicted because the per-user active-family cap was reached. */
    SUPERSEDED_BY_CAP
}
