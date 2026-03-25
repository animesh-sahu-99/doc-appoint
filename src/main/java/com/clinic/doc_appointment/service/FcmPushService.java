package com.clinic.doc_appointment.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class FcmPushService {

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
                } catch (Exception e) {
                    log.error("Failed to send FCM push notification to token: {}", token, e);
                }
            }
    }
}
