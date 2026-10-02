package com.example.apiwatch.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5441/apiwatch_test_db",
        "spring.datasource.username=apiwatch_test_user",
        "spring.datasource.password=apiwatch_test_password",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false",
        "apiwatch.monitoring.enabled=false",
        "apiwatch.alerts.enabled=false"
})
@AutoConfigureMockMvc
class RegistrationConcurrencyIntegrationTest {

    private static final String PASSWORD =
            "ConcurrencyTest@2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private PasswordEncoder passwordEncoder;

    private String email;

    @AfterEach
    void cleanUp() {
        if (email != null) {
            jdbcTemplate.update(
                    "DELETE FROM app_users WHERE email = ?",
                    email
            );
        }
    }

    @Test
    void concurrentRegistrationReturnsCreatedAndConflict()
            throws Exception {
        email = UUID.randomUUID() + "@example.com";

        CountDownLatch bothPassedEmailCheck = new CountDownLatch(2);

        doAnswer(invocation -> {
            bothPassedEmailCheck.countDown();

            if (!bothPassedEmailCheck.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Both requests must pass the initial email check"
                );
            }

            return invocation.callRealMethod();
        }).when(passwordEncoder).encode(eq(PASSWORD));

        String requestBody = """
                {
                    "fullName": "Concurrent Registration User",
                    "email": "%s",
                    "password": "%s"
                }
                """.formatted(email, PASSWORD);

        Callable<MvcResult> register = () -> mockMvc.perform(
                post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
        ).andReturn();

        ExecutorService workers = Executors.newFixedThreadPool(2);

        try {
            Future<MvcResult> first = workers.submit(register);
            Future<MvcResult> second = workers.submit(register);

            List<MvcResult> results = List.of(
                    first.get(30, TimeUnit.SECONDS),
                    second.get(30, TimeUnit.SECONDS)
            );

            List<Integer> statuses = results.stream()
                    .map(result -> result.getResponse().getStatus())
                    .sorted()
                    .toList();

            assertEquals(List.of(201, 409), statuses);

            MvcResult conflict = results.stream()
                    .filter(result ->
                            result.getResponse().getStatus() == 409
                    )
                    .findFirst()
                    .orElseThrow();

            assertTrue(
                    conflict.getResponse()
                            .getContentAsString()
                            .contains(
                                    "Email address is already registered"
                            )
            );

            Long storedUsers = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM app_users WHERE email = ?",
                    Long.class,
                    email
            );

            assertEquals(Long.valueOf(1), storedUsers);

            String passwordHash = jdbcTemplate.queryForObject(
                    "SELECT password_hash FROM app_users WHERE email = ?",
                    String.class,
                    email
            );

            assertNotEquals(PASSWORD, passwordHash);
            assertTrue(
                    passwordEncoder.matches(PASSWORD, passwordHash)
            );
        } finally {
            workers.shutdownNow();
        }
    }
}