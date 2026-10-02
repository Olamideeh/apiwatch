# APIWatch

APIWatch helps developers monitor public HTTP endpoints, identify outages and slow responses, and inspect check history through a browser dashboard.

Built with Java 21, Spring Boot, Spring Security, PostgreSQL, Flyway, Apache HttpClient, and plain HTML, CSS, and JavaScript.

## Features

- Account registration and login using BCrypt passwords and JWT authentication.
- Owner-scoped endpoint registration, listing, history, and uptime reports.
- Scheduled GET checks with configurable expected status, interval, timeout, and slow response threshold.
- ONLINE, DEGRADED, OFFLINE, and PENDING monitoring states.
- Pause and resume, with stale check results rejected after a monitoring state change.
- Outage alerts after three consecutive OFFLINE checks and recovery alerts when an open outage returns ONLINE.
- Persisted email delivery attempts with retries and database leases.
- A browser dashboard for registration, login, endpoint management, history, and uptime.
- PostgreSQL schema migrations managed by Flyway; Hibernate validates the schema.
- Database constraints and locking to handle concurrent registration and check processing.

## Project status

The backend and simple dashboard are implemented. Automated tests have been run successfully during development. Dashboard monitoring, pause/resume, and a selected-range uptime calculation have been exercised locally.

The complete Postman workflow, application Docker image, and public deployment are pending. Docker Compose currently runs supporting services; the Spring Boot application runs on the host.

## Requirements

- JDK 21.
- Docker Desktop with Docker Compose.
- Git.
- An internet connection for Maven dependencies and public endpoint checks.

Use the included Maven wrapper; a separate Maven installation is unnecessary.

## Run locally on Windows

### 1. Clone the repository

```powershell
git clone https://github.com/Olamideeh/apiwatch.git
cd apiwatch
```

Run the following commands from the directory containing `pom.xml`, `mvnw.cmd`, and the Compose file.

### 2. Start supporting services

```powershell
docker compose up -d
docker compose ps
```

| Service | Address |
| --- | --- |
| Application and dashboard | http://localhost:8084 |
| Application health | http://localhost:8084/actuator/health |
| PostgreSQL | localhost:5440 |
| Adminer | http://localhost:8088 |
| Mailpit inbox | http://localhost:8026 |
| Mailpit SMTP | localhost:1026 |

Local development database settings:

| Setting | Value |
| --- | --- |
| Database | apiwatch_db |
| Username | apiwatch_user |
| Password | apiwatch_password |

In Adminer, select PostgreSQL and use `postgres` as the server, because Adminer connects through the Compose network.

PostgreSQL data is stored in the `apiwatch_postgres_data` named volume. `docker compose down` preserves the volume; `docker compose down -v` deletes it and its database contents.

### 3. Set a local JWT signing secret

The application includes a development fallback secret. Set your own secret in the PowerShell session before starting it:

```powershell
$jwtBytes = New-Object byte[] 48
$jwtRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRng.GetBytes($jwtBytes)
$jwtRng.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
```

Keep the same secret across restarts if existing tokens should remain valid until their expiry. Changing it invalidates previously issued tokens. Do not commit signing secrets.

### 4. Start the application

```powershell
.\mvnw.cmd spring-boot:run
```

Open http://localhost:8084, create an account, and log in. Flyway applies pending migrations during startup.

Check health in another PowerShell window:

```powershell
Invoke-RestMethod http://localhost:8084/actuator/health
```

## Use the dashboard

1. Create an account with a password of at least 12 characters, then log in.
2. Add a public HTTP or HTTPS endpoint with a name and monitoring settings.
3. Wait for a scheduled check and select **Refresh** to retrieve results.
4. Select **View details** for the latest 20 checks and uptime report.
5. Select a local date/time range and click **Calculate uptime**.
6. Use **Pause** to stop accepting new check results and **Resume** to make the endpoint due again.

Refresh retrieves recorded results; it does not trigger a check. The dashboard refreshes manually. Refreshing endpoint details resets the uptime range to the last 24 hours.

The JWT is held in browser memory. Reloading the browser page requires another login. By default tokens expire after 60 minutes; an authenticated request with an expired token returns the user to login.

