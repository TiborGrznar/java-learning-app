# Java Learning App

Interactive web application for learning the Java programming language. Each learning module combines theory, a quiz and a coding task. Students track their progress, teachers follow their classes, administrators manage schools, classes and users.

Bachelor's thesis project at the Faculty of Natural Sciences, Matej Bel University in Banská Bystrica.

## Status

Work in progress.

| Area | State |
|------|-------|
| Database schema (Flyway) and JPA entities | Done |
| Authentication (JWT access token + rotating refresh token) | Done |
| Administrator: school, user, class, module and quiz question management | Done |
| Classes and student enrollment with a join code | Done |
| Teacher: own classes and their join codes | Done |
| Student: modules with step-by-step unlocking, theory, quiz | Done |
| Rate limiting and request size limits | Done |
| Teacher: student overview and deactivation of students | Planned |
| Coding tasks, progress overviews and archiving | Planned |
| Sandboxed execution of student code | Planned |
| Frontend (React + TypeScript) | Not started |

## API

All endpoints are prefixed with `/api/v1`. Everything except the four public `/auth` endpoints below requires a valid access token in the `Authorization: Bearer <token>` header. The `Access` column shows who may call the endpoint.

| Method | Path | Access | Description |
|--------|------|--------|-------------|
| POST | `/auth/register` | public | Creates a student account (`201`). The role is always `STUDENT` |
| POST | `/auth/login` | public | Returns an access token (15 min) and a refresh token (7 days) |
| POST | `/auth/refresh` | public | Exchanges a refresh token for a new token pair, the old refresh token is revoked. A refresh token works once: if two requests send the same token at the same time, exactly one gets a new pair and the other one gets `401`. A deactivated account is refused (`ACCOUNT_DISABLED`) |
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
| PUT | `/users/{id}` | `ADMIN` | Replaces name, surname, e-mail and school. The role and the active flag have their own endpoints. A student whose school changes leaves the class |
| PATCH | `/users/{id}/role` | `ADMIN` | Changes the role. Changing to `TEACHER` needs a school. A student who changes role leaves the class |
| PATCH | `/users/{id}/active` | `ADMIN` | Deactivates or reactivates an account. Deactivation also revokes all refresh tokens of the user |
| DELETE | `/users/{id}?confirmEmail=...` | `ADMIN` | Deletes a student for good (`204`) together with their progress, results and tokens. `confirmEmail` must repeat the student's e-mail. Teachers and administrators cannot be deleted |
| POST | `/modules` | `ADMIN` | Creates a learning module |
| GET | `/modules` | `ADMIN` | Lists modules |
| GET | `/modules/{id}` | `ADMIN` | Detail of a module |
| PUT | `/modules/{id}` | `ADMIN` | Replaces a module |
| PATCH | `/modules/{id}/active` | `ADMIN` | Deactivates or reactivates a module (modules are never deleted) |
| POST | `/modules/{moduleId}/questions` | `ADMIN` | Adds a quiz question to a module |
| GET | `/modules/{moduleId}/questions` | `ADMIN` | Lists the questions of a module |
| GET | `/modules/{moduleId}/questions/{id}` | `ADMIN` | Detail of a question |
| PUT | `/modules/{moduleId}/questions/{id}` | `ADMIN` | Replaces a question |
| DELETE | `/modules/{moduleId}/questions/{id}` | `ADMIN` | Deletes a question |
| POST | `/classes` | `ADMIN` | Creates a class with a generated join code |
| GET | `/classes` | `ADMIN` | Lists classes. Filters `schoolId`, `teacherId`, `active` |
| GET | `/classes/{id}` | `ADMIN` | Detail of a class, including the join code |
| PUT | `/classes/{id}` | `ADMIN` | Replaces the name and the teacher of a class. The school of a class never changes |
| PATCH | `/classes/{id}/active` | `ADMIN` | Deactivates or reactivates a class (classes are never deleted) |
| POST | `/classes/{id}/join-code` | `ADMIN` | Generates a new join code, the old one stops working |
| GET | `/teacher/classes` | `TEACHER` | Classes led by the signed-in teacher. Filter `active` |
| GET | `/teacher/classes/{id}` | `TEACHER` | Detail of an own class, including the join code. A class of another teacher answers `404 CLASS_NOT_FOUND` |
| POST | `/teacher/classes/{id}/join-code` | `TEACHER` | Generates a new join code of an own class |
| POST | `/enrollments` | `STUDENT` | Joins the class with the given `joinCode` (`200`). See the class rules below |
| GET | `/learning/modules` | `STUDENT` in a class | Active modules in course order with `locked` and `status`, without the theory text |
| GET | `/learning/modules/{id}` | `STUDENT` in a class | Module with the theory text and progress flags. A locked module answers `403 MODULE_LOCKED` |
| POST | `/learning/modules/{id}/theory-completed` | `STUDENT` in a class | Marks the theory as read and returns the module |
| GET | `/learning/modules/{moduleId}/quiz` | `STUDENT` in a class | Draws a quiz of up to ten questions and a quiz token (`Cache-Control: no-store`). The theory must be read first |
| POST | `/learning/modules/{moduleId}/quiz/submissions` | `STUDENT` in a class | Evaluates the answers of a drawn quiz. A quiz token works for one submission only. After a failed attempt the answers are marked right or wrong, the correct options are shown after a pass |

