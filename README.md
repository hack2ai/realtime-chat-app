# Real-Time Chat Application — Java Socket Programming

> A Java networking project exploring secure authentication, TCP message framing, multithreaded socket handling, and a MySQL-backed chat architecture.

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.java.com/)
[![Maven](https://img.shields.io/badge/Maven-3.8%2B-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8-4479A1?style=for-the-badge&logo=mysql&logoColor=white)](https://www.mysql.com/)

## Project Status

**Phase 1 of 5 — Foundation complete.**

The current release focuses on the server foundation, database layer, TCP/JSON protocol, and end-to-end authentication. Private messaging, group messaging, and the JavaFX client are planned for subsequent phases.

This status is intentionally explicit so the repository does not present planned functionality as already implemented.

## What Is Implemented

- Java 21 Maven project structure
- MySQL schema for users and chat entities
- JDBC database access with parameterized queries
- Hand-rolled connection pool
- Length-prefixed JSON protocol over TCP
- Multithreaded socket server
- Registration, login, and logout
- bcrypt password hashing
- Secure random session tokens
- Generic authentication errors to reduce account enumeration
- CLI test client for exercising the authentication flow

## Architecture

```text
CLI / Future JavaFX Client
          │
          │ TCP + JSON
          ▼
   ChatServer / ClientHandler
          │
          ├── AuthenticationService
          │
          ├── MessageCodec
          │
          └── DAO Layer
                  │
                  ▼
               MySQL 8
```

## Authentication Flow

```text
Client
  ↓
Register / Login Request
  ↓
Input Validation
  ↓
AuthenticationService
  ↓
bcrypt verification
  ↓
Session Token
  ↓
Authenticated Connection
```

## Security Engineering

The current implementation includes several defensive patterns:

- SQL statements use `PreparedStatement` parameter binding.
- Passwords are hashed with bcrypt before persistence.
- Authentication failures use a generic response to reduce username enumeration.
- Session tokens use cryptographically secure randomness.
- Application credentials are expected to remain outside version control.
- The project explicitly distinguishes development functionality from production security requirements.

### Production Security Gap

This is a portfolio/learning project and **has not been presented as a security-audited production system**. A production deployment would still require, at minimum:

- TLS for socket communication
- Login rate limiting / lockout controls
- Persisted and revocable sessions
- Secret rotation
- Stronger operational logging and monitoring
- Dependency and vulnerability scanning
- Hardened database permissions
- Security testing and threat modeling

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Networking | `Socket` / `ServerSocket` |
| Protocol | Length-prefixed JSON |
| Serialization | Gson |
| Database | MySQL 8 |
| Data access | JDBC |
| Password hashing | bcrypt / Spring Security Crypto |
| Build | Maven |
| Planned UI | JavaFX + CSS |

## Project Structure

```text
realtime-chat-app/
├── pom.xml
├── src/main/java/com/chatapp/
│   ├── client/              # CLI client / future UI client
│   ├── server/              # ChatServer and ClientHandler
│   ├── service/             # Authentication/business logic
│   ├── database/            # Connection pool and DAOs
│   ├── model/               # Domain and DTO models
│   ├── socket/protocol/     # Message types and framing
│   ├── util/                # Validation helpers
│   ├── exception/           # Application exceptions
│   └── config/              # Runtime configuration
└── src/main/resources/
    ├── config.properties.example
    └── sql/schema.sql
```

## Getting Started

### Prerequisites

- JDK 21+
- Maven 3.8+
- MySQL 8+

### 1. Configure MySQL

Run the schema supplied with the repository:

```bash
mysql -u root -p < src/main/resources/sql/schema.sql
```

**Do not use any seed credentials from the repository outside local development.** For a real deployment, create a dedicated database user with a unique password and minimum required privileges.

### 2. Configure the application

Copy the example configuration:

```bash
cp src/main/resources/config.properties.example src/main/resources/config.properties
```

Update the local database connection values as required.

`config.properties` should remain gitignored and must never contain production secrets committed to Git.

### 3. Build

```bash
mvn compile
```

### 4. Start the server

```bash
mvn exec:java -Dexec.mainClass="com.chatapp.server.ChatServer"
```

## Test the Authentication Flow

With the server running, use the CLI client to exercise registration and login:

```bash
java -cp target/classes com.chatapp.client.TestClient register alice alice@example.com YOUR_LOCAL_PASSWORD YOUR_LOCAL_PASSWORD
java -cp target/classes com.chatapp.client.TestClient login alice YOUR_LOCAL_PASSWORD
java -cp target/classes com.chatapp.client.TestClient ping
```

Never replace `YOUR_LOCAL_PASSWORD` with a real production credential in documentation or source control.

## TCP Message Framing

The project uses a length-prefixed JSON envelope:

```text
[ 4-byte payload length ][ UTF-8 JSON payload ]
```

This framing is important because TCP is a byte stream rather than a message-oriented protocol. A single read may contain multiple application messages, or only part of one message. The explicit length prefix allows the receiver to reconstruct complete messages reliably.

## Roadmap

- [x] Phase 1 — Foundation and authentication
- [ ] Phase 2 — Private messaging and presence
- [ ] Phase 3 — Group chat and membership controls
- [ ] Phase 4 — JavaFX client
- [ ] Phase 5 — File sharing, search, notifications, diagrams and deployment documentation

## Project Value

This project demonstrates practical **Java backend engineering, TCP networking, protocol design, authentication, database access, secure password handling, and incremental system architecture**.

## Author

**Pankaj (Tony) Kumar**  
AI Engineer • Full Stack Developer • Generative AI & RAG Specialist

[GitHub](https://github.com/hack2ai) • [LinkedIn](https://www.linkedin.com/in/pankaj-kumar-ab591a216)

## License

MIT — see [LICENSE](LICENSE).