## Monitoring rules

| State | Meaning |
| --- | --- |
| PENDING | No completed check has been recorded yet. |
| ONLINE | The expected HTTP status was received within the slow response threshold. |
| DEGRADED | The expected HTTP status was received, but response duration exceeded the threshold. |
| OFFLINE | A request failed or returned an unexpected HTTP status. |

Paused is a separate monitoring flag. It does not erase the last recorded status or history.

Settings accept expected status codes from 100 to 599, intervals from 60 to 86400 seconds, and timeouts from 100 to 30000 milliseconds. The slow response threshold must be positive and cannot exceed the configured timeout. Ordinary checks receive final HTTP responses; an informational 1xx code is generally unsuitable as an expected status.

The scheduler scans every five seconds after an initial ten-second delay. Actual timing also depends on worker availability, request duration, and database leases.

An outage opens after three consecutive OFFLINE results. A DEGRADED result resets the consecutive failure count but does not close an existing outage. An ONLINE result closes an existing outage and creates a recovery alert.

Email delivery allows up to three claimed attempts, with a 60-second retry delay after a failed delivery. Mailpit captures development emails locally; it does not deliver them to real inboxes.

## Uptime calculation

```text
uptime percentage = available checks / total checks × 100
```

ONLINE and DEGRADED checks count as available. OFFLINE checks count as unavailable. Reports include checks at or after `from` and strictly before `to`. The percentage is rounded to two decimal places and is null when the range contains no checks.

For example, 10 available checks out of 10 produce 100.00%; 10 available checks out of 24 produce 41.67%.

This is check-based availability, not a measurement of exact outage duration. Paused periods add no checks. Earlier failed checks remain in historical reports even after a connection issue is fixed.

## API endpoints

Authenticated endpoints require `Authorization: Bearer <accessToken>`. Endpoint ownership comes from the JWT subject, not a client-supplied owner ID.

| Method | Path | Purpose | Authentication |
| --- | --- | --- | --- |
| POST | /api/v1/auth/register | Register a user | Public |
| POST | /api/v1/auth/login | Obtain an access token | Public |
| POST | /api/v1/endpoints | Register an endpoint | Bearer token |
| GET | /api/v1/endpoints?page=0&size=20 | List owned endpoints | Bearer token |
| GET | /api/v1/endpoints/{id} | Retrieve an owned endpoint | Bearer token |
| PATCH | /api/v1/endpoints/{id}/monitoring | Pause or resume | Bearer token |
| GET | /api/v1/endpoints/{id}/checks?page=0&size=20 | Check history | Bearer token |
| GET | /api/v1/endpoints/{id}/uptime?from=...&to=... | Check-based uptime | Bearer token |
| GET | /actuator/health | Application health | Public |

Pagination starts at page zero and supports sizes from 1 to 100. Uptime API dates are ISO-8601 instants, for example `2026-10-02T21:20:00Z`.

Example registration body:

```json
{
  "fullName": "Example Developer",
  "email": "developer@example.test",
  "password": "ExamplePassword@2026"
}
```

Example login body:

```json
{
  "email": "developer@example.test",
  "password": "ExamplePassword@2026"
}
```

Example endpoint body; replace the URL with a public endpoint you own or are authorized to monitor:

```json
{
  "name": "My API health",
  "url": "https://your-api.example/health",
  "expectedStatusCode": 200,
  "intervalSeconds": 300,
  "timeoutMillis": 5000,
  "responseTimeLimitMillis": 1000
}
```

Pause with `{"paused": true}` and resume with `{"paused": false}`.

Validation errors return 400; invalid credentials return 401; inactive users return 403; unavailable or unowned endpoints return 404; duplicate registration returns 409. Database email uniqueness also protects against simultaneous registration requests.

## Run automated tests

Integration tests use a separate PostgreSQL database on port 5441. Create its container once:

```powershell
docker run -d --name apiwatch-test-db -e POSTGRES_DB=apiwatch_test_db -e POSTGRES_USER=apiwatch_test_user -e POSTGRES_PASSWORD=apiwatch_test_password -p 5441:5432 postgres:16-alpine
```

On later runs, start the existing container:

