# Observability stack

The repository includes an optional Prometheus overlay for local or controlled deployment observability.

## Start

Set the metrics bearer token, then start the base Compose stack with the observability override:

```bash
export CHATAPP_MYSQL_ROOT_PASSWORD='change-this-root-secret'
export CHATAPP_DB_PASSWORD='change-this-app-secret'
export CHATAPP_METRICS_AUTH_TOKEN='change-this-metrics-token'

docker compose \
  -f docker-compose.yml \
  -f docker-compose.observability.yml \
  up --build -d
```

Prometheus is available only on the local host at `http://127.0.0.1:9090`.

The server metrics endpoint is also exposed only on the local host at `http://127.0.0.1:9100/metrics`. Prometheus reaches the server over the internal Compose network and authenticates with the same bearer token supplied through `CHATAPP_METRICS_AUTH_TOKEN`.

## Stop

```bash
docker compose \
  -f docker-compose.yml \
  -f docker-compose.observability.yml \
  down
```

Prometheus time-series data is stored in the named `prometheus-data` volume. Remove it only when intentionally resetting observability history:

```bash
docker volume rm realtime-chat-app_prometheus-data
```

The observability overlay does not enable metrics in the base Compose deployment; metrics remain disabled unless the override is explicitly supplied.
