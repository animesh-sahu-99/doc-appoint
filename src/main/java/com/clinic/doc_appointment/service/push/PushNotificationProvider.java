package com.clinic.doc_appointment.service.push;

import java.util.List;

/**
 * Port for delivering push notifications, decoupling the notification domain from any specific
 * push-provider SDK. {@link FcmPushAdapter} adapts Firebase Cloud Messaging to this interface.
 */
public interface PushNotificationProvider {
    void send(List<String> tokens, PushMessage message);
}