```powershell
docker start apiwatch-test-db
```

Check that it is ready:

```powershell
docker exec apiwatch-test-db pg_isready -U apiwatch_test_user -d apiwatch_test_db
```

Keep development PostgreSQL and Mailpit running as well. The email delivery integration tests use Mailpit on port 1026. Run:

```powershell
.\mvnw.cmd clean test
```

The suite covers registration and authentication, owner-scoped access, URL/address validation, status transitions, HTTP probes, scheduled processing, check leases, transactional rollback, history, uptime, and email delivery. Database integration tests exercise concurrent claims, duplicate recording, stale results after pause/resume, and simultaneous registration.

## Three checks that demonstrate the application works

1. **Monitor an endpoint:** register a permitted public health URL that returns the expected status; verify a check appears in history with its status and duration.
2. **Pause and resume:** pause, wait longer than the interval, and verify no new results appear; resume and verify a new result is recorded. Integration tests verify old claim results remain rejected after resume.
3. **Calculate uptime:** choose a range containing only successful expected-status checks; verify available checks equal total checks and uptime is 100.00%. Choose a broader range with failed checks and verify the percentage decreases.

## Configuration

| Property or variable | Current development value |
| --- | --- |
| server.port | 8084 |
| spring.datasource.url | jdbc:postgresql://localhost:5440/apiwatch_db |
| JWT_SECRET | Environment variable with development fallback |
| JWT_EXPIRATION_MINUTES | 60 by default |
| apiwatch.monitoring.enabled | true |
| apiwatch.monitoring.scan-delay-millis | 5000 |
| apiwatch.monitoring.initial-delay-millis | 10000 |
| spring.mail.host | localhost |
| spring.mail.port | 1026 |
| apiwatch.alerts.from | no-reply@apiwatch.test |
| apiwatch.alerts.enabled | true |
| apiwatch.alerts.scan-delay-millis | 5000 |
| apiwatch.alerts.initial-delay-millis | 10000 |

The current configuration enables DEBUG logging for `HttpProbeService` and `ApacheMonitoringHttpTransport` to diagnose connection failures. Remove those temporary logging overrides or change them to INFO when troubleshooting is complete.

## Limitations

- Checks support public HTTP/HTTPS GET endpoints only; no custom authorization headers, request bodies, or response-body assertions.
- Redirects are disabled. A redirect response is evaluated against the configured expected status rather than followed.
- Loopback, private, and selected reserved address ranges are blocked, including connection-time DNS validation. Localhost applications cannot be monitored with this configuration. These checks are not a substitute for deployment-level network isolation and egress controls.
- Duration measures work through response headers, including DNS and connection setup. Timeout settings apply to request phases, not a strict total deadline; total elapsed duration can exceed the configured timeout.
- Database leases reject superseded results but cannot guarantee exactly one network request. SMTP delivery can also be duplicated if sending succeeds before recording delivery fails.
- The dashboard displays the latest 20 checks; the API exposes paginated history.
- Mailpit is for development email capture. Real email delivery requires an SMTP provider and its configuration.
- Registration has no email verification, password reset, refresh tokens, or rate limiting.
- Data retention and cleanup are not implemented; history grows as checks run.
- Public deployment and application containerization have not yet been completed.

## Troubleshooting

**Database connection failure:** start Docker services and check `docker compose ps`. Host-run Spring Boot uses port 5440; Adminer connects to `postgres:5432` inside Compose.

**OFFLINE with no HTTP code:** the check did not return a usable HTTP status. Inspect the controlled error in history and diagnostic logs in the application terminal. A failed local probe does not prove the target website is globally unavailable.

**DEGRADED with HTTP 200:** the expected status was received, but duration exceeded the slow response threshold.

**Unexpected uptime percentage:** check the selected date range. Old failed checks are retained, and Refresh resets the dashboard range to the last 24 hours. Use Calculate uptime after choosing a custom range.

**No real email received:** open http://localhost:8026. Development email is captured by Mailpit rather than delivered externally.

**Port already in use:** stop the conflicting process or adjust its port and corresponding application configuration. Run only one host application instance on port 8084.

## Repository

https://github.com/Olamideeh/apiwatch
