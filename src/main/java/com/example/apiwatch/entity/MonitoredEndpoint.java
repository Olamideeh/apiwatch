package com.example.apiwatch.entity;

import com.example.apiwatch.enums.EndpointStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "monitored_endpoints",
        indexes = {
                @Index(
                        name = "idx_endpoint_owner",
                        columnList = "owner_id"
                ),
                @Index(
                        name = "idx_endpoint_schedule",
                        columnList = "paused, next_check_at"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonitoredEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "owner_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_endpoint_owner")
    )
    private User owner;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 2048)
    private String url;

    @Builder.Default
    @Column(name = "expected_status_code", nullable = false)
    private int expectedStatusCode = 200;

    @Builder.Default
    @Column(name = "interval_seconds", nullable = false)
    private int intervalSeconds = 60;

    @Builder.Default
    @Column(name = "timeout_millis", nullable = false)
    private int timeoutMillis = 5000;

    @Builder.Default
    @Column(name = "response_time_limit_millis", nullable = false)
    private int responseTimeLimitMillis = 1000;

    @Builder.Default
    @Column(nullable = false)
    private boolean paused = false;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", nullable = false, length = 20)
    private EndpointStatus currentStatus = EndpointStatus.PENDING;

    @Builder.Default
    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures = 0;

    @Builder.Default
    @Column(name = "outage_open", nullable = false)
    private boolean outageOpen = false;

    @Column(name = "next_check_at", nullable = false)
    private Instant nextCheckAt;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @PrePersist
    public void beforeInsert() {
        normalizeValues();

        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;

        if (nextCheckAt == null) {
            nextCheckAt = now;
        }

        if (currentStatus == null) {
            currentStatus = EndpointStatus.PENDING;
        }
    }

    @PreUpdate
    public void beforeUpdate() {
        normalizeValues();
        updatedAt = Instant.now();
    }

    private void normalizeValues() {
        if (name != null) {
            name = name.trim();
        }

        if (url != null) {
            url = url.trim();
        }
    }
}