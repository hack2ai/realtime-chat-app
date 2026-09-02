# Real-Time Chat Application

> A professional Java 21 networking project for building a secure, database-backed real-time chat system over TCP.

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.java.com/)
[![Maven](https://img.shields.io/badge/Maven-3.8%2B-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8-4479A1?style=for-the-badge&logo=mysql&logoColor=white)](https://www.mysql.com/)

## Status

**Phase 2 — private messaging and presence.**

The application now provides authentication plus real-time one-to-one messaging, presence events, typing indicators, message delivery/read states, paginated conversation history, MySQL persistence, and the professional server foundation from Phase 1. Group messaging and the full JavaFX client remain roadmap work.

## Highlights

- Java 21 + Maven build
- TCP sockets with explicit 4-byte length-prefixed UTF-8 JSON frames
- Bounded server thread pool with connection back-pressure
- Defensive maximum frame size (10 MB)
- BCrypt password hashing with configurable work factor
- Cryptographically random 256-bit session tokens
- Generic authentication failure responses to reduce account enumeration
- MySQL 8 / JDBC persistence with prepared statements
- Private messages persisted with SENT / DELIVERED / READ states
- Conversation history limited to 100 messages per request
- Online/offline presence broadcasts and typing events
- Java 21 virtual threads for non-blocking message pushes
- Environment-variable and JVM-property configuration overrides
- JUnit protocol tests and GitHub Actions CI

## Architecture

```text
                  TCP + JSON
Client ───────────────────────────► ChatServer
                                      │
                                ClientHandler
                                      │
              ┌───────────────────────┼───────────────────────┐
              ▼                       ▼                       ▼
     AuthenticationService       ChatService            Protocol/Codec
              │                       │                       │
              ▼                       ▼                       │
           UserDAO              PrivateMessageDAO             │
              └───────────────────────┬───────────────────────┘
                                      ▼
                                   MySQL 8
```

The networking, service, persistence, protocol, and domain layers remain separated so the next group-chat and JavaFX work can be added without turning the socket handler into a monolith.

## Phase 2 Features

### Private messaging

Clients can send a message to another authenticated user. Messages are persisted before delivery, which means offline recipients can retrieve them from conversation history after reconnecting.

### Presence

The server broadcasts `S2C_USER_ONLINE` and `S2C_USER_OFFLINE` events as authenticated connections appear and disappear. Clients can also request the current user list.

### Typing indicators

Authenticated clients can send `C2S_TYPING_START` and `C2S_TYPING_STOP`; the server pushes the corresponding events to other connected clients.

### History and read state

Conversation history supports a bounded page size and cursor-style `beforeMessageId`. Receivers can acknowledge a message as read without being able to modify another user's messages.

## Security

Current defensive controls include:

- parameterized SQL via JDBC
- bcrypt password hashing
- secure random session tokens
- generic login failures
- single active session per account
- bounded message frames
- bounded server work queues
- server-side message length validation
- no application password or admin seed account in the database schema
- secrets can be supplied through environment variables or JVM system properties

**Important:** this is a portfolio/learning project, not a security-audited production service. A production deployment still needs TLS, rate limiting, stronger persistent session management, secret rotation, hardened database permissions, dependency scanning, monitoring, threat modeling, and security testing.

See [SECURITY.md](SECURITY.md) for reporting guidance.

## Getting Started

### Prerequisites

- JDK 21+
- Maven 3.8+
- MySQL 8+

### 1. Create the database

```bash
mysql -u root -p < src/main/resources/sql/schema.sql
```

The schema creates tables only. It intentionally does **not** create a default application user or publish a password.

### 2. Configure the application

```bash
cp src/main/resources/config.properties.example src/main/resources/config.properties
```

Edit the local values. `config.properties` is ignored by Git and must never be committed.

For deployment, secrets can be supplied without a config file:

```bash
export CHATAPP_DB_PASSWORD='your-secret'
export CHATAPP_DB_USER='chatapp_user'
```

The precedence is: **JVM system property → environment variable → config file**.

### 3. Verify the build

```bash
mvn verify
```

### 4. Start the server

```bash
mvn exec:java -Dexec.mainClass="com.chatapp.server.ChatServer"
```

## Protocol

Every message is encoded as:

```text
[ 4-byte big-endian payload length ][ UTF-8 JSON payload ]
```

This framing makes message boundaries deterministic even when TCP splits or combines packets. The server rejects negative or oversized frames before allocating the payload buffer.

### Core message flow

```text
C2S_PRIVATE_MESSAGE
        │
        ▼
   ChatService
        │
        ▼
PrivateMessageDAO ──► MySQL
        │
        ▼
S2C_PRIVATE_MESSAGE ──► recipient (when online)
        │
        └──────────────► sender delivery state
```

## Project Structure

```text
realtime-chat-app/
├── .github/workflows/ci.yml
├── pom.xml
├── README.md
├── CONTRIBUTING.md
├── SECURITY.md
├── LICENSE
└── src/
    ├── main/java/com/chatapp/
    │   ├── client/              # CLI and future JavaFX client
    │   ├── config/              # Runtime configuration
    │   ├── database/            # Pool, manager, DAOs
    │   ├── exception/           # Application exceptions
    │   ├── model/               # Domain models and DTOs
    │   ├── server/              # Server and connection handlers
    │   ├── service/             # Business logic
    │   ├── socket/protocol/     # Envelope, framing, message types
    │   └── util/                # Validation helpers
    ├── main/resources/
    │   ├── config.properties.example
    │   └── sql/schema.sql
    └── test/java/com/chatapp/   # Automated tests
```

## Roadmap

- [x] Phase 1 — foundation and authentication
- [x] Phase 2 — private messaging and presence
- [ ] Phase 3 — group chat and membership controls
- [ ] Phase 4 — polished JavaFX desktop client
- [ ] Phase 5 — file sharing, search, notifications, observability, deployment docs

## Development

Run the complete verification suite before submitting changes:

```bash
mvn verify
```

GitHub Actions runs the same Maven verification on pushes and pull requests targeting `main`.

See [CONTRIBUTING.md](CONTRIBUTING.md) for project conventions.

## License

MIT — see [LICENSE](LICENSE).
