package com.clinic.doc_appointment.service;

import com.clinic.doc_appointment.repository.UserDeviceRepository;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class FcmPushService {

    private final UserDeviceRepository userDeviceRepository;

    @Transactional
    public void sendPushNotificationToTokens(List<String> tokens, String title, String body, String type, String relatedEntityId) {
        if (tokens == null || tokens.isEmpty()) return;

        if (FirebaseApp.getApps().isEmpty()) {
            log.debug("Skip FCM send: Firebase is not initialized");
            return;
        }

        for (String token : tokens) {
            try {
                Message message = Message.builder()
                        .setToken(token)
                        .setNotification(Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build())
                        .setAndroidConfig(AndroidConfig.builder()
                                .setPriority(AndroidConfig.Priority.HIGH)
                                .setNotification(AndroidNotification.builder()
                                        .setSound("default")
                                        .setChannelId("medibook_channel")
                                        .build())
                                .build())
                        .putData("type", type != null ? type : "")
                        .putData("relatedEntityId", relatedEntityId != null ? relatedEntityId : "")
                        .build();

                String response = FirebaseMessaging.getInstance().send(message);
                log.info("Successfully sent FCM push notification: {}", response);

            } catch (FirebaseMessagingException e) {
                // If Firebase tells us the token is no longer valid, deactivate it in the DB
                if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                    log.warn("FCM token is UNREGISTERED — deactivating in database: {}...", token.substring(0, 20));
                    userDeviceRepository.findByFcmToken(token).ifPresent(device -> {
                        device.setActive(false);
                        userDeviceRepository.save(device);
                    });
                } else {
                    log.error("Failed to send FCM push notification to token: {}...", token.substring(0, 20), e);
                }
            } catch (Exception e) {
                log.error("Unexpected error sending FCM push notification", e);
            }
        }
    }
}
