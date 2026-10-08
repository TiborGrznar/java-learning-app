# Java Learning App

Interactive web application for learning the Java programming language. Each learning module combines theory, a quiz and a coding task. Students track their progress, teachers follow their classes, administrators manage schools and users.

Bachelor's thesis project at the Faculty of Natural Sciences, Matej Bel University in Banská Bystrica.

## Status

Work in progress.

| Area | State |
|------|-------|
| Database schema (Flyway) and JPA entities | Done |
| Authentication (JWT access token + rotating refresh token) | Done |
| Administrator: school and user management | Done |
| Teacher: student overview and deactivation of students | Planned |
| Class management and student enrollment | Planned |
| Quizzes, coding tasks, progress tracking | Planned |
| Sandboxed execution of student code | Planned |
| Frontend (React + TypeScript) | Not started |

## API

All endpoints are prefixed with `/api/v1`. Everything except `/auth/**` requires a valid access token in the `Authorization: Bearer <token>` header. The `Access` column shows who may call the endpoint.

| Method | Path | Access | Description |
|--------|------|--------|-------------|
| POST | `/auth/register` | public | Creates a student account (`201`). The role is always `STUDENT` |
| POST | `/auth/login` | public | Returns an access token (15 min) and a refresh token (7 days) |
| POST | `/auth/refresh` | public | Exchanges a refresh token for a new token pair, the old refresh token is revoked. A deactivated account is refused (`ACCOUNT_DISABLED`) |
| POST | `/auth/logout` | public | Revokes the given refresh token (`204`) |
| GET | `/users/me` | any signed-in user | Profile of the signed-in user |
| POST | `/schools` | `ADMIN` | Creates a school (`201`). The name must be unique |
| GET | `/schools` | `ADMIN` | Lists schools |
| GET | `/schools/{id}` | `ADMIN` | Detail of a school |
| PUT | `/schools/{id}` | `ADMIN` | Replaces the name and the address of a school |
| PATCH | `/schools/{id}/active` | `ADMIN` | Deactivates or reactivates a school (schools are never deleted) |
| POST | `/users` | `ADMIN` | Creates a teacher or an administrator (`201`). A teacher needs an active school. The response carries a generated temporary password, shown only once (`Cache-Control: no-store`) |
| GET | `/users` | `ADMIN` | Page of users. Filters `schoolId`, `role`, `active`; paging `page` (from 0, default 0) and `size` (default 20, at most 100); sorted by surname, name, id |
| GET | `/users/{id}` | `ADMIN` | Detail of a user |
| PUT | `/users/{id}` | `ADMIN` | Replaces name, surname, e-mail and school. The role and the active flag have their own endpoints |
| PATCH | `/users/{id}/role` | `ADMIN` | Changes the role. Changing to `TEACHER` needs a school |
| PATCH | `/users/{id}/active` | `ADMIN` | Deactivates or reactivates an account. Deactivation also revokes all refresh tokens of the user |

Rules of user management: an administrator cannot change their own role or deactivate themselves, and the last active administrator cannot be demoted or deactivated. Students register themselves, administrators never create them. There is no password change yet, so the temporary password of a new teacher stays valid until that is implemented.

Errors use a uniform JSON body. `code` is a stable identifier for client logic, `message` is a Slovak text for the user, `fieldErrors` is present only for validation errors:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Formulár obsahuje chyby",
  "fieldErrors": { "email": "E-mail nemá platný formát" }
}
```

| Status | Code | Meaning |
|--------|------|---------|
| 400 | `VALIDATION_ERROR` | A field is missing or invalid, details in `fieldErrors` |
| 400 | `INVALID_REQUEST_BODY` | The body is not valid JSON or contains an unknown value (e.g. an unknown role) |
| 400 | `PASSWORD_MISMATCH` | Password and its confirmation differ |
| 401 | `INVALID_CREDENTIALS` | Wrong e-mail or password (the same answer for both) |
| 401 | `INVALID_REFRESH_TOKEN` | The refresh token is unknown, expired or revoked |
| 401 | `INVALID_TOKEN` | The access token is malformed or expired |
| 401 | `UNAUTHENTICATED` | No access token was sent |
| 403 | `ACCOUNT_DISABLED` | The account is deactivated |
| 403 | `ACCESS_DENIED` | The role of the user does not allow the operation |
| 404 | `SCHOOL_NOT_FOUND` | The school does not exist |
| 404 | `USER_NOT_FOUND` | The user does not exist |
| 409 | `EMAIL_ALREADY_EXISTS` | The e-mail is already registered |
| 409 | `SCHOOL_NAME_ALREADY_EXISTS` | A school with the same name exists |
| 409 | `SCHOOL_INACTIVE` | The school is deactivated and cannot get new users |
| 409 | `LAST_ADMIN` | The last active administrator cannot be removed |
| 409 | `SELF_MODIFICATION_NOT_ALLOWED` | An administrator cannot change their own role or deactivate themselves |

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

## Continuous integration

GitHub Actions (`.github/workflows/ci.yml`) builds the backend and runs all tests on every push to any branch, on pull requests to `main` and on demand. The job uses a MySQL 8 service container with throwaway credentials and a `JWT_SECRET` generated for each run, so no repository secrets are needed. A newer run of the same branch or pull request cancels the older one, runs on `main` are never cancelled.

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
├── .github/workflows/    CI (GitHub Actions)
├── backend/              Spring Boot application
│   └── src/
│       ├── main/
│       │   ├── java/com/tgrznar/javalearningapp/
│       │   │   ├── auth/         authentication
│       │   │   │   ├── dto/          request and response records
│       │   │   │   ├── exception/    authentication exceptions
│       │   │   │   ├── security/     JWT service, filter, security config, 401/403 JSON handlers
│       │   │   │   └── token/        refresh token entity, repository and service
│       │   │   ├── common/       global exception handler, error body, page response
│       │   │   ├── user/
│       │   │   │   ├── model/        user entity, role, repository
│       │   │   │   ├── admin/        user management by an administrator
│       │   │   │   ├── dto/          request and response records
│       │   │   │   ├── exception/    user exceptions
│       │   │   │   └── bootstrap/    creation of the first administrator at startup
│       │   │   ├── school/       school entity, repository, service, controller
│       │   │   │   ├── dto/
│       │   │   │   └── exception/
│       │   │   ├── module/ quizquestion/ codingtask/
│       │   │   └── quizresult/ codingresult/ userprogress/
│       │   └── resources/
│       │       ├── application.yaml
│       │       └── db/migration/ Flyway migrations (V1, V2, ...)
│       └── test/             unit and integration tests
├── docker-compose.yml    local MySQL
└── .env.example          template of required environment variables
```

Tests lie in the package of the tested class (they need package-private access in some cases). The API integration tests of a feature are in an `api` subpackage, one class per operation, with a shared abstract test support class.

## Database migrations

Schema changes are managed by Flyway. An applied migration is never edited, every change is a new versioned file (`V4__...sql`). Foreign keys use `ON DELETE RESTRICT`, records with dependent data are soft-deleted through the `is_active` flag.

## Development workflow

- GitHub Flow: feature branch, pull request, squash and merge into `main`, delete the branch.
- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `build:`, `refactor:`, `test:`, `docs:`, `ci:`).
- Dependency and build changes are committed separately from feature code.
- Files are staged by explicit path, not with `git add .`.