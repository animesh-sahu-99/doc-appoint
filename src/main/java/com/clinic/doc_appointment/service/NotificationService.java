package com.clinic.doc_appointment.service;

import com.clinic.doc_appointment.dto.response.NotificationResponse;
import com.clinic.doc_appointment.entity.Notification;
import com.clinic.doc_appointment.entity.PushOutboxMessage;
import com.clinic.doc_appointment.entity.UserDevice;
import com.clinic.doc_appointment.enums.NotificationType;
import com.clinic.doc_appointment.exception.ForbiddenOperationException;
import com.clinic.doc_appointment.exception.ResourceNotFoundException;
import com.clinic.doc_appointment.mapper.NotificationMapper;
import com.clinic.doc_appointment.repository.NotificationRepository;
import com.clinic.doc_appointment.repository.PushOutboxRepository;
import com.clinic.doc_appointment.repository.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final PushOutboxRepository pushOutboxRepository;
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
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        if (!notification.getUserId().equals(userId)) {
            throw new ForbiddenOperationException("Unauthorized to access this notification");
        }

        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead(String userId) {
        notificationRepository.markAllAsRead(userId);
    }

    /**
     * Records a notification, announces it over WebSocket, and queues its push.
     *
     * <p>The push is no longer sent here. It is enqueued to {@code push_outbox} in this same
     * transaction and delivered by {@code PushOutboxWorker}, so the HTTP response never blocks on
     * Google and a transient FCM failure is retried instead of vanishing into a log line. Because
     * both rows are written together, a committed notification always has a pending push and a
     * rolled-back one has neither.
     *
     * <p><strong>{@code REQUIRES_NEW} is load-bearing.</strong> The only caller is
     * {@link com.clinic.doc_appointment.listener.AppointmentNotificationListener}, which runs at
     * {@code AFTER_COMMIT}. At that point the outer transaction has committed but its resources
     * are still bound to the thread, so {@code REQUIRED} would silently <em>participate</em> in a
     * completed transaction — no new begin, no commit, and both inserts discarded when the
     * EntityManager closes. {@code REQUIRES_NEW} suspends it and begins a genuinely new one.
     *
     * <p>The WebSocket broadcast stays inline: it is local, in-process and sub-millisecond, so
     * there is nothing to gain by deferring it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendNotification(String userId, String title, String message, NotificationType type, String relatedEntityId) {
        Notification notification = new Notification()
                .setUserId(userId)
                .setTitle(title)
                .setMessage(message)
                .setType(type)
                .setRelatedEntityId(relatedEntityId)
                .setRead(false);

        notification = notificationRepository.save(notification);
        pushOutboxRepository.save(PushOutboxMessage.pendingFor(notification, Instant.now()));
        log.info("Saved Notification and queued push for user {}: {}", userId, title);

        // Broadcast to WebSocket queue: /user/{userId}/queue/notifications
        NotificationResponse response = notificationMapper.toResponse(notification);
        messagingTemplate.convertAndSendToUser(userId, "/queue/notifications", response);
        log.info("Broadcasted Notification to STOMP over WebSocket for user {}", userId);
    }
}
