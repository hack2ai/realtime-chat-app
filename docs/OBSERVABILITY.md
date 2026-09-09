# Observability

The chat server exposes an optional read-only Prometheus-style metrics endpoint over HTTP.

## Metrics endpoint

Metrics are disabled by default. Enable them with:

```properties
metrics.enabled=true
metrics.bindAddress=127.0.0.1
metrics.port=9100
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

The endpoint accepts only `GET`, sends restrictive response headers, disables caching, and applies a per-source rate limit. Metrics contain operational counters and JVM information only; they do not expose message bodies, passwords, session tokens, or attachment contents.

When running through Docker Compose, keep the metrics listener bound to localhost or expose it only through a trusted monitoring network. The application TCP port and metrics port serve different purposes and should be controlled independently.

## Exported metrics

The current endpoint reports:

- authenticated connected users and active client handlers
- accepted and rejected TCP connections
- processed requests and protocol errors
- authentication failures and rate-limited requests
- internal handler errors
- client-handler executor activity, queue depth, and completed tasks
- JVM heap usage, committed/max heap, uptime, and live thread count

Counters are monotonic for the lifetime of the running process. Gauges represent the current server or JVM state at scrape time.

## Operational guidance

For production deployments, collect the endpoint through a trusted local or private monitoring path and alert on sustained growth in protocol errors, authentication failures, rejected connections, rate-limited requests, handler queue depth, or JVM memory usage.

The metrics endpoint is intentionally lightweight and in-process. It is not a replacement for centralized logs, distributed tracing, uptime monitoring, certificate-expiry monitoring, database monitoring, or external security telemetry.
