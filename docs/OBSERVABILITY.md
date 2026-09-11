# Observability

The chat server exposes an optional read-only Prometheus-style metrics endpoint over HTTP, plus readiness and protocol-level health checks for container orchestration.

## Readiness and health checks

The server writes a readiness marker after the listening socket is bound and the database connection has been validated. Container orchestration should treat the service as ready only after that marker is present.

The server image also includes a protocol-level health check:

```text
java -cp /app/chatapp-server.jar com.chatapp.server.ServerHealthCheck
```

The probe connects to `127.0.0.1` on the configured chat port, performs the configured TLS handshake when TLS is enabled, sends a `PING`, and requires a `PONG` response. The port can be supplied through the `chatapp.server.port` JVM property or `CHATAPP_SERVER_PORT`; the default is `5050`.

## Metrics endpoint

Metrics are disabled by default. Enable them with:

```properties
metrics.enabled=true
metrics.bindAddress=127.0.0.1
metrics.port=9100
metrics.allowRemote=false
```

The endpoint is:

```text
GET /metrics
```

Example response:

```text
# HELP chatapp_connected_users Currently connected authenticated users.
# TYPE chatapp_connected_users gauge
chatapp_connected_users 2
```

The endpoint is intentionally separate from the chat TCP/TLS protocol. It is intended for a local Prometheus agent, node-level collector, or an explicitly trusted metrics scraper.

## Security defaults

Keep `metrics.enabled=false` unless monitoring is required. The example configuration binds the endpoint to `127.0.0.1`; do not expose it publicly without an explicit network security decision.

When exposing metrics beyond loopback, set `metrics.allowRemote=true` deliberately and configure a non-empty metrics bearer token. Place the listener behind a private monitoring network, firewall, or authenticated reverse proxy as an additional boundary.

Remote clients must send the configured token as:

```http
Authorization: Bearer CHANGE_ME
```

The endpoint accepts only `GET`, sends restrictive response headers, disables caching, and applies a per-source rate limit. Clients that exceed the rate limit receive HTTP `429` with a `Retry-After` header. Metrics contain operational counters and JVM information only; they do not expose message bodies, passwords, session tokens, or attachment contents.

When running through Docker Compose, keep the metrics listener bound to localhost or expose it only through a trusted monitoring network. The application TCP port and metrics port serve different purposes and should be controlled independently.

## Prometheus scrape example

For a deployment where the metrics listener is reachable by Prometheus:

```yaml
scrape_configs:
  - job_name: chatapp
    metrics_path: /metrics
    static_configs:
      - targets: ["chat-server.internal:9100"]
```

Prefer a private monitoring path rather than publishing port `9100` to the public internet.

## Exported metrics

The current endpoint reports:

- authenticated connected users and active client handlers
- accepted and rejected TCP connections
- processed requests and protocol errors
- authentication failures and rate-limited requests
- internal handler errors
- client-handler executor activity, queue depth, and completed tasks
- database connection-pool totals, idle connections, and configured maximum
- JVM heap usage, committed/max heap, uptime, and live thread count

Counters are monotonic for the lifetime of the running process. Gauges represent the current server or JVM state at scrape time.

## Operational signals

For incident triage, useful first-response signals include:

- `chatapp_connected_users` falling unexpectedly: investigate client disconnects, restarts, or network failures.
- `chatapp_rejected_connections_total` increasing: inspect configured capacity and connection-rate pressure.
- `chatapp_protocol_errors_total` or `chatapp_rate_limited_requests_total` increasing rapidly: investigate malformed or abusive client traffic.
- `chatapp_internal_errors_total` increasing: inspect server logs and database health.
- `chatapp_handler_pool_queue_depth` remaining elevated: investigate workload spikes or saturated handler capacity.
- `chatapp_db_pool_idle_connections` approaching zero: investigate database latency, pool sizing, and connection leaks.
- `chatapp_jvm_memory_used_bytes` approaching `chatapp_jvm_memory_max_bytes`: investigate heap pressure and attachment/message workloads.

## Operational boundaries

The metrics endpoint is intentionally lightweight and in-process. It is not a replacement for centralized logs, distributed tracing, uptime monitoring, certificate-expiry monitoring, database monitoring, or external security telemetry.