Rules of user management: an administrator cannot change their own role or deactivate themselves, and the last active administrator cannot be demoted or deactivated. Students register themselves, administrators never create them. There is no password change yet, so the temporary password of a new teacher stays valid until that is implemented.

Deactivation and deletion of a student are different on purpose. **Deactivation** pauses the account (a postponed or repeated year): the student keeps their progress and the administrator can reactivate the account. **Deletion** is for a student who leaves the school: the account, progress, quiz results, coding results and tokens are removed for good and the e-mail can be registered again. A teacher who leads an active class cannot be deactivated, demoted or moved to another school, the administrator has to reassign or deactivate the class first.

Rules of classes and enrollment:

- A class belongs to one school and is led by one teacher of that school. Only administrators create classes. A class is deactivated, never deleted. Deactivating a class only closes it to new students, the students already in it keep their access.
- The join code has 8 characters and is shown only to administrators and to the teacher of the class. Generating a new code invalidates the old one. An unknown code, a deactivated class and a deactivated school all answer the same `404 INVALID_JOIN_CODE`.
- A student joins with the code and gets the school of the class. A student who is already in a class answers `409 ALREADY_ENROLLED`, a student whose school differs from the school of the class answers `409 SCHOOL_MISMATCH`.
- After 5 failed attempts within 15 minutes a student answers `429 TOO_MANY_ATTEMPTS` with a `Retry-After` header. The counter lives in memory of one application instance.
- A student who has not joined a class gets `403 NOT_ENROLLED` on everything under `/learning/**`. Teachers and administrators get `403 ACCESS_DENIED` there.
- A module is unlocked when it is the first active one, when the student passed the quiz of the previous active module, or when the student already has progress in it.

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
| 400 | `INVALID_QUIZ_TOKEN` | The quiz token is malformed or was issued for someone else, load the quiz again |
| 413 | `PAYLOAD_TOO_LARGE` | The request body exceeds the allowed size, a client bug |
| 401 | `INVALID_CREDENTIALS` | Wrong e-mail or password (the same answer for both) |
| 401 | `INVALID_REFRESH_TOKEN` | The refresh token is unknown, expired, revoked or already used. Also returned after the account was deactivated, because deactivation revokes all its refresh tokens |
| 401 | `INVALID_TOKEN` | The access token is valid, but its user no longer exists |
| 401 | `UNAUTHENTICATED` | No access token was sent, or it is invalid or expired |
| 403 | `ACCOUNT_DISABLED` | The account is deactivated |
| 403 | `ACCESS_DENIED` | The role of the user does not allow the operation |
| 403 | `MODULE_LOCKED` | The quiz of the previous module has not been passed yet |
| 403 | `NOT_ENROLLED` | The student has not joined a class yet |
| 404 | `SCHOOL_NOT_FOUND` | The school does not exist |
| 404 | `USER_NOT_FOUND` | The user does not exist |
| 404 | `MODULE_NOT_FOUND` | The module does not exist or is deactivated |
| 404 | `QUESTION_NOT_FOUND` | The question does not exist in the given module |
| 404 | `CLASS_NOT_FOUND` | The class does not exist (or belongs to another teacher) |
| 404 | `INVALID_JOIN_CODE` | No active class of an active school has this join code |
| 409 | `EMAIL_ALREADY_EXISTS` | The e-mail is already registered |
| 409 | `SCHOOL_NAME_ALREADY_EXISTS` | A school with the same name exists |
| 409 | `SCHOOL_INACTIVE` | The school is deactivated and cannot get new users |
| 409 | `LAST_ADMIN` | The last active administrator cannot be removed |
| 409 | `SELF_MODIFICATION_NOT_ALLOWED` | An administrator cannot change their own role or deactivate themselves |
| 409 | `USER_NOT_DELETABLE` | Only a student can be deleted, a teacher or an administrator is deactivated |
| 409 | `CLASS_NAME_ALREADY_EXISTS` | The school already has a class with this name |
| 409 | `TEACHER_LEADS_CLASSES` | The teacher leads an active class, reassign or deactivate the class first |
| 409 | `ALREADY_ENROLLED` | The student already belongs to a class |
| 409 | `SCHOOL_MISMATCH` | The school of the student differs from the school of the class |
| 409 | `THEORY_NOT_COMPLETED` | The theory of the module has not been marked as read yet |
| 409 | `QUIZ_NOT_AVAILABLE` | The module has no quiz questions |
| 409 | `QUIZ_OUTDATED` | The quiz expired or one of its questions was deleted, load it again |
| 409 | `QUIZ_ALREADY_SUBMITTED` | The quiz token was already used for a submission (double click, retry), load the quiz again |
| 429 | `TOO_MANY_ATTEMPTS` | Too many failed enrollment attempts, the `Retry-After` header says how many seconds to wait |
| 429 | `RATE_LIMITED` | Too many requests from this address, for this e-mail or from this student, the `Retry-After` header says how many seconds to wait |

