package com.clinic.doc_appointment.service.push;

/** Provider-agnostic push payload carried across the {@link PushNotificationProvider} port. */
public record PushMessage(String title, String body, String type, String relatedEntityId) {
}
