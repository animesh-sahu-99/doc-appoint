package com.clinic.doc_appointment.entity;

import com.clinic.doc_appointment.enums.RevocationReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import java.time.Instant;

/**
 * One issued refresh token. The raw token is never stored — only its SHA-256 hash — so a
 * database leak yields no usable credential.
 *
 * <p><strong>Families.</strong> Rows sharing a {@code familyId} form one device session: each
 * rotation revokes the current row and inserts a successor carrying the same family. Replaying
 * an already-consumed token therefore identifies a compromised session, and the whole family
 * can be revoked at once.
 *
 * <p><strong>Two expiries.</strong> {@code expiresAt} slides forward on every rotation, so an
 * active user is never interrupted; {@code absoluteExpiresAt} is fixed when the family is
 * created and copied unchanged through every rotation, capping how long a session can be
 * extended before a real re-login is required.
 *
 * <p><strong>Why {@link Instant} and not {@code LocalDateTime}</strong> (which the rest of this
 * codebase uses): the build pins {@code -Duser.timezone=Asia/Kolkata} in both the Spring Boot
 * and Surefire plugins because zone handling here is fragile. A zone or DST shift applied to a
 * zone-less expiry column would silently lengthen or shorten a security token's life. These
 * columns are instants in time, so store them as such — please do not "fix" this back.
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_refresh_family", columnList = "family_id"),
        @Index(name = "idx_refresh_user", columnList = "user_id"),
        @Index(name = "idx_refresh_expires", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private String id;

    /**
     * SHA-256 hex of the raw token. Unique so lookup is a single indexed hit.
     * Deliberately not repeated in {@code @Table(indexes = ...)} — that would emit a second,
     * redundant constraint alongside the one this annotation already creates.
     */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    /** Constant across a rotation chain; identifies one device session. */
    @Column(name = "family_id", nullable = false, length = 64)
    private String familyId;

    /** Owning entity's primary key, e.g. {@code "PAT-<uuid>"} or {@code "DOC-<uuid>"}. */
    @Column(name = "user_id", nullable = false)
    private String userId;

    /** {@code "ROLE_DOCTOR"} / {@code "ROLE_PATIENT"} — resolves which table {@code userId} lives in. */
    @Column(name = "role", nullable = false, length = 20)
    private String role;

    /**
     * Audit and log context only; never used to look the owner up. Email is nullable on
     * {@code Patient} and can change, so {@code userId} + {@code role} is the identity here.
     */
    @Column(name = "user_email")
    private String userEmail;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    /** Sliding expiry: {@code min(now + refresh TTL, absoluteExpiresAt)} on each rotation. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Hard cap for the family; set once at creation and copied unchanged through rotations. */
    @Column(name = "absolute_expires_at", nullable = false)
    private Instant absoluteExpiresAt;

    /** Null while the token is live. */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoked_reason", length = 32)
    private RevocationReason revokedReason;

    @Column(name = "device_type", length = 20)
    private String deviceType;

    @Column(name = "user_agent", length = 256)
    private String userAgent;

    /** Sized for IPv6. */
    @Column(name = "created_ip", length = 45)
    private String createdIp;

    /** True when the token has been neither revoked nor expired as of {@code now}. */
    public boolean isLiveAt(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
