package com.example.apiwatch.repository;

import com.example.apiwatch.entity.Alert;
import com.example.apiwatch.enums.AlertDeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

    Page<Alert> findByEndpoint_IdAndEndpoint_Owner_Id(
            UUID endpointId,
            UUID ownerId,
            Pageable pageable
    );

    List<Alert>
    findTop50ByDeliveryStatusOrderByCreatedAtAscIdAsc(
            AlertDeliveryStatus deliveryStatus
    );
}