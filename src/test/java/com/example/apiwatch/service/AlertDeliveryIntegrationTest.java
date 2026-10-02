package com.example.apiwatch.service;

import com.example.apiwatch.entity.Alert;
import com.example.apiwatch.entity.MonitoredEndpoint;
import com.example.apiwatch.entity.User;
import com.example.apiwatch.enums.AlertDeliveryStatus;
import com.example.apiwatch.enums.AlertType;
import com.example.apiwatch.repository.AlertRepository;
import com.example.apiwatch.repository.MonitoredEndpointRepository;
import com.example.apiwatch.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5441/apiwatch_test_db",
        "spring.datasource.username=apiwatch_test_user",
        "spring.datasource.password=apiwatch_test_password",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false",
        "apiwatch.monitoring.enabled=false",
        "apiwatch.alerts.enabled=false",
        "spring.mail.host=localhost",
        "spring.mail.port=1026",
        "spring.mail.properties.mail.smtp.auth=false",
        "spring.mail.properties.mail.smtp.starttls.enable=false",
        "apiwatch.alerts.from=no-reply@apiwatch.test"
})
class AlertDeliveryIntegrationTest {

    @Autowired
    private AlertDeliveryCoordinator coordinator;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MonitoredEndpointRepository endpointRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private AlertEmailSender emailSender;

    private UUID ownerId;
    private UUID endpointId;

    @BeforeEach
    void setUp() {
        User owner = userRepository.saveAndFlush(
                User.builder()
                        .fullName("Mail Integration User")
                        .email("mail-test-" + UUID.randomUUID()
                                + "@apiwatch.test")
                        .passwordHash("test-only-password-hash")
                        .active(true)
                        .build()
        );

        ownerId = owner.getId();

        MonitoredEndpoint endpoint = endpointRepository.saveAndFlush(
                MonitoredEndpoint.builder()
                        .owner(owner)
                        .name("Mailpit Integration Check")
                        .url("https://example.com/health")
                        .nextCheckAt(Instant.now())
                        .build()
        );

        endpointId = endpoint.getId();
    }

    @AfterEach
    void cleanUp() {
        if (endpointId != null) {
            jdbcTemplate.update(
                    "DELETE FROM alerts WHERE endpoint_id = ?",
                    endpointId
            );
            jdbcTemplate.update(
                    "DELETE FROM check_results WHERE endpoint_id = ?",
                    endpointId
            );
            jdbcTemplate.update(
                    "DELETE FROM monitored_endpoints WHERE id = ?",
                    endpointId
            );
        }

        if (ownerId != null) {
            jdbcTemplate.update(
                    "DELETE FROM app_users WHERE id = ?",
                    ownerId
            );
        }
    }

    @ParameterizedTest
    @EnumSource(AlertType.class)
    void deliversEmailAndDoesNotResendCompletedAlert(AlertType type) {
        MonitoredEndpoint endpoint = endpointRepository
                .findById(endpointId)
                .orElseThrow();

        User owner = userRepository.findById(ownerId).orElseThrow();

        Alert alert = alertRepository.saveAndFlush(
                Alert.builder()
                        .endpoint(endpoint)
                        .type(type)
                        .recipientEmail(owner.getEmail())
                        .nextAttemptAt(Instant.now().minusSeconds(1))
                        .build()
        );

        UUID alertId = alert.getId();

        // Calls the actual email sender and Mailpit SMTP server.
        assertTrue(coordinator.deliver(alertId));

        Alert stored = alertRepository.findById(alertId).orElseThrow();

        assertAll(
                () -> assertEquals(
                        AlertDeliveryStatus.SENT,
                        stored.getDeliveryStatus()
                ),
                () -> assertEquals(1, stored.getAttemptCount()),
                () -> assertNotNull(stored.getSentAt()),
                () -> assertNull(stored.getLastError()),
                () -> assertNull(stored.getDeliveryToken()),
                () -> assertNull(stored.getDeliveryLeaseUntil())
        );

        assertFalse(coordinator.deliver(alertId));

        verify(emailSender, times(1))
                .send(any(com.example.apiwatch.dto.AlertEmailMessage.class));
    }
}