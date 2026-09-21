# Application Architecture

## Overview

The Real-Time Chat Application is organized as a layered TCP application with a JavaFX client.

```text
JavaFX Client
    |
    v
socket/protocol  <---- shared wire contract
    |
    v
server
  ClientHandler / ChatServer
    |
    v
service
  Authentication / Chat / Group / Search / Attachment
    |              |
    v              v
database        AttachmentStorage
  DAOs               |
    |                +--> local disk implementation
    |                +--> future managed object storage adapter
    v
MySQL
```

The dependency direction is intentionally inward:

```text
client -> protocol -> server -> service -> database
                               |
                               +-> storage abstraction
```

Infrastructure details should not leak upward into request-handling code.

## Layer responsibilities

### Client

`com.chatapp.client` owns JavaFX screens, user interaction, connection lifecycle, and rendering of server events.

The client does not contain database queries or server-side authorization rules.

### Protocol

`com.chatapp.socket.protocol` owns the wire contract:

- `Envelope`
- `MessageType`
- length-prefixed UTF-8 framing
- JSON serialization/deserialization
- protocol-size enforcement

Both client and server use this shared codec so framing rules are defined once.

### Server

`com.chatapp.server` owns transport lifecycle and connection orchestration.

`ChatServer` is the composition root for server dependencies and connection registry responsibilities.

`ClientHandler` owns one socket session and translates protocol messages into service calls. It should remain a transport adapter rather than becoming a persistence or business-rules layer.

### Services

`com.chatapp.service` contains business rules and authorization.

Examples:

- `AuthenticationService`: registration, password verification, sessions
- `ChatService`: private messaging and read/delivery state
- `GroupService`: membership and group messaging rules
- `MessageSearchService`: search validation and access rules
- `AttachmentService`: attachment authorization and integrity checks
- `RequestRateLimiter`: bounded request policy

Services should not expose SQL or filesystem implementation details.

### Database

`com.chatapp.database` contains JDBC and persistence operations.

DAOs own SQL statements, result mapping, generated-key handling, and database resource management.

Business services call DAOs instead of calling `DatabaseManager` directly.

### Storage

`AttachmentStorage` is the storage boundary for file bytes.

`AttachmentStorageService` is the current local-disk implementation. A managed object-storage adapter can implement the same interface later without changing `AttachmentService`, `ClientHandler`, or the protocol.

This separation keeps storage migration independent from chat authorization and message handling.

### Domain and DTOs

`com.chatapp.model` contains domain objects.

`com.chatapp.model.dto` contains request/response/event payloads exchanged across the protocol boundary.

DTOs should remain transport-oriented; domain and persistence concerns should stay in their owning layers.

### Security and configuration

`com.chatapp.security` owns TLS context creation.

`com.chatapp.config` owns runtime configuration resolution.

`com.chatapp.util` contains reusable validation that is independent of transport and persistence.

## Change rules

When adding a feature:

1. Add or extend a DTO/message type when the wire contract changes.
2. Put authorization and business rules in a service.
3. Put SQL in a DAO.
4. Put external file/object storage behind `AttachmentStorage` or another narrow interface.
5. Keep `ClientHandler` limited to protocol translation, session state, and routing.
6. Add tests at the service/protocol boundary and persistence boundary where behavior warrants it.

Avoid adding SQL to server handlers, database calls to JavaFX code, or filesystem-specific logic to business services.

## Current composition

At startup, `ChatServer` creates the long-lived server services and injects them into each `ClientHandler`. This keeps shared business components at the server boundary and avoids per-connection copies of stateless search and attachment services.

The legacy convenience constructors on `ClientHandler` remain for compatibility and testing; production server construction uses the fully injected constructor.

## Operational architecture

The server exposes no general-purpose HTTP API. Readiness is represented by a process-local marker heartbeat, while operational metrics are emitted through SLF4J log events.

Container deployment adds:

- bounded log rotation
- non-root server execution
- read-only application filesystem
- writable attachment volume only
- internal database network
- resource limits
- health checks
- configurable application and database TLS
