# Real-Time Chat Application

> A professional Java 21 networking project for building a secure, database-backed real-time chat system over TCP.

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.java.com/)
[![Maven](https://img.shields.io/badge/Maven-3.8%2B-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8-4479A1?style=for-the-badge&logo=mysql&logoColor=white)](https://www.mysql.com/)

## Status

**Phase 6 — production hardening and deployment readiness.**

The application provides secure authentication, real-time private messaging, presence and typing events, delivery/read states, paginated history, group chat, private file sharing, message search, MySQL persistence, a responsive JavaFX desktop client, automated dependency updates, container packaging, CI/CD checks, and configurable TLS transport.

## Highlights

- Java 21 + Maven build
- JavaFX desktop client with responsive background networking
- TCP sockets with explicit 4-byte length-prefixed UTF-8 JSON frames
- Optional TLS transport for encrypted client/server TCP connections
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
- Private attachment upload/download with participant authorization, 5 MB limit, safe filenames, and SHA-256 integrity verification
- Private message search with bounded result sets
- Java 21 virtual threads for asynchronous message pushes
- Environment-variable and JVM-property configuration overrides
- JUnit protocol tests and GitHub Actions CI
- Docker image and Docker Compose deployment support
- Weekly Dependabot updates for Maven dependencies, GitHub Actions, and Docker

## Architecture

```text
                    TCP / TLS + JSON
JavaFX Client ─────────────────────────► ChatServer
     │                                      │
     │                                ClientHandler
     │                                      │
     │          ┌───────────────┬───────────┼───────────┐
     │          ▼               ▼           ▼           ▼
     │ Authentication       ChatService  GroupService AttachmentService
     │       │                 │           │              │
     │       ▼                 ▼           ▼              ▼
     │    UserDAO        PrivateMessageDAO GroupDAO   AttachmentDAO
     │          └───────────────┬───────────┴──────────────┘
     │                          ▼
     └──────────────────────► MySQL 8
                                  │
                                  ▼
                         Local attachment storage
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

### Private file sharing

Authenticated private-chat participants can exchange files up to 5 MB. Files are stored outside the repository, metadata is persisted separately, filenames are sanitized, downloads require participant authorization, and downloaded bytes are verified against the stored SHA-256 digest.

### Message search

Private conversations support server-side text search with bounded result counts. Search requests and responses use dedicated protocol messages rather than filtering only the currently rendered JavaFX rows.

### Desktop client

The JavaFX client provides:

- Sign-in and account registration screens
- Configurable server host/port through JVM properties
- Optional TLS client transport with configurable trust store
- People list with online status and unread badges
- Group list with member counts and unread badges
- Private and group conversation history
- Real-time messaging, typing, delivery/read states
- Private message search
- Private file upload/download controls
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
- connection, login, registration, request, search, upload, download, PING, and protocol-error rate limiting
- server-side message length validation
- authenticated participant checks for private attachments
- attachment filename/path sanitization
- attachment size limits and SHA-256 integrity verification
- no application password or admin seed account in the database schema
- secrets can be supplied through environment variables or JVM system properties
- runtime attachment data is excluded from Git
- optional TLS for the application TCP transport

**Important:** this is a portfolio/learning project, not a security-audited production service. A production deployment still needs certificate lifecycle management, secret rotation, hardened database permissions, monitoring, threat modeling, malware/content scanning for uploads, and security testing.

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

Attachment files default to `data/attachments` and are intentionally excluded from version control. For production, replace local storage with durable object storage and keep only attachment metadata in MySQL.

### 3. Configure application TLS

The server can use a PKCS12 keystore containing its certificate and private key. Set the following values in `config.properties` or through environment/JVM overrides:

```properties
# Server
server.port=5050
tls.enabled=true
tls.keyStorePath=config/server-keystore.p12
tls.keyStorePassword=CHANGE_ME

# JavaFX client
client.tls.enabled=true
# Leave blank for the JVM default CA trust store, or set a PKCS12 trust store for a private CA.
client.tls.trustStorePath=
client.tls.trustStorePassword=
```

Keep `tls.enabled=false` and `client.tls.enabled=false` for trusted local development only. Never commit private keys, keystores, or passwords.

### 4. Verify the build

```bash
mvn verify
```

### 5. Start the server

```bash
mvn exec:java -Dexec.mainClass="com.chatapp.server.ChatServer"
```

### 6. Start the desktop client

```bash
mvn javafx:run
```

For a different server endpoint:

```bash
mvn javafx:run -Dchatapp.server.host=192.168.1.10 -Dchatapp.server.port=5050
```

### Docker Compose deployment

The repository also includes a containerized server plus MySQL stack. Docker Compose keeps database data and attachment bytes in named volumes and requires secrets to be supplied from the environment rather than committed to Git.

Set the required secrets:

```bash
export CHATAPP_MYSQL_ROOT_PASSWORD='change-this-root-secret'
export CHATAPP_DB_PASSWORD='change-this-app-secret'
```

Start the stack:

```bash
docker compose up --build -d
```

The TCP server listens on port `5050`. The JavaFX desktop client can connect to the Docker host using that address and port. Stop the stack with:

```bash
docker compose down
```

The server container runs as a non-root user. The Compose database is intended for development/demo environments; production deployments should use managed MySQL, application TLS, TLS for database traffic where appropriate, an external secret manager, and durable object storage for attachments.

### Automated releases

Pushing a semantic version tag such as `v1.1.0` triggers the release workflow. It verifies the Maven build, publishes the runnable server JAR and SHA-256 checksum to a GitHub Release, and builds and pushes the tagged and `latest` server image to GHCR.

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
Search             C2S_SEARCH_PRIVATE_MESSAGES / S2C_PRIVATE_SEARCH_RESULTS
Files              C2S_UPLOAD_PRIVATE_FILE / C2S_DOWNLOAD_PRIVATE_FILE
Errors              S2C_ERROR
Notifications      S2C_NOTIFICATION
```

## Project Structure

```text
realtime-chat-app/
├── .github/workflows/ci.yml
├── .github/workflows/codeql.yml
├── .github/workflows/release.yml
├── .github/dependabot.yml
├── .dockerignore
├── Dockerfile
├── docker-compose.yml
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
- [x] Phase 5 — file sharing and message search
- [x] Phase 6 — notifications, deployment packaging, CI/CD hardening, rate limiting, dependency automation, and configurable TLS
- [ ] Phase 7 — production observability, managed storage, certificate lifecycle automation, and external security testing

## Development

Run the complete verification suite before submitting changes:

```bash
mvn verify
```

GitHub Actions runs the same Maven verification on pushes and pull requests targeting `main`. CodeQL analysis and container builds are also automated, and tagged releases publish versioned server packages.

See [CONTRIBUTING.md](CONTRIBUTING.md) for project conventions.

## License

MIT — see [LICENSE](LICENSE).