## Rate limiting and request size limits

Limits are kept in memory (`ratelimit/RateLimiter`, a sliding window), so they reset on restart and are not shared between application instances. Replace the limiter before running several instances.

| What | Key | Default |
|------|-----|---------|
| `POST /auth/login` | client IP | 10 per 15 minutes |
| `POST /auth/register` | client IP | 5 per hour |
| `POST /auth/refresh` | client IP | 30 per minute |
| any other request under `/api/v1/` | client IP | 600 per minute |
| failed logins | hash of the e-mail, any IP | 5 per 15 minutes |
| quiz load | student id | 20 per minute |

All values are configurable under `app.rate-limit.*`. A limit answers `429 RATE_LIMITED` with a `Retry-After` header. The number of tracked keys is bounded, so forged addresses cannot exhaust memory.

**Client IP.** By default the address of the TCP connection is used. `X-Forwarded-For` is believed only when the connection comes from a proxy listed in `RATE_LIMIT_TRUSTED_PROXIES` (exact addresses, comma separated). The rightmost entry that is not a trusted proxy is the client. Set it before deploying behind a reverse proxy, otherwise all users share the proxy's address.

**Body size.** Request bodies under `/api/` are limited to 16 KiB, and to 1 MiB under the prefixes in `app.rate-limit.large-body-prefixes` (module theory). Larger bodies get `413 PAYLOAD_TOO_LARGE`, including bodies sent without a `Content-Length`.

Known trade-offs: anyone who knows an e-mail can block logins to it for the length of the window (only failures count and a successful login clears the counter, so the block is short). A school behind one public address shares the registration and general limits, raise them in the configuration if needed.

## Tech stack

- **Backend:** Java 25, Spring Boot 4.1, Spring Data JPA, Spring Security, JJWT, Lombok, Maven
- **Database:** MySQL 8.4 LTS (Docker for local development), Flyway migrations
- **Frontend (planned):** React, TypeScript, Vite, Monaco Editor

