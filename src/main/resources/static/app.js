"use strict";

const $ = id => document.getElementById(id);

let token = null;
let sessionVersion = 0;
let page = 0;
let selectedEndpoint = null;
let detailVersion = 0;
let listVersion = 0;

function notify(text, error = false) {
    const message = $("message");
    message.textContent = text;
    message.className = error ? "error" : "success";
    message.hidden = false;
}

function switchAuth(register) {
    $("login-form").hidden = register;
    $("register-form").hidden = !register;
    $("login-tab").classList.toggle("active", !register);
    $("register-tab").classList.toggle("active", register);
    $("login-tab").setAttribute("aria-pressed", String(!register));
    $("register-tab").setAttribute("aria-pressed", String(register));
}

function closeDetails() {
    selectedEndpoint = null;
    detailVersion++;
    $("detail-panel").hidden = true;
    $("check-list").replaceChildren();
}

function logout(message = "You have logged out.") {
    token = null;
    sessionVersion++;
    listVersion++;
    page = 0;
    closeDetails();

    $("dashboard-view").hidden = true;
    $("auth-view").hidden = false;
    $("logout").hidden = true;
    $("account-email").textContent = "";
    $("endpoint-list").replaceChildren();
    $("login-password").value = "";
    switchAuth(false);
    notify(message);
}

async function api(path, options = {}, authenticated = true) {
    const version = sessionVersion;
    const headers = {
        Accept: "application/json",
        ...options.headers
    };

    if (options.body) headers["Content-Type"] = "application/json";

    if (authenticated) {
        if (!token) throw new Error("Please log in.");
        headers.Authorization = `Bearer ${token}`;
    }

    const response = await fetch(`/api/v1${path}`, {
        ...options,
        headers,
        credentials: "omit"
    });

    const text = await response.text();
    let data = {};

    if (text) {
        try {
            data = JSON.parse(text);
        } catch {
            throw new Error("The server returned an unexpected response.");
        }
    }

    if (version !== sessionVersion) {
        throw new Error("Your session has changed. Please try again.");
    }

    if (response.status === 401 && authenticated) {
        logout("Your session has expired. Please log in again.");
        throw new Error("Your session has expired. Please log in again.");
    }

    if (!response.ok) {
        const errors = Array.isArray(data.errors)
            ? data.errors.map(item => item.message).join(" ")
            : "";

        throw new Error(
            errors || data.detail || `Request failed (${response.status}).`
        );
    }

    return data;
}

async function withBusy(button, action) {
    button.disabled = true;

    try {
        await action();
    } catch (error) {
        notify(error.message || "Unable to complete the request.", true);
    } finally {
        button.disabled = false;
    }
}

function formatDate(value) {
    return value ? new Date(value).toLocaleString() : "Not checked yet";
}

function badge(status) {
    const element = document.createElement("span");
    const allowed = ["ONLINE", "DEGRADED", "OFFLINE", "PENDING", "PAUSED"];
    const safeStatus = allowed.includes(status) ? status : "PENDING";

    element.className = `badge ${safeStatus}`;
    element.textContent = safeStatus;
    return element;
}

function textElement(tag, text, className = "") {
    const element = document.createElement(tag);
    element.textContent = text;
    element.className = className;
    return element;
}

function renderEndpoint(endpoint) {
    const article = document.createElement("article");
    article.className = "endpoint";

    const heading = document.createElement("div");
    heading.className = "endpoint-top";
    heading.append(
        textElement("h3", endpoint.name),
        badge(endpoint.paused ? "PAUSED" : endpoint.currentStatus)
    );

    article.append(
        heading,
        textElement("p", endpoint.url, "endpoint-url"),
        textElement(
            "p",
            `Last check: ${formatDate(endpoint.lastCheckedAt)} · ` +
            `Interval: ${endpoint.intervalSeconds}s · ` +
            `Consecutive failures: ${endpoint.consecutiveFailures}`,
            "endpoint-meta"
        )
    );

    const actions = document.createElement("div");
    actions.className = "endpoint-actions";

    const details = textElement("button", "View details", "secondary");
    details.type = "button";
    details.addEventListener("click", () =>
        withBusy(details, () => openDetails(endpoint))
    );

    const toggle = textElement(
        "button",
        endpoint.paused ? "Resume" : "Pause",
        "secondary"
    );
    toggle.type = "button";

    toggle.addEventListener("click", () => withBusy(toggle, async () => {
        await api(`/endpoints/${endpoint.id}/monitoring`, {
            method: "PATCH",
            body: JSON.stringify({ paused: !endpoint.paused })
        });

        notify(endpoint.paused ? "Monitoring resumed." : "Monitoring paused.");
        await loadEndpoints();
    }));

    actions.append(details, toggle);
    article.append(actions);
    return article;
}

async function loadEndpoints() {
    const version = ++listVersion;
    const requestedPage = page;
    const result = await api(`/endpoints?page=${requestedPage}&size=10`);

    if (version !== listVersion) return;

    // An empty page can occur if the dataset changes between requests.
    if (requestedPage > 0 && result.content.length === 0) {
        page--;
        return loadEndpoints();
    }

    $("endpoint-list").replaceChildren(
        ...result.content.map(renderEndpoint)
    );

    $("endpoint-empty").hidden = result.totalElements !== 0;
    $("endpoint-total").textContent = `${result.totalElements} registered`;
    $("page-label").textContent =
        `Page ${result.page + 1} of ${Math.max(1, result.totalPages)}`;

    $("previous").disabled = result.page === 0;
    $("next").disabled = result.last;
}

