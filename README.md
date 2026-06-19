# Real-Time Chat Application (Java Socket Programming)

A real-time chat application supporting private and group messaging, built with **Java Socket Programming**, **JavaFX**, and **MySQL**.

> **Project status: Phase 1 of 5 — Foundation.**
> This repo currently contains the project skeleton, database layer, JSON wire protocol, and a fully working **authentication system** (register / login / logout) over raw sockets. There is **no chat UI yet** — private messaging, group messaging, and the JavaFX client are planned for later phases (see [Roadmap](#roadmap) below). If you're looking at this repo expecting a finished chat app, it isn't one yet.

### ✅ Verified end-to-end (not just "should work")

Phase 1 was actually run, not just compiled: a real `ChatServer` process, against a real MySQL-protocol-compatible database, exercised with a real client. Confirmed working:

- Register a new user → password hashed with real bcrypt → row written to a real database
- Login with correct password → success + session token
- Login with wrong password → correctly rejected
- Login with a username that doesn't exist → rejected with the **same generic message** as wrong password (prevents account enumeration)
- Duplicate registration → correctly rejected
- Seeded `admin` account logs in with the password documented in this README
- Full server-side connect → authenticate → online/offline tracking → disconnect lifecycle, no errors in the logs

> **One known caveat:** this verification used Bouncy Castle (bcrypt) and the MariaDB JDBC driver as stand-ins for the exact libraries declared in `pom.xml` (Spring Security Crypto, MySQL Connector/J), because Maven Central wasn't reachable in the sandbox used to build this. Both pairs implement the same algorithms/wire protocols, and the bcrypt hashes were cross-checked against an independently-generated Python bcrypt hash to confirm compatibility — but the *exact* dependency versions in `pom.xml` have not yet been compiled together in one run. **If you run `mvn compile` on your own machine and it doesn't work cleanly, please open an issue** — that's the one gap this hasn't closed yet.

---

## Table of Contents

- [What's implemented so far](#whats-implemented-so-far)
- [Tech stack](#tech-stack)
- [Project structure](#project-structure)
- [Getting started](#getting-started)
- [Trying it out](#trying-it-out)
- [Wire protocol](#wire-protocol)
- [Security notes](#security-notes)
- [Roadmap](#roadmap)
- [License](#license)

---

## What's implemented so far

- ✅ Maven project structure (Java 21)
- ✅ MySQL 8 schema: `users`, `private_messages`, `chat_groups`, `group_members`, `group_messages`
- ✅ JDBC connection pool (hand-rolled, no external pooling library)
- ✅ `UserDAO` — fully parameterized queries (SQL-injection safe)
- ✅ Length-prefixed JSON wire protocol (`Envelope` + `MessageCodec`) — solves the classic "TCP is a stream, not a sequence of messages" framing bug
- ✅ `AuthenticationService` — registration, login, logout, session tokens
  - Passwords hashed with **bcrypt** (`BCryptPasswordEncoder`, cost factor 12)
  - Generic "invalid username/email or password" error on failed login (prevents username enumeration), with a constant-time-ish comparison path even when the identifier doesn't exist
- ✅ `ChatServer` / `ClientHandler` — multithreaded socket server, one thread per connected client, full register/login/logout message dispatch
- ✅ `TestClient` — bare CLI client for exercising the auth flow without needing the (not-yet-built) JavaFX UI

**Not yet implemented:** private messaging delivery, group chat, JavaFX client/UI, message history retrieval, typing indicators, read receipts, file sharing, emoji picker, search, notifications, admin panel, UML diagrams, deployment guide. See [Roadmap](#roadmap).

---

## Tech stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Networking | Java Socket Programming (`java.net.Socket`, `ServerSocket`), multithreaded |
| Wire format | JSON (Gson), length-prefixed framing |
| Database | MySQL 8 |
| Data access | Plain JDBC (no ORM), hand-rolled connection pool |
| Password hashing | bcrypt via Spring Security Crypto (`spring-security-crypto`, standalone — not full Spring Framework) |
| Build | Maven |
| Frontend *(planned)* | JavaFX + CSS |

---

## Project structure

```
realtime-chat-app/
├── pom.xml
├── src/main/java/com/chatapp/
│   ├── client/            # ChatClient / TestClient (CLI test client for now)
│   ├── server/             # ChatServer, ClientHandler
│   ├── service/             # AuthenticationService (business logic)
│   ├── database/            # ConnectionPool, DatabaseManager, *DAO classes
│   ├── model/                # Domain objects: User, PrivateMessage, ChatGroup, ...
│   │   └── dto/                # Wire-format request/response payload classes
│   ├── socket/protocol/       # MessageType enum, Envelope, MessageCodec
│   ├── util/                    # ValidationUtil
│   ├── exception/                # ValidationException, AuthenticationException
│   └── config/                    # AppConfig (reads config.properties)
└── src/main/resources/
    ├── config.properties.example  # Copy to config.properties and fill in
    └── sql/schema.sql               # Run this against MySQL before first use
```

---

## Getting started

### Prerequisites

- **Java 21** (JDK, not just JRE — you need `javac` to build)
- **Maven 3.8+**
- **MySQL 8** running locally (or reachable over network)

### 1. Set up the database

```bash
mysql -u root -p < src/main/resources/sql/schema.sql
```

This creates the `chatapp_db` database, a dedicated `chatapp_user` MySQL user, all five tables, and seeds one admin account:

| Username | Password | Role |
|---|---|---|
| `admin` | `Admin@123` | ADMIN |

> ⚠️ This seed password is published in this repo's `schema.sql` for local development convenience. **Do not use this account or password if you deploy anywhere beyond your own machine** — change it immediately, or delete the seed `INSERT` from the script.

### 2. Configure the app

```bash
cp src/main/resources/config.properties.example src/main/resources/config.properties
```

Edit `config.properties` if your MySQL host/port/credentials differ from the defaults (`localhost:3306`, user `chatapp_user`, password `chatapp_pass` — matching what `schema.sql` creates out of the box).

> `config.properties` is gitignored on purpose — it's where real credentials live once you have any. Never commit it.

### 3. Build

```bash
mvn compile
```

> **Note on verification:** this project was developed and structurally verified (`javac` clean compile with all lint warnings enabled, plus targeted runtime tests of the validation logic and the message-framing protocol) in a sandboxed environment without direct access to Maven Central. If `mvn compile` surfaces anything unexpected on your machine — e.g. a dependency version conflict — please open an issue; it would be genuinely useful to know about.

### 4. Run the server

```bash
mvn exec:java -Dexec.mainClass="com.chatapp.server.ChatServer"
```

or, after packaging:

```bash
mvn package
java -jar target/chatapp-server.jar
```

You should see:
```
Chat server started on port 5050. Max concurrent clients: 200
```

---

## Trying it out

With the server running, use the bundled CLI test client to register and log in — no JavaFX UI needed yet:

```bash
# Register a new user
java -cp target/classes com.chatapp.client.TestClient register alice alice@example.com Passw0rd1 Passw0rd1

# Log in
java -cp target/classes com.chatapp.client.TestClient login alice Passw0rd1

# Basic connectivity check
java -cp target/classes com.chatapp.client.TestClient ping
```

A successful login prints the user ID, username, role, and a session token — proving the full register → hash → store → retrieve → verify → issue-token pipeline works end to end.

---

## Wire protocol

Every message between client and server is a length-prefixed JSON **envelope**:

```
[ 4 bytes: payload length N (big-endian int) ][ N bytes: UTF-8 JSON ]
```

```json
{
  "type": "C2S_LOGIN",
  "payload": { "usernameOrEmail": "alice", "password": "Passw0rd1" },
  "timestamp": 1750000000000
}
```

The 4-byte length prefix exists because raw TCP is a byte stream, not a message stream — without it, two quick messages can arrive concatenated in a single read, or one message can split across reads, either of which breaks naive JSON parsing intermittently and unpredictably. See `MessageCodec.java` for the full explanation and implementation.

All message types are defined once, canonically, in `MessageType.java` — shared by both client and server so they can never drift out of sync.

---

## Security notes

- All SQL queries use `PreparedStatement` parameter binding — no string-concatenated SQL anywhere in this codebase.
- Passwords are hashed with bcrypt (cost factor 12, configurable in `config.properties`) before ever reaching the database. Plaintext passwords are never logged or persisted.
- Login failures return an intentionally generic message regardless of whether the username/email didn't exist or the password was wrong, to avoid leaking which accounts exist.
- Session tokens are 256 bits of `SecureRandom` entropy, not `java.util.Random`.

This is a learning/portfolio project, not an audited production system — treat it accordingly if you extend it toward real deployment (e.g. you'd want persisted/revocable sessions, rate limiting on login attempts, and TLS on the socket layer, none of which are in scope yet).

---

## Roadmap

- [x] **Phase 1 — Foundation**: project structure, database, JSON protocol, authentication *(this release)*
- [ ] **Phase 2 — Private Chat**: real-time 1:1 messaging, online/offline presence, typing indicators, read receipts, message history
- [ ] **Phase 3 — Group Chat**: group creation/membership, group messaging, group admin controls
- [ ] **Phase 4 — JavaFX Client**: login/register screens, dashboard, chat windows, CSS styling
- [ ] **Phase 5 — Extras & polish**: file sharing, emoji support, search, notifications, UML diagrams, deployment guide

---

## License

MIT — see [LICENSE](LICENSE).