Architecture: three layers (presentation, application, data) with role-based access control (student, teacher, admin).

## Prerequisites

- JDK 25
- Docker (for the local MySQL container and for the tests, which start their own MySQL container)
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
| `RATE_LIMIT_TRUSTED_PROXIES` | Optional, comma separated addresses of reverse proxies whose `X-Forwarded-For` header is believed (default empty: never) |

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

The tests need a running Docker, nothing else: no database container from step 2 and no environment variables. The integration tests start their own throwaway MySQL 8.4 container with [Testcontainers](https://java.testcontainers.org/) (one container for the whole run, removed afterwards) and Flyway applies all migrations to it from scratch, so the development database is never touched. Most tests are rolled back, so they do not depend on each other. The concurrency tests (single-use quiz token, atomic refresh token rotation) cannot run in one transaction, because parallel requests must see committed data, so each of them removes its own data in the end. A test-only JWT secret lives in `backend/src/test/resources/application-dev.yaml`.

## Continuous integration

GitHub Actions (`.github/workflows/ci.yml`) builds the backend and runs all tests on every push to any branch, on pull requests to `main` and on demand. The job needs no database service and no secrets: the tests start their own MySQL container with Testcontainers (Docker is preinstalled on the runner) and use the test-only configuration from `src/test/resources`. A newer run of the same branch or pull request cancels the older one, runs on `main` are never cancelled.

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
│       │   │   ├── common/       global exception handler, error body, page response, MVC configuration
│       │   │   ├── ratelimit/    sliding-window limiter, per-IP filter, body size filter, client IP resolution
│       │   │   ├── user/
│       │   │   │   ├── model/        user entity, role, repository
│       │   │   │   ├── admin/        user management by an administrator
│       │   │   │   ├── dto/          request and response records
│       │   │   │   ├── exception/    user exceptions
│       │   │   │   └── bootstrap/    creation of the first administrator at startup
│       │   │   ├── school/       school entity, repository, service, controller
│       │   │   │   ├── dto/
│       │   │   │   └── exception/
│       │   │   ├── schoolclass/  classes and join codes
│       │   │   │   ├── admin/        class management by an administrator
│       │   │   │   ├── teacher/      classes of a teacher
│       │   │   │   ├── enrollment/   joining a class, attempt limit, gate for /learning/**
│       │   │   │   ├── dto/
│       │   │   │   └── exception/
│       │   │   ├── module/       course modules (admin/ holds their management)
│       │   │   ├── quizquestion/ quiz questions (admin/ holds their management)
│       │   │   ├── learning/     the student's view: modules, theory, progress
│       │   │   │   └── quiz/         quiz draw and evaluation
│       │   │   └── codingtask/ quizresult/ codingresult/ userprogress/
│       │   └── resources/
│       │       ├── application.yaml
│       │       └── db/migration/ Flyway migrations (V1 to V6)
│       └── test/             unit and integration tests, testsupport/ holds the Testcontainers setup and shared fixtures
├── docker-compose.yml    local MySQL
└── .env.example          template of required environment variables
```

Tests lie in the package of the tested class (they need package-private access in some cases). The API integration tests of a feature are in an `api` subpackage, one class per operation, with a shared abstract test support class.

## Database migrations

Schema changes are managed by Flyway. An applied migration is never edited, every change is a new versioned file (`V7__...sql`). Foreign keys use `ON DELETE RESTRICT`, records with dependent data are soft-deleted through the `is_active` flag. Two exceptions use `ON DELETE CASCADE`: `refresh_tokens` (session data, V2) and the learning data of a student (`user_progress`, `quiz_results`, `coding_results`, V5), so that deleting a student removes it. A new table that holds a student's data must follow the same rule.

## Development workflow

- GitHub Flow: feature branch, pull request, squash and merge into `main`, delete the branch.
- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `build:`, `refactor:`, `test:`, `docs:`, `ci:`).
- Dependency and build changes are committed separately from feature code.
- Files are staged by explicit path, not with `git add .`.
