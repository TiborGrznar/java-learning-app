# Java Learning App

Interactive web application for learning the Java programming language. Each learning module combines theory, a quiz and a coding task. Students track their progress, teachers follow their classes, administrators manage schools and users.

Bachelor's thesis project at the Faculty of Natural Sciences, Matej Bel University in Banská Bystrica.

## Status

Work in progress.

| Area | State |
|------|-------|
| Database schema (Flyway) and JPA entities | Done |
| Authentication (JWT access token + rotating refresh token) | Done |
| User, school and class management | Planned |
| Quizzes, coding tasks, progress tracking | Planned |
| Sandboxed execution of student code | Planned |
| Frontend (React + TypeScript) | Not started |

## API

All endpoints are prefixed with `/api/v1`. Everything except `/auth/**` requires a valid access token in the `Authorization: Bearer <token>` header.

| Method | Path | Description |
|--------|------|-------------|
| POST | `/auth/register` | Creates a student account (`201`). The role is always `STUDENT` |
| POST | `/auth/login` | Returns an access token (15 min) and a refresh token (7 days) |
| POST | `/auth/refresh` | Exchanges a refresh token for a new token pair, the old refresh token is revoked |
| POST | `/auth/logout` | Revokes the given refresh token (`204`) |

Errors use a uniform JSON body. `code` is a stable identifier for client logic, `message` is a Slovak text for the user, `fieldErrors` is present only for validation errors:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Formulár obsahuje chyby",
  "fieldErrors": { "email": "E-mail nemá platný formát" }
}
```

## Tech stack

- **Backend:** Java 25, Spring Boot 4.1, Spring Data JPA, Spring Security, JJWT, Lombok, Maven
- **Database:** MySQL 8 (Docker for local development), Flyway migrations
- **Frontend (planned):** React, TypeScript, Vite, Monaco Editor

Architecture: three layers (presentation, application, data) with role-based access control (student, teacher, admin).

## Prerequisites

- JDK 25
- Docker (for the local MySQL container)
- Git

Maven is not required, the repository contains the Maven Wrapper.

## Getting started

### 1. Configure environment variables

```
cp .env.example .env
```

Fill in the values in `.env` (it is git-ignored and must never be committed):

| Variable | Description |
|----------|-------------|
| `DB_NAME` | Database name |
| `DB_USER` | Application database user |
| `DB_PASSWORD` | Password of the application user |
| `DB_ROOT_PASSWORD` | MySQL root password (used by the Docker container) |
| `DB_PORT` | Port published by the container (default `3306`) |
| `JWT_SECRET` | Base64-encoded HMAC key, at least 256 bits. Required, there is no default |
| `JWT_ACCESS_TTL` | Optional, access token lifetime (default `15m`) |
| `JWT_REFRESH_TTL` | Optional, refresh token lifetime (default `7d`) |
| `ADMIN_EMAIL` | Optional, e-mail of the first administrator (see [First administrator](#first-administrator)) |
| `ADMIN_PASSWORD` | Optional, password of the first administrator: at least 12 characters, at most 72 bytes. Remove it from the environment after the first start |
| `ADMIN_NAME` | Optional, first name of the first administrator (default `Admin`) |
| `ADMIN_SURNAME` | Optional, surname of the first administrator (default `Administrátor`) |

Generate a `JWT_SECRET` (PowerShell):

```
$b = New-Object byte[] 32; (New-Object Security.Cryptography.RNGCryptoServiceProvider).GetBytes($b); [Convert]::ToBase64String($b)
```

Linux/macOS: `openssl rand -base64 32`

### 2. Start the database

```
docker compose up -d
```

Wait until the container reports `healthy`. Flyway creates the schema automatically when the application starts.

### 3. Run the backend

The variables from `.env` are read by Docker Compose only, the application expects them in its environment. In IntelliJ IDEA add them to the run configuration of the application (and to the JUnit configuration template for tests). In PowerShell you can load them into the current session:

```
Get-Content .\.env | ForEach-Object { if ($_ -match '^\s*([^#=\s]+)\s*=\s*(.*)$') { [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process') } }
cd backend
.\mvnw.cmd spring-boot:run
```

The backend listens on `http://localhost:8080`. The active profile is `dev` by default.

### 4. Run the tests

```
cd backend
.\mvnw.cmd test
```

The tests need the database container and the environment variables from step 3 (including `JWT_SECRET`). The integration tests run against the local development database, every test is rolled back, so no data is left behind.

## First administrator

Only an administrator can create teachers and other administrators, so the first one is created at startup from the environment:

1. Set `ADMIN_EMAIL` and `ADMIN_PASSWORD` (optionally also `ADMIN_NAME` and `ADMIN_SURNAME`) before starting the backend.
2. If the database contains no administrator, the account is created and the log shows `Created the first administrator account ...`. If an administrator already exists, the variables are ignored: an existing account is never changed or replaced, and an existing student or teacher account is never promoted.
3. Log in with `POST /api/v1/auth/login`.
4. Remove `ADMIN_PASSWORD` from the environment. The password is stored only as a hash, the variable is not needed any more.

The application refuses to start when the configuration is unusable: only one of `ADMIN_EMAIL` and `ADMIN_PASSWORD` is set, the e-mail is malformed, the password has fewer than 12 characters or more than 72 bytes (characters with diacritics take more than one byte), or an account with that e-mail already exists.

**Recovery.** There is no password reset or password change yet. If no administrator can log in, delete the administrator in the database and start the application again with `ADMIN_EMAIL` and `ADMIN_PASSWORD` set:

```sql
DELETE FROM users WHERE role = 'admin';
```

Never put the real administrator password into shell commands or tools such as Postman: the shell history and cloud sync keep it in plain text. Use a throwaway password for local development.

## Project structure

```
.
├── backend/              Spring Boot application
│   └── src/
│       ├── main/
│       │   ├── java/com/tgrznar/javalearningapp/
│       │   │   ├── auth/         authentication
│       │   │   │   ├── dto/          request and response records
│       │   │   │   ├── exception/    authentication exceptions
│       │   │   │   ├── security/     JWT service, filter, security config, 401/403 JSON handlers
│       │   │   │   └── token/        refresh token entity, repository and service
│       │   │   ├── common/       global exception handler and error body
│       │   │   ├── user/         user entity, repository, profile endpoint
│       │   │   │   └── bootstrap/    creation of the first administrator at startup
│       │   │   ├── school/ module/ quizquestion/ codingtask/
│       │   │   └── quizresult/ codingresult/ userprogress/
│       │   └── resources/
│       │       ├── application.yaml
│       │       └── db/migration/ Flyway migrations (V1, V2, ...)
│       └── test/             unit and integration tests, same package layout
├── docker-compose.yml    local MySQL
└── .env.example          template of required environment variables
```

Each domain package contains its JPA entity and Spring Data repository.

## Database migrations

Schema changes are managed by Flyway. An applied migration is never edited, every change is a new versioned file (`V3__...sql`). Foreign keys use `ON DELETE RESTRICT`, records with dependent data are soft-deleted through the `is_active` flag.

## Development workflow

- GitHub Flow: feature branch, pull request, squash and merge into `main`, delete the branch.
- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `build:`, `refactor:`, `test:`, `docs:`).
- Dependency and build changes are committed separately from feature code.
- Files are staged by explicit path, not with `git add .`.