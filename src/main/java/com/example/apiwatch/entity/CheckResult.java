package com.example.apiwatch.entity;

import com.example.apiwatch.enums.EndpointStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "check_results",
        indexes = {
                @Index(
                        name = "idx_check_result_endpoint_time",
                        columnList = "endpoint_id, checked_at"
                )
        }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "endpoint_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_check_result_endpoint")
    )
    private MonitoredEndpoint endpoint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private EndpointStatus status;

    @Column(name = "actual_status_code", updatable = false)
    private Integer actualStatusCode;

    @Column(
            name = "response_time_millis",
            nullable = false,
            updatable = false
    )
    private long responseTimeMillis;

    @Column(name = "error_message", updatable = false, length = 2000)
    private String errorMessage;

    @Column(name = "checked_at", nullable = false, updatable = false)
    private Instant checkedAt;

    @PrePersist
    public void beforeInsert() {
        if (checkedAt == null) {
            checkedAt = Instant.now();
        }
    }
}