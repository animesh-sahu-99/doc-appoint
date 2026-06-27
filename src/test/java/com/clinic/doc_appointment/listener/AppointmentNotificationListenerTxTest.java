package com.clinic.doc_appointment.listener;

import com.clinic.doc_appointment.event.AppointmentChangedEvent;
import com.clinic.doc_appointment.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Verifies the AFTER_COMMIT semantics of {@link AppointmentNotificationListener}: notifications are
 * delivered only when the surrounding transaction commits, and an event published in a transaction
 * that rolls back is discarded (the retry-rollback fix, DESIGN_PATTERNS.md §6).
 */
@SpringJUnitConfig(AppointmentNotificationListenerTxTest.Config.class)
class AppointmentNotificationListenerTxTest {

    @Configuration
    @EnableTransactionManagement
    static class Config {

        @Bean
        DataSource dataSource() {
            return new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).build();
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        NotificationService notificationService() {
            return Mockito.mock(NotificationService.class);
        }

        @Bean
        AppointmentNotificationListener listener(NotificationService notificationService) {
            return new AppointmentNotificationListener(notificationService);
        }
    }

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private NotificationService notificationService;

    @BeforeEach
    void resetMock() {
        Mockito.reset(notificationService);
    }

    private AppointmentChangedEvent bookedEvent() {
        return new AppointmentChangedEvent(
                AppointmentChangedEvent.Kind.BOOKED,
                "APPOINTMENT-1", "PAT-1", "DOC-1", "Priya", "Sharma",
                LocalDate.of(2026, 1, 1), LocalTime.of(9, 0));
    }

    @Test
    void deliversNotificationsAfterCommit() {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> publisher.publishEvent(bookedEvent()));

        // A BOOKED event notifies both the patient and the doctor.
        verify(notificationService, times(2)).sendNotification(any(), any(), any(), any(), any());
    }

    @Test
    void discardsNotificationsWhenTransactionRollsBack() {
        assertThrows(RuntimeException.class, () ->
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    publisher.publishEvent(bookedEvent());
                    throw new RuntimeException("force rollback");
                }));

        verifyNoInteractions(notificationService);
    }
}
