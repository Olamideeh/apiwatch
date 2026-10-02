package com.example.apiwatch.repository;

import com.example.apiwatch.entity.MonitoredEndpoint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MonitoredEndpointRepository
        extends JpaRepository<MonitoredEndpoint, UUID> {

    Optional<MonitoredEndpoint> findByIdAndOwner_Id(
            UUID endpointId,
            UUID ownerId
    );

    Page<MonitoredEndpoint> findByOwner_Id(
            UUID ownerId,
            Pageable pageable
    );

    List<MonitoredEndpoint>
    findTop50ByPausedFalseAndNextCheckAtLessThanEqualOrderByNextCheckAtAscIdAsc(
            Instant now
    );
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select endpoint
        from MonitoredEndpoint endpoint
        where endpoint.id = :endpointId
        """)
    Optional<MonitoredEndpoint> findByIdForUpdate(
            @Param("endpointId") UUID endpointId
    );
    @Query("""
        select endpoint
        from MonitoredEndpoint endpoint
        where endpoint.paused = false
          and endpoint.owner.active = true
          and endpoint.nextCheckAt <= :now
          and (
              endpoint.checkLeaseUntil is null
              or endpoint.checkLeaseUntil <= :now
          )
        order by endpoint.nextCheckAt asc, endpoint.id asc
        """)
    List<MonitoredEndpoint> findDueEndpoints(
            @Param("now") Instant now,
            Pageable pageable
    );
}