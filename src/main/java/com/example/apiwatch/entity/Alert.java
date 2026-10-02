package com.example.apiwatch.entity;

import com.example.apiwatch.enums.AlertDeliveryStatus;
import com.example.apiwatch.enums.AlertType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "alerts",
        indexes = {
                @Index(
                        name = "idx_alert_endpoint_time",
                        columnList = "endpoint_id, created_at"
                ),
                @Index(
                        name = "idx_alert_delivery_status",
                        columnList = "delivery_status"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "endpoint_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_alert_endpoint")
    )
    private MonitoredEndpoint endpoint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private AlertType type;

    @Column(
            name = "recipient_email",
            nullable = false,
            updatable = false,
            length = 200
    )
    private String recipientEmail;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    private AlertDeliveryStatus deliveryStatus =
            AlertDeliveryStatus.PENDING;

    @Builder.Default
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Version
    private Long version;

    @PrePersist
    public void beforeInsert() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }

        if (deliveryStatus == null) {
            deliveryStatus = AlertDeliveryStatus.PENDING;
        }
    }
}