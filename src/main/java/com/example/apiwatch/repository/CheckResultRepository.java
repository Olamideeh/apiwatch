package com.example.apiwatch.repository;

import com.example.apiwatch.entity.CheckResult;
import com.example.apiwatch.enums.EndpointStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface CheckResultRepository
        extends JpaRepository<CheckResult, UUID> {

    Page<CheckResult> findByEndpoint_IdAndEndpoint_Owner_Id(
            UUID endpointId,
            UUID ownerId,
            Pageable pageable
    );

    long countByEndpoint_IdAndEndpoint_Owner_IdAndCheckedAtGreaterThanEqualAndCheckedAtLessThan(
            UUID endpointId,
            UUID ownerId,
            Instant from,
            Instant to
    );

    long countByEndpoint_IdAndEndpoint_Owner_IdAndStatusAndCheckedAtGreaterThanEqualAndCheckedAtLessThan(
            UUID endpointId,
            UUID ownerId,
            EndpointStatus status,
            Instant from,
            Instant to
    );
}