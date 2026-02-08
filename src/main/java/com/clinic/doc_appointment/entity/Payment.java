// entity/Payment.java
package com.clinic.doc_appointment.entity;

import com.clinic.doc_appointment.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class Payment {

    @Id
    private String paymentId;

    @PrePersist
    public void generateId(){
        if(paymentId == null){
            paymentId = "PAYMENT-"+ UUID.randomUUID();
        }
    }

    @OneToOne
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    private String paymentMethod;  // CARD, UPI, NET_BANKING, etc.

    private String transactionId;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status = PaymentStatus.PENDING;

    @CreationTimestamp
    private LocalDateTime paymentDate;
}