package com.clinic.doc_appointment.service;

import com.clinic.doc_appointment.dto.response.NotificationResponse;
import com.clinic.doc_appointment.entity.Notification;
import com.clinic.doc_appointment.entity.UserDevice;
import com.clinic.doc_appointment.enums.NotificationType;
import com.clinic.doc_appointment.mapper.NotificationMapper;
import com.clinic.doc_appointment.repository.NotificationRepository;
import com.clinic.doc_appointment.service.push.PushMessage;
import com.clinic.doc_appointment.service.push.PushNotificationProvider;
import com.clinic.doc_appointment.repository.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final PushNotificationProvider pushNotificationProvider;
    private final NotificationMapper notificationMapper;

    public Page<NotificationResponse> getUserNotifications(String userId, int page, int size) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
                .map(notificationMapper::toResponse);
    }

    @Transactional
    public void registerDeviceToken(String userId, String fcmToken, String deviceType) {
        // Step 1: Deactivate all previous tokens for this user (handles app reinstall / new tokens)
        userDeviceRepository.deactivateOldTokensForUser(userId, fcmToken);

        // Step 2: Upsert the current token
        userDeviceRepository.findByFcmToken(fcmToken).ifPresentOrElse(
                device -> {
                    if (!device.getUserId().equals(userId)) {
                        device.setUserId(userId);
                        device.setDeviceType(deviceType);
                        device.setActive(true);
                        userDeviceRepository.save(device);
                        log.info("Transferred existing FCM token to user: {}", userId);
                    } else if (!device.isActive()) {
                        device.setActive(true);
                        userDeviceRepository.save(device);
                        log.info("Re-activated FCM token for user: {}", userId);
                    }
                },
                () -> {
                    UserDevice newDevice = UserDevice.builder()
                            .userId(userId)
                            .fcmToken(fcmToken)
                            .deviceType(deviceType)
                            .isActive(true)
                            .build();
                    userDeviceRepository.save(newDevice);
                    log.info("Registered new FCM token for user: {}", userId);
                }
        );
    }

    public int getUnreadCount(String userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAsRead(String notificationId, String userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));

        if (!notification.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized to access this notification");
        }

        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead(String userId) {
        notificationRepository.markAllAsRead(userId);
    }

    @Transactional
    public void sendNotification(String userId, String title, String message, NotificationType type, String relatedEntityId) {
        Notification notification = new Notification()
                .setUserId(userId)
                .setTitle(title)
                .setMessage(message)
                .setType(type)
                .setRelatedEntityId(relatedEntityId)
                .setRead(false);

        notification = notificationRepository.save(notification);
        log.info("Saved Notification for user {}: {}", userId, title);

        // Broadcast to WebSocket queue: /user/{userId}/queue/notifications
        NotificationResponse response = notificationMapper.toResponse(notification);
        messagingTemplate.convertAndSendToUser(userId, "/queue/notifications", response);
        log.info("Broadcasted Notification to STOMP over WebSocket for user {}", userId);

        // Send Push Notification via FCM
        List<String> tokens = userDeviceRepository.findByUserIdAndIsActiveTrue(userId)
                .stream()
                .map(UserDevice::getFcmToken)
                .collect(Collectors.toList());

        if (!tokens.isEmpty()) {
            pushNotificationProvider.send(tokens, new PushMessage(title, message, type.name(), relatedEntityId));
        }
    }
}
