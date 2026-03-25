package com.clinic.doc_appointment.repository;

import com.clinic.doc_appointment.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, String> {
    List<UserDevice> findByUserIdAndIsActiveTrue(String userId);
    Optional<UserDevice> findByFcmToken(String fcmToken);

    @Modifying
    @Query("UPDATE UserDevice d SET d.isActive = false WHERE d.userId = :userId AND d.fcmToken != :excludeToken")
    void deactivateOldTokensForUser(@Param("userId") String userId, @Param("excludeToken") String excludeToken);
}