function localInputDate(date) {
    const pad = value => String(value).padStart(2, "0");

    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-` +
        `${pad(date.getDate())}T${pad(date.getHours())}:` +
        `${pad(date.getMinutes())}`;
}

async function openDetails(endpoint) {
    selectedEndpoint = endpoint;
    const version = ++detailVersion;

    $("detail-name").textContent = endpoint.name;
    $("detail-panel").hidden = false;
    $("check-list").replaceChildren();
    $("checks-empty").hidden = true;
    $("uptime-result").textContent = "Loading endpoint details…";

    const to = new Date();
    const from = new Date(to.getTime() - 24 * 60 * 60 * 1000);

    $("range-from").value = localInputDate(from);
    $("range-to").value = localInputDate(to);

    const [history, uptime] = await Promise.all([
        api(`/endpoints/${endpoint.id}/checks?page=0&size=20`),
        api(
            `/endpoints/${endpoint.id}/uptime?` +
            new URLSearchParams({
                from: from.toISOString(),
                to: to.toISOString()
            })
        )
    ]);

    if (version !== detailVersion) return;

    const rows = history.content.map(check => {
        const row = document.createElement("tr");
        const statusCell = document.createElement("td");
        statusCell.append(badge(check.status));

        row.append(
            textElement("td", formatDate(check.checkedAt)),
            statusCell,
            textElement("td", check.actualStatusCode ?? "—"),
            textElement("td", `${check.responseTimeMillis} ms`),
            textElement("td", check.errorMessage || "—")
        );

        return row;
    });

    $("check-list").replaceChildren(...rows);
    $("checks-empty").hidden = rows.length !== 0;
    renderUptime(uptime);

    $("detail-panel").scrollIntoView({
        behavior: "smooth",
        block: "start"
    });
}

function renderUptime(result) {
    $("uptime-result").textContent =
        result.uptimePercentage == null
            ? "No checks recorded within this time range."
            : `${Number(result.uptimePercentage).toFixed(2)}% uptime · ` +
            `${result.availableChecks} available / ` +
            `${result.totalChecks} checks · ` +
            `${result.offlineChecks} offline`;
}

$("login-tab").addEventListener("click", () => switchAuth(false));
$("register-tab").addEventListener("click", () => switchAuth(true));
$("logout").addEventListener("click", () => logout());
$("close-details").addEventListener("click", closeDetails);

$("login-form").addEventListener("submit", event => {
    event.preventDefault();

    withBusy(event.submitter, async () => {
        const result = await api("/auth/login", {
            method: "POST",
            body: JSON.stringify({
                email: $("login-email").value.trim(),
                password: $("login-password").value
            })
        }, false);

        if (!result.accessToken) {
            throw new Error("The login response did not contain a token.");
        }

        token = result.accessToken;
        sessionVersion++;
        page = 0;

        $("login-password").value = "";
        $("account-email").textContent = result.email;
        $("auth-view").hidden = true;
        $("dashboard-view").hidden = false;
        $("logout").hidden = false;
        $("message").hidden = true;

        await loadEndpoints();
    });
});

$("register-form").addEventListener("submit", event => {
    event.preventDefault();

    withBusy(event.submitter, async () => {
        const email = $("register-email").value.trim();

        await api("/auth/register", {
            method: "POST",
            body: JSON.stringify({
                fullName: $("register-name").value.trim(),
                email,
                password: $("register-password").value
            })
        }, false);

        $("register-form").reset();
        $("login-email").value = email;
        switchAuth(false);
        notify("Account created. Log in to start monitoring.");
    });
});

$("endpoint-form").addEventListener("submit", event => {
    event.preventDefault();

    withBusy(event.submitter, async () => {
        const timeout = Number($("timeout").value);
        const responseLimit = Number($("response-limit").value);

        if (responseLimit > timeout) {
            throw new Error(
                "The slow response threshold cannot exceed the timeout."
            );
        }

        await api("/endpoints", {
            method: "POST",
            body: JSON.stringify({
                name: $("endpoint-name").value.trim(),
                url: $("endpoint-url").value.trim(),
                expectedStatusCode: Number($("expected-status").value),
                intervalSeconds: Number($("interval").value),
                timeoutMillis: timeout,
                responseTimeLimitMillis: responseLimit
            })
        });

        $("endpoint-form").reset();
        page = 0;
        notify("Endpoint registered. Its first check will run shortly.");
        await loadEndpoints();
    });
});

$("uptime-form").addEventListener("submit", event => {
    event.preventDefault();

    withBusy(event.submitter, async () => {
        if (!selectedEndpoint) return;

        const from = new Date($("range-from").value);
        const to = new Date($("range-to").value);

        if (!Number.isFinite(from.getTime()) ||
            !Number.isFinite(to.getTime()) || from >= to) {
            throw new Error("Choose a valid range with From before To.");
        }

        const version = ++detailVersion;
        const endpointId = selectedEndpoint.id;

        const result = await api(
            `/endpoints/${endpointId}/uptime?` +
            new URLSearchParams({
                from: from.toISOString(),
                to: to.toISOString()
            })
        );

        if (version === detailVersion) renderUptime(result);
    });
});

$("refresh").addEventListener("click", event =>
    withBusy(event.currentTarget, async () => {
        await loadEndpoints();

        if (selectedEndpoint) await openDetails(selectedEndpoint);
    })
);

$("previous").addEventListener("click", async () => {
    if (page === 0) return;

    page--;
    try {
        await loadEndpoints();
    } catch (error) {
        notify(error.message, true);
    }
});

$("next").addEventListener("click", async () => {
    page++;
    try {
        await loadEndpoints();
    } catch (error) {
        notify(error.message, true);
    }
});