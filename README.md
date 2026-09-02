# Real-Time Chat Application

> A professional Java 21 networking project for building a secure, database-backed real-time chat system over TCP.

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.java.com/)
[![Maven](https://img.shields.io/badge/Maven-3.8%2B-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8-4479A1?style=for-the-badge&logo=mysql&logoColor=white)](https://www.mysql.com/)

## Status

**Phase 4 — JavaFX desktop client in progress.**

The application now provides secure authentication, real-time private messaging, presence and typing events, delivery/read states, paginated history, group chat, group membership, MySQL persistence, and a desktop JavaFX client with sign-in, registration, people/groups navigation, history, and messaging.

## Highlights

- Java 21 + Maven build
- JavaFX desktop client with responsive background networking
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
- Group creation, joining, leaving, messaging, membership checks, and history
- Java 21 virtual threads for asynchronous message pushes
- Environment-variable and JVM-property configuration overrides
- JUnit protocol tests and GitHub Actions CI

## Architecture

```text
                    TCP + JSON
JavaFX Client ─────────────────────────► ChatServer
     │                                      │
     │                                ClientHandler
     │                                      │
     │              ┌───────────────────────┼───────────────────────┐
     │              ▼                       ▼                       ▼
     │     AuthenticationService       ChatService            GroupService
     │              │                       │                       │
     │              ▼                       ▼                       ▼
     │           UserDAO              PrivateMessageDAO          GroupDAO
     │              └───────────────────────┬───────────────────────┘
     │                                      ▼
     └──────────────────────────────────► MySQL 8
```

The networking, service, persistence, protocol, client, and domain layers remain separated so features can evolve without turning the socket handler into a monolith.

## Features

### Authentication

Registration and login support username/email identity, BCrypt password hashing, secure session tokens, generic authentication failures, and one active session per account.

### Private messaging

Clients can send messages to authenticated users. Messages are persisted before delivery, allowing offline recipients to retrieve conversation history after reconnecting.

### Presence and typing

The server broadcasts online/offline events and supports typing start/stop indicators. Clients can request the current user list at any time.

### Delivery and read state

Private messages track delivery and read state. Receivers can acknowledge messages as read without being able to modify another user's message data.

### Group chat

Authenticated users can create groups, join groups by ID, leave groups, send messages, and retrieve paginated group history. Group messages are persisted and delivered to currently connected members.

### Desktop client

The JavaFX client provides:

- Sign-in and account registration screens
- Configurable server host/port through JVM properties
- People list with online status
- Group list with member counts
- Private conversation history
- Group conversation history
- Group creation and join-by-ID controls
- Non-blocking socket reads/writes so the UI stays responsive

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

### 5. Start the desktop client

```bash
mvn javafx:run
```

For a different server endpoint:

```bash
mvn javafx:run -Dchatapp.server.host=192.168.1.10 -Dchatapp.server.port=5050
```

## Protocol

Every message is encoded as:

```text
[ 4-byte big-endian payload length ][ UTF-8 JSON payload ]
```

This framing makes message boundaries deterministic even when TCP splits or combines packets. The server rejects negative or oversized frames before allocating the payload buffer.

### Message categories

```text
Authentication     C2S_REGISTER / C2S_LOGIN
Presence           C2S_REQUEST_USER_LIST / S2C_USER_ONLINE
Private chat       C2S_PRIVATE_MESSAGE / S2C_PRIVATE_MESSAGE
Read state         C2S_MESSAGE_READ / S2C_MESSAGE_READ
Groups             C2S_CREATE_GROUP / C2S_GROUP_MESSAGE
History            C2S_REQUEST_*_HISTORY / S2C_*_HISTORY
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
    │   ├── client/              # JavaFX and CLI clients
    │   ├── config/              # Runtime configuration
    │   ├── database/            # Pool, manager, DAOs
    │   ├── exception/           # Application exceptions
    │   ├── model/               # Domain models and DTOs
    │   ├── server/              # Server and connection handlers
    │   ├── service/             # Business logic
    │   ├── socket/protocol/     # Envelope, framing, message types
    │   └── util/                # Validation helpers
    ├── main/resources/
    │   ├── chat.css
    │   ├── config.properties.example
    │   └── sql/schema.sql
    └── test/java/com/chatapp/   # Automated tests
```

## Roadmap

- [x] Phase 1 — foundation and authentication
- [x] Phase 2 — private messaging and presence
- [x] Phase 3 — group chat and membership controls
- [x] Phase 4 — JavaFX desktop client
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
