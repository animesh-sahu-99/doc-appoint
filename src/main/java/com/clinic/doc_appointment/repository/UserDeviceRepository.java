package com.clinic.doc_appointment.repository;

import com.clinic.doc_appointment.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, String> {
    List<UserDevice> findByUserIdAndIsActiveTrue(String userId);
    Optional<UserDevice> findByFcmToken(String fcmToken);

    @Modifying
    @Query("UPDATE UserDevice d SET d.isActive = false WHERE d.userId = :userId AND d.fcmToken != :excludeToken")
    void deactivateOldTokensForUser(@Param("userId") String userId, @Param("excludeToken") String excludeToken);

    /**
     * Retires tokens the push provider reported as permanently dead — uninstalled apps, rotated
     * registrations, tokens minted for a different Firebase project.
     *
     * <p>Called by {@code PushOutboxWorker} when recording a send outcome. Only the provider's
     * authoritative "this device is gone" codes reach here; anything ambiguous is retried instead,
     * because wrongly deactivating a token silently ends a real user's notifications for good.
     *
     * <p>{@code lastUpdatedAt} is set explicitly because a bulk update bypasses the entity's
     * {@code @PreUpdate} callback.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE UserDevice d SET d.isActive = false, d.lastUpdatedAt = :now "
            + "WHERE d.fcmToken IN :tokens AND d.isActive = true")
    int deactivateTokens(@Param("tokens") Collection<String> tokens, @Param("now") LocalDateTime now);
}
