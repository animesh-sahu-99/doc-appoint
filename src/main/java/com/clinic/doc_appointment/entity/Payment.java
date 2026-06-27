// entity/Payment.java
package com.clinic.doc_appointment.entity;

import com.clinic.doc_appointment.enums.PaymentMethod;
import com.clinic.doc_appointment.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.clinic.doc_appointment.enums.IdPrefix;
import com.clinic.doc_appointment.util.IdGenerator;

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
            paymentId = IdGenerator.withPrefix(IdPrefix.PAYMENT);
        }
    }

    @JsonBackReference
    @OneToOne
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    private String transactionId;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status = PaymentStatus.PENDING;

    @CreationTimestamp
    private LocalDateTime paymentDate;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}