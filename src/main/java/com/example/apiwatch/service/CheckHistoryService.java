package com.example.apiwatch.service;

import com.example.apiwatch.dto.CheckResultResponse;
import com.example.apiwatch.dto.PageResponse;
import com.example.apiwatch.dto.UptimeResponse;
import com.example.apiwatch.entity.CheckResult;
import com.example.apiwatch.enums.EndpointStatus;
import com.example.apiwatch.repository.CheckResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckHistoryService {

    private final EndpointManagementService managementService;
    private final CheckResultRepository checkResultRepository;

    @Transactional(readOnly = true)
    public PageResponse<CheckResultResponse> getHistory(
            UUID ownerId,
            UUID endpointId,
            int page,
            int size
    ) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page number cannot be negative"
            );
        }

        if (size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Page size must be between 1 and 100"
            );
        }

        managementService.getEndpoint(ownerId, endpointId);

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("checkedAt"),
                        Sort.Order.desc("id")
                )
        );

        Page<CheckResult> results = checkResultRepository
                .findByEndpoint_IdAndEndpoint_Owner_Id(
                        endpointId,
                        ownerId,
                        pageable
                );

        return new PageResponse<>(
                results.getContent()
                        .stream()
                        .map(this::toResponse)
                        .toList(),
                results.getNumber(),
                results.getSize(),
                results.getTotalElements(),
                results.getTotalPages(),
                results.isLast()
        );
    }

    @Transactional(
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ
    )
    public UptimeResponse getUptime(
            UUID ownerId,
            UUID endpointId,
            Instant from,
            Instant to
    ) {
        if (from == null || to == null) {
            throw new IllegalArgumentException(
                    "Both from and to timestamps are required"
            );
        }

        if (!from.isBefore(to)) {
            throw new IllegalArgumentException(
                    "From timestamp must be before to timestamp"
            );
        }

        managementService.getEndpoint(ownerId, endpointId);

        long total = checkResultRepository
                .countByEndpoint_IdAndEndpoint_Owner_IdAndCheckedAtGreaterThanEqualAndCheckedAtLessThan(
                        endpointId,
                        ownerId,
                        from,
                        to
                );

        long offline = checkResultRepository
                .countByEndpoint_IdAndEndpoint_Owner_IdAndStatusAndCheckedAtGreaterThanEqualAndCheckedAtLessThan(
                        endpointId,
                        ownerId,
                        EndpointStatus.OFFLINE,
                        from,
                        to
                );

        long available = total - offline;

        BigDecimal percentage = total == 0
                ? null
                : BigDecimal.valueOf(available)
                .multiply(BigDecimal.valueOf(100))
                .divide(
                        BigDecimal.valueOf(total),
                        2,
                        RoundingMode.HALF_UP
                );

        return new UptimeResponse(
                endpointId,
                from,
                to,
                total,
                available,
                offline,
                percentage
        );
    }

    private CheckResultResponse toResponse(CheckResult result) {
        return new CheckResultResponse(
                result.getId(),
                result.getEndpoint().getId(),
                result.getStatus(),
                result.getActualStatusCode(),
                result.getResponseTimeMillis(),
                result.getErrorMessage(),
                result.getCheckedAt()
        );
    }
}