# Real-Time Chat Application

> A professional Java 21 networking project for building a secure, database-backed real-time chat system over TCP.

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.java.com/)
[![Maven](https://img.shields.io/badge/Maven-3.8%2B-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8-4479A1?style=for-the-badge&logo=mysql&logoColor=white)](https://www.mysql.com/)

## Status

**Phase 1 — foundation and authentication.**

The current build provides a runnable TCP server, length-prefixed JSON protocol, MySQL persistence, registration/login/logout, bcrypt password hashing, session tokens, connection pooling, bounded client capacity, and a CLI test client. Private messaging, groups, and the JavaFX UI remain roadmap work.

The repository intentionally does not advertise planned features as implemented features.

## Highlights

- Java 21 + Maven build
- TCP sockets with explicit 4-byte length-prefixed UTF-8 JSON frames
- Bounded server thread pool with connection back-pressure
- Defensive maximum frame size (10 MB)
- BCrypt password hashing with configurable work factor
- Cryptographically random 256-bit session tokens
- Generic authentication failure responses to reduce account enumeration
- MySQL 8 / JDBC persistence with prepared statements
- Lightweight connection pool with validation and timeout handling
- Environment-variable and JVM-property configuration overrides
- Graceful resource cleanup on server shutdown and client disconnect
- JUnit protocol tests and GitHub Actions CI

## Architecture

```text
                 TCP + JSON
Client ─────────────────────────► ChatServer
                                    │
                              ClientHandler
                                    │
                 ┌──────────────────┼──────────────────┐
                 ▼                  ▼                  ▼
       AuthenticationService   Protocol/Codec       DAO layer
                 │                                      │
                 └────────────── MySQL 8 ───────────────┘
```

The code is deliberately separated into configuration, networking, protocol, service, persistence, domain models, and validation packages so the project can grow without turning the socket handler into a monolith.

## Security

Current defensive controls include:

- parameterized SQL via JDBC
- bcrypt password hashing
- secure random session tokens
- generic login failures
- bounded message frames
- bounded server work queues
- no application password or admin seed account in the database schema
- secrets can be supplied through environment variables or JVM system properties

**Important:** this is a portfolio/learning project, not a security-audited production service. A production deployment still needs TLS, rate limiting, stronger session persistence/revocation, secret rotation, hardened database permissions, dependency scanning, monitoring, threat modeling, and security testing.

See [SECURITY.md](SECURITY.md) for reporting guidance.

## Getting Started

### Prerequisites

- JDK 21+
- Maven 3.8+
- MySQL 8+

### 1. Create the database

Run:

```bash
mysql -u root -p < src/main/resources/sql/schema.sql
```

The schema creates tables only. It intentionally does **not** create a default application user or publish a password.

For local development, create a dedicated MySQL account with only the privileges the application needs, then put those credentials in your local configuration.

### 2. Configure the application

```bash
cp src/main/resources/config.properties.example src/main/resources/config.properties
```

Edit the local values. `config.properties` is ignored by Git and must never be committed.

For deployment, secrets can be supplied without a config file. For example:

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

## CLI Authentication Test

With the server running:

```bash
java -cp target/classes com.chatapp.client.TestClient register alice alice@example.com YOUR_LOCAL_PASSWORD YOUR_LOCAL_PASSWORD
java -cp target/classes com.chatapp.client.TestClient login alice YOUR_LOCAL_PASSWORD
java -cp target/classes com.chatapp.client.TestClient ping
```

Never put real credentials into documentation, source code, CI configuration, or commit history.

## Protocol

Every message is encoded as:

```text
[ 4-byte big-endian payload length ][ UTF-8 JSON payload ]
```

This framing makes message boundaries deterministic even when TCP splits or combines packets. The server rejects negative or oversized frames before allocating the payload buffer.

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
    │   ├── database/            # Pool, database manager, DAOs
    │   ├── exception/            # Application exceptions
    │   ├── model/                # Domain and DTO models
    │   ├── server/               # Server and connection handlers
    │   ├── service/              # Authentication/business logic
    │   ├── socket/protocol/      # Envelope, framing, message types
    │   └── util/                 # Validation helpers
    ├── main/resources/
    │   ├── config.properties.example
    │   └── sql/schema.sql
    └── test/java/com/chatapp/    # Automated tests
```

## Roadmap

- [x] Phase 1 — foundation and authentication
- [ ] Phase 2 — private messaging and presence
- [ ] Phase 3 — group chat and membership controls
- [ ] Phase 4 — JavaFX desktop client
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
