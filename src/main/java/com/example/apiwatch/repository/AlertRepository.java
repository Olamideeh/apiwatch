package com.example.apiwatch.repository;

import com.example.apiwatch.entity.Alert;
import com.example.apiwatch.enums.AlertDeliveryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

    Page<Alert> findByEndpoint_IdAndEndpoint_Owner_Id(
            UUID endpointId,
            UUID ownerId,
            Pageable pageable
    );

    List<Alert> findTop50ByDeliveryStatusOrderByCreatedAtAscIdAsc(
            AlertDeliveryStatus deliveryStatus
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select alert
            from Alert alert
            where alert.id = :alertId
            """)
    Optional<Alert> findByIdForUpdate(
            @Param("alertId") UUID alertId
    );

    @Query("""
            select alert
            from Alert alert
            where alert.deliveryStatus in :statuses
              and alert.attemptCount < 3
              and alert.nextAttemptAt <= :now
              and (
                  alert.deliveryLeaseUntil is null
                  or alert.deliveryLeaseUntil <= :now
              )
            order by alert.createdAt asc, alert.id asc
            """)
    List<Alert> findDueAlerts(
            @Param("statuses")
            Collection<AlertDeliveryStatus> statuses,
            @Param("now") Instant now,
            Pageable pageable
    );
}