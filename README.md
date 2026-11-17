# Java Learning App

Interactive web application for learning the Java programming language. Each learning module combines theory, a quiz and a coding task. Students track their progress, teachers follow their classes, administrators manage schools, classes and users.

Bachelor's thesis project at the Faculty of Natural Sciences, Matej Bel University in Banská Bystrica.

## Status

Work in progress.

| Area | State       |
|------|-------------|
| Database schema (Flyway) and JPA entities | Done        |
| Authentication (JWT access token + rotating refresh token in an HttpOnly cookie) | Done        |
| Administrator: school, user, class, module and quiz question management | Done        |
| Classes and student enrollment with a join code | Done        |
| Teacher: own classes and their join codes | Done        |
| Student: modules with step-by-step unlocking, theory, quiz | Done        |
| Rate limiting and request size limits | Done        |
| Teacher: student overview and deactivation of students | Done        |
| Administrator: coding tasks and their test cases | Done        |
| Student: solving coding tasks (submission, evaluation in the sandbox) | Done        |
| Student: progress overview | Done        |
| Sandboxed execution of student code | Done           |
| Password change (own) and password reset by an administrator | Done |
| Preparation for the frontend: opt-in CORS, OpenAPI description and Swagger UI | Done |
| Frontend (React + TypeScript) | Not started |

## API

All endpoints are prefixed with `/api/v1`. Everything except the four public `/auth` endpoints below requires a valid access token in the `Authorization: Bearer <token>` header. The `Access` column shows who may call the endpoint.

| Method | Path | Access | Description |
|--------|------|--------|-------------|
| POST | `/auth/register` | public | Creates a student account (`201`). The role is always `STUDENT` |
| POST | `/auth/login` | public | Returns an access token (15 min) in the body and sets the refresh token (7 days) in an HttpOnly cookie (see [Refresh token cookie](#refresh-token-cookie)). `Cache-Control: no-store`. The `Origin` header is checked |
| POST | `/auth/refresh` | public | Reads the refresh token from the cookie and answers like the login: a new access token in the body and a new cookie, the old refresh token is revoked. No body is needed. A refresh token works once: if two requests send the same token at the same time, exactly one gets a new pair and the other one gets `401`. No cookie: `401 INVALID_REFRESH_TOKEN`. A deactivated account is refused (`ACCOUNT_DISABLED`). The `Origin` header is checked |
| POST | `/auth/logout` | public | Revokes the refresh token from the cookie and clears the cookie (always `204`, also without a cookie or with an unknown one). The `Origin` header is checked |
| GET | `/users/me` | any signed-in user | Profile of the signed-in user |
| PUT | `/users/me/password` | any signed-in user | Changes the own password. Body `currentPassword`, `newPassword`, `confirmNewPassword`. Revokes all refresh tokens and answers like the login (a new access token in the body, a new refresh token cookie, `Cache-Control: no-store`), so the caller stays signed in and the other devices have to log in again |
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
| POST | `/users/{id}/password-reset` | `ADMIN` | Gives a user (a student, a teacher or another administrator, also a deactivated one) a generated temporary password and revokes all their refresh tokens. The password is in the response only once (`Cache-Control: no-store`) and is never logged. Own account: `409 SELF_MODIFICATION_NOT_ALLOWED` |
| DELETE | `/users/{id}?confirmEmail=...` | `ADMIN` | Deletes a student for good (`204`) together with their progress, results and tokens. `confirmEmail` must repeat the student's e-mail. Teachers and administrators cannot be deleted |
| POST | `/modules` | `ADMIN` | Creates a learning module (`201`). A new module is inactive until it gets an active coding task |
| GET | `/modules` | `ADMIN` | Lists modules |
| GET | `/modules/{id}` | `ADMIN` | Detail of a module |
| PUT | `/modules/{id}` | `ADMIN` | Replaces a module |
| PATCH | `/modules/{id}/active` | `ADMIN` | Deactivates or reactivates a module (modules are never deleted). Activation needs at least one active coding task, otherwise `409 MODULE_REQUIRES_ACTIVE_TASK` |
| POST | `/modules/{moduleId}/questions` | `ADMIN` | Adds a quiz question to a module |
| GET | `/modules/{moduleId}/questions` | `ADMIN` | Lists the questions of a module |
| GET | `/modules/{moduleId}/questions/{id}` | `ADMIN` | Detail of a question |
| PUT | `/modules/{moduleId}/questions/{id}` | `ADMIN` | Replaces a question |
| DELETE | `/modules/{moduleId}/questions/{id}` | `ADMIN` | Deletes a question |
| POST | `/modules/{moduleId}/coding-tasks` | `ADMIN` | Adds a coding task (`201`). A new task is inactive |
| GET | `/modules/{moduleId}/coding-tasks` | `ADMIN` | Lists the tasks of a module in course order with the number of test cases, without any code |
| GET | `/modules/{moduleId}/coding-tasks/{id}` | `ADMIN` | Detail of a task, including template, driver and reference solution |
| PUT | `/modules/{moduleId}/coding-tasks/{id}` | `ADMIN` | Replaces the content of a task. Changing the driver or the reference solution of an active task deactivates it (see the rules of coding tasks below); anything else keeps the active flag. The answer shows the resulting `active` |
| PATCH | `/modules/{moduleId}/coding-tasks/{id}/active` | `ADMIN` | Activates or deactivates a task. Activation needs a reference solution and at least one test case (`409 CODING_TASK_NOT_READY`), and the reference solution must pass every test case in the sandbox (`409 REFERENCE_SOLUTION_FAILED` with diagnostics in `details`, the task stays inactive; the call can take up to a minute). The last active task of an active module cannot be deactivated (`409 MODULE_REQUIRES_ACTIVE_TASK`) |
| DELETE | `/modules/{moduleId}/coding-tasks/{id}` | `ADMIN` | Deletes a task together with its test cases (`204`). A task with submitted results answers `409 CODING_TASK_HAS_RESULTS` |
| GET | `/modules/{moduleId}/coding-tasks/{id}/cases` | `ADMIN` | Lists the test cases with their expected outputs |
| PUT | `/modules/{moduleId}/coding-tasks/{id}/cases` | `ADMIN` | Replaces all test cases of a task (1 to 20). The case number is the position in the list. Changing an input or an expected output of an active task deactivates it; the answer lists only the cases, so read the task again |
| POST | `/classes` | `ADMIN` | Creates a class with a generated join code |
| GET | `/classes` | `ADMIN` | Lists classes. Filters `schoolId`, `teacherId`, `active` |
| GET | `/classes/{id}` | `ADMIN` | Detail of a class, including the join code |
| PUT | `/classes/{id}` | `ADMIN` | Replaces the name and the teacher of a class. The school of a class never changes |
| PATCH | `/classes/{id}/active` | `ADMIN` | Deactivates or reactivates a class (classes are never deleted) |
| POST | `/classes/{id}/join-code` | `ADMIN` | Generates a new join code, the old one stops working |
| GET | `/teacher/classes` | `TEACHER` | Classes led by the signed-in teacher. Filter `active` |
| GET | `/teacher/classes/{id}` | `TEACHER` | Detail of an own class, including the join code. A class of another teacher answers `404 CLASS_NOT_FOUND` |
| POST | `/teacher/classes/{id}/join-code` | `TEACHER` | Generates a new join code of an own class |
| GET | `/teacher/students` | `TEACHER` | Page of the students of the teacher's own classes with their state in every active module (the module matrix). Filters `classId`, `active`; paging `page` (from 0, default 0) and `size` (default 20, at most 100); sorted by class name, surname, name, id. A class that is not the teacher's own answers `404 CLASS_NOT_FOUND`. See the rules of the teacher's student overview below |
| GET | `/teacher/students/{studentId}/progress` | `TEACHER` | The progress of one student of the teacher's own classes: a short summary of the student and exactly what the student sees in `GET /learning/progress`. Any other id answers `404 USER_NOT_FOUND` |
| PATCH | `/teacher/students/active` | `TEACHER` | Deactivates or reactivates up to 100 students of the teacher's own classes at once: `{"studentIds": [...], "active": false}`. All or nothing, one id outside the scope answers `404 USER_NOT_FOUND` for the whole request. The answer lists the `changed` and the `unchanged` ids. Deactivation also revokes the refresh tokens of the changed students |
| POST | `/enrollments` | `STUDENT` | Joins the class with the given `joinCode` (`200`). See the class rules below |
| GET | `/learning/modules` | `STUDENT` in a class | Active modules in course order with `locked` and `status`, without the theory text |
| GET | `/learning/modules/{id}` | `STUDENT` in a class | Module with the theory text and progress flags. A locked module answers `403 MODULE_LOCKED` |
| POST | `/learning/modules/{id}/theory-completed` | `STUDENT` in a class | Marks the theory as read and returns the module |
| GET | `/learning/progress` | `STUDENT` in a class | The student's own progress: completed and total modules and, per module, the status, the lock state, the three flags, the latest quiz result and the state of the active coding tasks. See the rules of progress below |
| GET | `/learning/modules/{moduleId}/quiz` | `STUDENT` in a class | Draws a quiz of up to ten questions and a quiz token (`Cache-Control: no-store`). The theory must be read first |
| POST | `/learning/modules/{moduleId}/quiz/submissions` | `STUDENT` in a class | Evaluates the answers of a drawn quiz. A quiz token works for one submission only. After a failed attempt the answers are marked right or wrong, the correct options are shown after a pass |
| GET | `/learning/modules/{moduleId}/coding-tasks` | `STUDENT` in a class | Active coding tasks of the module in course order with the `solved` flag. The module must be unlocked and its theory read (the quiz is not required) |
| GET | `/learning/modules/{moduleId}/coding-tasks/{taskId}` | `STUDENT` in a class | A task: description, template code, the visible test cases (input and expected output) and the number of hidden cases. Never the driver, the reference solution or anything about a hidden case |
| POST | `/learning/modules/{moduleId}/coding-tasks/{taskId}/submissions` | `STUDENT` in a class | Compiles and runs the code `{"code": "..."}` (at most 12000 characters) in the sandbox and returns the verdict. See the rules of submissions below |

Rules of progress: the list holds the same active modules in the same order as `GET /learning/modules`, locked ones included (`locked`, status `NOT_STARTED`). `lastQuiz` is the latest quiz attempt (`score`, `maxScore`, `completedAt`), not the best one, and `null` when the quiz was not tried; it has no "passed" flag because the pass threshold can change, "ever passed" is `quizCompleted`. `coding` is `null` until the module is unlocked and its theory read, then it holds `activeTasks`, `passedTasks` and the active tasks with `state` (`NOT_ATTEMPTED`, `FAILED`, or `PASSED` when at least one result passed) and `lastAttemptAt`. A deactivated task is neither listed nor counted, and `codingCompleted` stays true when a task is added later, so `passedTasks` can be lower than `activeTasks`. Only statuses, counts and times are read from the results, never the submitted code, the output or the error messages.

Rules of coding tasks: a task belongs to one module and has a description, an optional template, an optional driver (`Main.java`), a reference solution and 1 to 20 test cases. Each code field and each case field is at most 16384 characters. The driver and the reference solution are written by the administrator and run only in the sandbox. A case is hidden or visible, and expected outputs, the driver and the reference solution are never part of a student response. A new task is inactive and can be activated once it has a reference solution and at least one case. A new module is inactive too, and an active module always has at least one active task: it cannot be activated without one, and the last active task of an active module can be neither deactivated nor deleted. A task that students have submitted solutions to is never deleted, only deactivated, so the history stays.

Activation proves that the task can be solved: the reference solution (with the driver, if any) runs in the sandbox over all cases, hidden ones included, and its output is compared by the same rules as a student's. Only one such verification runs at a time (`409 VERIFICATION_IN_PROGRESS`), and it activates the task only if the task was not edited meanwhile (`409 CODING_TASK_CHANGED`).
On failure the answer is `409 REFERENCE_SOLUTION_FAILED` and its `details` tell the administrator what went wrong: `status` (`COMPILE_ERROR`, `TIMEOUT`, `RUNTIME_ERROR` or `FAILED`), `failedCase`, `passedCases`, `totalCases`, `message` and, for a wrong output, `expectedOutput` and `actualOutput` (each at most 2000 characters). The diagnostics are for administrators only.
Editing what the activation verified (the driver, the reference solution, or an input or an expected output of a case; the hidden flag is not part of it) of an active task deactivates it, and it has to be activated, and so verified, again before students see it. Changing the title, description, template or order does not. If the task is the last active task of an active module, such an edit is refused (`409 MODULE_REQUIRES_ACTIVE_TASK`) and nothing is changed, so add and activate another task first. Results already stored stay, and so does the completed coding part of a module.

Rules of submissions:

- A student may submit when the module is unlocked and its theory is read. The task must be active and belong to the module, otherwise `404 CODING_TASK_NOT_FOUND`.
- The code is limited to 12000 characters, which leaves room for JSON escaping inside the default 16 KiB body limit. It runs in the sandbox, never in the backend, and the program is judged on its standard output only.
- A student has at most one submission being evaluated at a time (`409 SUBMISSION_IN_PROGRESS`), and at most 10 submissions per minute (`429 RATE_LIMITED`). If the sandbox is busy, the answer is `503 CODE_RUNNER_BUSY` with `Retry-After`, and `500 CODE_RUNNER_UNAVAILABLE` when it cannot be reached. Neither stores a result.
- The answer holds `status` (`PASSED`, `COMPILE_ERROR`, `RUNTIME_ERROR`, `TIMEOUT` or `FAILED`), `passed`, `passedCases`, `totalCases`, `errorMessage`, `actualOutput`, the outcome of every case in `cases` and `moduleCodingCompleted`. Overall status: `PASSED` when every case passes, `COMPILE_ERROR` when the code does not compile (`TIMEOUT` when the compilation itself takes too long), otherwise the status of the first failing case. Running out of memory counts as a runtime error.
- A visible case that fails shows the message of the runner (for an exception the type, the text and the line, e.g. `ArrayIndexOutOfBoundsException: Index 5 out of bounds for length 3`) and, for a wrong answer, what the program printed. A hidden case shows only its number and verdict; after an exception it adds the type and the line in the student's own file (`Výnimka NullPointerException (Solution.java, riadok 4)`) when the type is a well-known JDK exception, and never the text of the exception, because that could carry the hidden input.
- Every submission with a verdict is stored as a result (the history stays). The coding part of a module is completed when the student has a passed result for every currently active task of the module, and it stays completed afterwards.
  Comparison of the output (for task authors): line endings `\r\n` and `\n` are the same, spaces at the end of a line (not tabs) and blank lines at the end are ignored, everything else must match exactly: letter case, spaces inside and at the start of a line, blank lines in the middle, and numbers character by character (`3.0` is not `3`).
  Rules of user management: an administrator cannot change their own role, deactivate themselves or reset their own password, and the last active administrator cannot be demoted or deactivated. Students register themselves, administrators never create them.

**Passwords.** A password has at least 8 characters and at most 72 bytes (BCrypt ignores the rest; a character with diacritics takes more than one byte), the check is the same for registration and password change.
- *Change* (`PUT /users/me/password`): the current password is verified, the new one has to differ from it (`400 PASSWORD_UNCHANGED`) and has to match its confirmation (`400 PASSWORD_MISMATCH`). A wrong current password answers `400 INVALID_CURRENT_PASSWORD` (not 401, so a client does not take it for an expired session), 5 wrong attempts per user within 15 minutes answer `429 RATE_LIMITED`, and a successful change clears the counter. The user is loaded from the database, so a deactivated or deleted account is refused even with a still valid access token (`403 ACCOUNT_DISABLED`, `401 INVALID_TOKEN`).
- *Reset* (`POST /users/{id}/password-reset`): an administrator hands the temporary password over to the user. The failed-login and failed-password-change counters of that user are cleared, so a locked-out user can sign in with the temporary password at once. There is no e-mail reset, no forced change of the temporary password and a teacher cannot reset the password of a student.
- Both operations revoke all refresh tokens of the user. An access token that was issued before the change stays valid until it expires (15 minutes), because the password is not part of the per-request check (see below).
  **Access check on every request.** The access token proves only who the caller is. The security filter reads the current role and the active flag of that user from the database on every authenticated request (one query by primary key, two columns). A deactivated or deleted user with a still valid token gets `401 UNAUTHENTICATED` at once, and a demoted or promoted user has the new role at once. The role claim in the token is not used for authorization. A client handles the `401` as before: it tries `/auth/refresh` once, which fails because deactivation revoked the refresh tokens, and sends the user to the login, which answers `403 ACCOUNT_DISABLED`.
  Deactivation and deletion of a student are different on purpose. **Deactivation** pauses the account (a postponed or repeated year): the student keeps their progress and the administrator can reactivate the account. **Deletion** is for a student who leaves the school: the account, progress, quiz results, coding results and tokens are removed for good and the e-mail can be registered again. A teacher who leads an active class cannot be deactivated, demoted or moved to another school, the administrator has to reassign or deactivate the class first.

Rules of classes and enrollment:

- A class belongs to one school and is led by one teacher of that school. Only administrators create classes. A class is deactivated, never deleted. Deactivating a class only closes it to new students, the students already in it keep their access.
- The join code has 8 characters and is shown only to administrators and to the teacher of the class. Generating a new code invalidates the old one. An unknown code, a deactivated class and a deactivated school all answer the same `404 INVALID_JOIN_CODE`.
- A student joins with the code and gets the school of the class. A student who is already in a class answers `409 ALREADY_ENROLLED`, a student whose school differs from the school of the class answers `409 SCHOOL_MISMATCH`.
- After 5 failed attempts within 15 minutes a student answers `429 TOO_MANY_ATTEMPTS` with a `Retry-After` header. The counter lives in memory of one application instance.
- A student who has not joined a class gets `403 NOT_ENROLLED` on everything under `/learning/**`. Teachers and administrators get `403 ACCESS_DENIED` there.
- A module is unlocked when it is the first active one, when the student passed the quiz of the previous active module, or when the student already has progress in it.
  Rules of the teacher's student overview and bulk change:
- A teacher sees and changes only the students of **their own classes** (`classes.teacher_id`) that belong to **the teacher's current school**. The school condition matters when a teacher who still leads only deactivated classes is moved to another school: the old classes then show nobody. A teacher without a class gets an empty page, a teacher without a school (or an inactive one) gets `403 ACCESS_DENIED`. The teacher is read from the database on every call, so a still valid access token does not help after a deactivation or a change of role.
- A student outside this scope, a teacher, an administrator and an unknown id are indistinguishable: all answer `404 USER_NOT_FOUND` (`404 CLASS_NOT_FOUND` for a class filter), never `403`, so nothing about other accounts is revealed. The message does not say which id of a bulk request was refused.
- Deactivated students and students of deactivated classes stay in the list (`active`, `classActive`), so they can be reactivated.
- The overview answers `modules` (id, title, order of every active module) and `students`, a page whose rows hold the name, surname, e-mail, the class, `completedModules` and one cell per module with `status` (`NOT_STARTED`, `IN_PROGRESS`, `COMPLETED`) and the three flags. The detail holds the same data as the student's own overview. A teacher never gets submitted code, program output, error messages, join codes or password hashes. The number of database queries does not depend on the number of students.
- The bulk change has no partial success. Repeated ids count once. A student who is already in the requested state is not an error, he is listed in `unchanged`. Two changes of the same students at the same time are decided one after another (the rows are locked), so every student is reported as changed once. Reactivation does not bring refresh tokens back, the student signs in again.
- There is no separate archive: "archiving" a student means deactivating the account, the progress stays. Deactivating a class and deleting a student stay with administrators.
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
| 400 | `INVALID_CURRENT_PASSWORD` | The current password of a password change is wrong (not 401 on purpose: it does not mean an expired session) |
| 400 | `PASSWORD_UNCHANGED` | The new password is the same as the current one |
| 400 | `INVALID_QUIZ_TOKEN` | The quiz token is malformed or was issued for someone else, load the quiz again |
| 413 | `PAYLOAD_TOO_LARGE` | The request body exceeds the allowed size, a client bug |
| 401 | `INVALID_CREDENTIALS` | Wrong e-mail or password (the same answer for both) |
| 401 | `INVALID_REFRESH_TOKEN` | The refresh token cookie is missing, or its token is unknown, expired, revoked or already used. Also returned after the account was deactivated, because deactivation revokes all its refresh tokens. A visitor who is not signed in gets it from `/auth/refresh` too, it is a normal answer and not an error of the client |
| 401 | `INVALID_TOKEN` | The access token is valid, but its user no longer exists (rare: the user was deleted between the check in the security filter and the call of the service) |
| 401 | `UNAUTHENTICATED` | No access token was sent, or it is invalid or expired, or its user is deactivated or no longer exists |
| 403 | `ACCOUNT_DISABLED` | The account is deactivated (answered by the login and the token refresh; an access token of a deactivated user gets `401 UNAUTHENTICATED`) |
| 403 | `ACCESS_DENIED` | The role of the user does not allow the operation |
| 403 | `ORIGIN_NOT_ALLOWED` | `/auth/login`, `/auth/refresh` or `/auth/logout` was called with an `Origin` header that is not trusted (see [Refresh token cookie](#refresh-token-cookie)) |
| 403 | `MODULE_LOCKED` | The quiz of the previous module has not been passed yet |
| 403 | `NOT_ENROLLED` | The student has not joined a class yet |
| 404 | `SCHOOL_NOT_FOUND` | The school does not exist |
| 404 | `USER_NOT_FOUND` | The user does not exist (for a teacher also: the user is not a student of the teacher's own classes) |
| 404 | `MODULE_NOT_FOUND` | The module does not exist or is deactivated |
| 404 | `QUESTION_NOT_FOUND` | The question does not exist in the given module |
| 404 | `CODING_TASK_NOT_FOUND` | The coding task does not exist in the given module |
| 404 | `CLASS_NOT_FOUND` | The class does not exist (or does not belong to the calling teacher and the teacher's school) |
| 404 | `INVALID_JOIN_CODE` | No active class of an active school has this join code |
| 409 | `EMAIL_ALREADY_EXISTS` | The e-mail is already registered |
| 409 | `SCHOOL_NAME_ALREADY_EXISTS` | A school with the same name exists |
| 409 | `SCHOOL_INACTIVE` | The school is deactivated and cannot get new users |
| 409 | `LAST_ADMIN` | The last active administrator cannot be removed |
| 409 | `SELF_MODIFICATION_NOT_ALLOWED` | An administrator cannot change their own role, deactivate themselves or reset their own password (they use the password change) |
| 409 | `USER_NOT_DELETABLE` | Only a student can be deleted, a teacher or an administrator is deactivated |
| 409 | `CLASS_NAME_ALREADY_EXISTS` | The school already has a class with this name |
| 409 | `TEACHER_LEADS_CLASSES` | The teacher leads an active class, reassign or deactivate the class first |
| 409 | `ALREADY_ENROLLED` | The student already belongs to a class |
| 409 | `SCHOOL_MISMATCH` | The school of the student differs from the school of the class |
| 409 | `THEORY_NOT_COMPLETED` | The theory of the module has not been marked as read yet (needed for the quiz and for coding tasks) |
| 409 | `QUIZ_NOT_AVAILABLE` | The module has no quiz questions |
| 409 | `QUIZ_OUTDATED` | The quiz expired or one of its questions was deleted, load it again |
| 409 | `QUIZ_ALREADY_SUBMITTED` | The quiz token was already used for a submission (double click, retry), load the quiz again |
| 409 | `MODULE_REQUIRES_ACTIVE_TASK` | An active module needs an active coding task: it cannot be activated without one, and its last active task cannot be deactivated or deleted |
| 409 | `CODING_TASK_NOT_READY` | The task cannot be activated without a reference solution and at least one test case |
| 409 | `CODING_TASK_HAS_RESULTS` | Students have submitted solutions to the task, it cannot be deleted, deactivate it instead |
| 409 | `SUBMISSION_IN_PROGRESS` | The student's previous submission is still being evaluated, wait for its result |
| 409 | `REFERENCE_SOLUTION_FAILED` | The reference solution does not pass its own test cases, the task stays inactive. The body carries `details` (see the rules of coding tasks) |
| 409 | `VERIFICATION_IN_PROGRESS` | Another reference solution is being verified, retry in a moment |
| 409 | `CODING_TASK_CHANGED` | The task was edited while its reference solution was verified, nothing was activated, review the task and activate it again |
| 429 | `TOO_MANY_ATTEMPTS` | Too many failed enrollment attempts, the `Retry-After` header says how many seconds to wait |
| 429 | `RATE_LIMITED` | Too many requests from this address, for this e-mail or from this user, the `Retry-After` header says how many seconds to wait |
| 500 | `CODE_RUNNER_UNAVAILABLE` | The code runner is down, misconfigured or sent a broken answer, a server fault (the details are only in the server log) |
| 503 | `CODE_RUNNER_BUSY` | The code runner has no free capacity, the `Retry-After` header says how many seconds to wait |

## Rate limiting and request size limits

Limits are kept in memory (`ratelimit/RateLimiter`, a sliding window), so they reset on restart and are not shared between application instances. Replace the limiter before running several instances.

| What | Key | Default |
|------|-----|---------|
| `POST /auth/login` | client IP | 10 per 15 minutes |
| `POST /auth/register` | client IP | 5 per hour |
| `POST /auth/refresh` | client IP | 120 per minute |
| any other request under `/api/v1/` | client IP | 600 per minute |
| failed logins | hash of the e-mail, any IP | 5 per 15 minutes |
| quiz load | student id | 20 per minute |
| coding submission | student id | 10 per minute |
| wrong current password in a password change | user id | 5 per 15 minutes |

All values are configurable under `app.rate-limit.*`. A limit answers `429 RATE_LIMITED` with a `Retry-After` header. The number of tracked keys is bounded, so forged addresses cannot exhaust memory.

**Client IP.** By default the address of the TCP connection is used. `X-Forwarded-For` is believed only when the connection comes from a proxy listed in `RATE_LIMIT_TRUSTED_PROXIES` (exact addresses, comma separated). The rightmost entry that is not a trusted proxy is the client. Set it before deploying behind a reverse proxy, otherwise all users share the proxy's address.

**Body size.** Request bodies under `/api/` are limited to 16 KiB, and to 1 MiB under the prefixes in `app.rate-limit.large-body-prefixes` (module theory, coding tasks and test cases). Larger bodies get `413 PAYLOAD_TOO_LARGE`, including bodies sent without a `Content-Length`. A student's code goes under `/api/v1/learning`, which has the default limit, so it is capped at 12000 characters.

CORS preflight requests (`OPTIONS`) do not count towards any limit, and a `429` answer carries the CORS headers, so a browser client can read `Retry-After` (the header is exposed).

Known trade-offs: anyone who knows an e-mail can block logins to it for the length of the window (only failures count and a successful login clears the counter, so the block is short). A school behind one public address shares the registration and general limits, raise them in the configuration if needed. The refresh limit is higher than the others on purpose: the access token lives only in the memory of the browser, so every page load and every new tab calls `/auth/refresh`, and a school can sit behind one public address.

## CORS and the frontend

CORS is **off by default**: with no origin configured the API sends no CORS headers and browsers block cross-origin calls. This is the right setting when the frontend and the API share one origin (a reverse proxy in production, the Vite dev server proxy in development).

To allow a frontend on another origin, set `CORS_ALLOWED_ORIGINS` (comma separated, for example `http://localhost:5173`). Rules:

- Only exact origins are accepted: `scheme://host[:port]`, no wildcard, path, query or user info. A wrong value stops the application at startup.
- Applies to `/api/**` only, methods `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, request headers `Authorization` and `Content-Type`, preflight cached for `app.cors.max-age` (default 1 hour).
- Credentials are allowed (`Access-Control-Allow-Credentials: true`) because the refresh token travels in a cookie. This is why a wildcard origin is refused at startup. A cross-origin frontend must send `credentials: 'include'` (fetch) on `/auth/login`, `/auth/refresh`, `/auth/logout` and `PUT /users/me/password`. The access token still travels in the `Authorization` header.
- The filter runs before the body size and rate limit filters and before Spring Security, so `401`, `413` and `429` answers also carry the CORS headers and the browser can show the error.
- A request from an origin that is not allowed gets `403` with the plain text `Invalid CORS request`, not the JSON error body.

## Refresh token cookie

The access token (15 minutes) is returned in the response body and the client keeps it in memory only. The refresh token (7 days) never appears in a body, it travels in a cookie that JavaScript cannot read.

| Attribute | Value | Why |
|-----------|-------|-----|
| Name | `jla_refresh_token` | |
| `HttpOnly` | yes | a script cannot read the token, so an XSS cannot steal it |
| `Secure` | `true` by default (`REFRESH_COOKIE_SECURE`) | the cookie is sent over HTTPS only |
| `SameSite` | `Strict` | the cookie is not sent on requests started from another site |
| `Path` | `/api/v1/auth` | the browser sends it only to the login, refresh and logout endpoints |
| `Max-Age` | the refresh token lifetime (default 7 days) | |
| `Domain` | not set | the cookie belongs to the exact host of the API |

The `__Host-` prefix is not possible (it requires `Path=/`) and `__Secure-` is not used because `Secure` is configurable.

**Origin check (protection against CSRF).** `SameSite` works by site, not by origin: a page on another port or on a sibling subdomain is the same site. The API has CSRF protection off (stateless tokens) and, with CORS off, nothing else stops a cross-origin `POST`. So `POST /auth/login`, `/auth/refresh` and `/auth/logout` check the `Origin` header:

- no `Origin` header: allowed (a browser always sends it on a `POST`, so this is a non-browser client);
- `Origin` in the trusted set (`AUTH_ALLOWED_ORIGINS` together with `CORS_ALLOWED_ORIGINS`): allowed;
- any other `Origin`: `403 ORIGIN_NOT_ALLOWED`, logged as a warning;
- the trusted set is empty: every request with an `Origin` is refused (fail closed). A setup with the frontend and the API on one origin must therefore list that origin in `AUTH_ALLOWED_ORIGINS`.

Registration and the password change are not checked: they do not read the cookie (the password change authenticates with the `Authorization` header).

**Failed refresh.** The cookie is not cleared when `/auth/refresh` fails. With two tabs, the tab that loses the race would otherwise delete the new cookie of the winner. `/auth/logout` always clears it.

**Behind a reverse proxy.** The cookie `Path` is `/api/v1/auth`, so the proxy must pass the `/api` prefix unchanged. A proxy that strips or rewrites it makes the browser stop sending the cookie.

**Notes for the frontend**

- Call `/auth/refresh` on startup to get the first access token. A visitor who is not signed in gets `401 INVALID_REFRESH_TOKEN`, which is a normal answer.
- Keep one refresh in flight at a time (for example with the Web Locks API, so also across tabs) and retry the original request once after a `401`.
- With a proxy (same origin) no extra option is needed; cross-origin use `credentials: 'include'`.

Known limits: an XSS can still call `/auth/refresh` from the page (`HttpOnly` protects against theft of the token, not against its use), so the content security policy and the escaping of user content stay important. The rate limiters are in the memory of one instance.

## OpenAPI description and Swagger UI

The API is described by springdoc-openapi, **off by default**. Set `API_DOCS_ENABLED=true` to turn it on:

| Address | Content |
|---------|---------|
| `/v3/api-docs` and `/v3/api-docs.yaml` | the OpenAPI description (JSON, YAML) |
| `/swagger-ui.html` | Swagger UI, use **Authorize** and paste an access token from `POST /auth/login` |

While it is on, these addresses are public (no token needed), so turn it on in development only. The default `dev` profile does not turn it on, the variable decides. The description lists the bearer scheme, marks the four public `/auth` endpoints as not requiring a token and adds the `ErrorResponse` body for `401`, `403`, `429` and `500`.

## Code runner (sandbox)

Student code is untrusted, so it never runs inside the backend. The `runner/` module is a separate Spring Boot service, the only part that talks to Docker. Every submission runs in its own disposable container, which is removed afterwards. The backend calls the runner over HTTP (`coderunner/HttpCodeRunner`); the runner only reports what the program printed and never sees an expected output, comparing is the backend's job (`learning/coding/AttemptEvaluator`). The runner must be running for submissions and for activating a coding task.

### Build the image and start the runner

The runner never pulls images, so build the sandbox image first (and again after changing `runner/sandbox/launcher.sh` or the `Dockerfile`):

```
docker build --tag jla-sandbox:spike runner/sandbox
```

Set `RUNNER_TOKEN` in `.env` (see the table above; the backend and the runner must use the same value), load `.env` as in step 3 and start the runner. The environment variables are per window, so load `.env` in the window where you start it:

```
cd runner
.\mvnw.cmd spring-boot:run
```

It listens on `127.0.0.1:8081` only. The log line `Started RunnerApplication` means it is ready. If it stops with `must be at least 32 characters long` and the value `${RUNNER_TOKEN}`, the variable is not set: `.env` has no `RUNNER_TOKEN` line (an `.env` created before the runner was added does not have it) or it was not loaded in this window.

To check it from a second window (load `.env` there too):

```
Invoke-RestMethod http://127.0.0.1:8081/health
$body = @{ solutionSource = 'public class Solution { public static void main(String[] a) { System.out.println(1 + 2); } }'; caseInputs = @('') } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8081/run -Headers @{ 'X-Runner-Token' = $env:RUNNER_TOKEN } -ContentType 'application/json; charset=utf-8' -Body $body | ConvertTo-Json -Depth 10
```

Never paste the token into a chat, a ticket or a command that is kept in a history.

### Backend integration

The backend reads `app.runner.*` (`application.yaml`): `base-url` (environment variable `RUNNER_BASE_URL`, default `http://127.0.0.1:8081`), `token` (`RUNNER_TOKEN`, **required, no default**: the backend does not start with a token shorter than 32 characters), `connect-timeout` (2 s) and a read timeout of 25 s plus 5 s per case, a safety net above the runner's own limits. The tests do not need a runner or the token, they use a scripted fake runner and a test-only token.

The backend checks every answer (one result per input, numbered from 1, none after a compilation error) and translates failures: a busy runner (`503 SANDBOX_BUSY`) becomes `503 CODE_RUNNER_BUSY` with the same `Retry-After`, everything else that goes wrong (connection, timeout, `4xx` and `5xx` of the runner, a broken answer) becomes `500 CODE_RUNNER_UNAVAILABLE` without any detail for the client. No database transaction stays open while the runner works.

### API

`GET /health` needs no token: `200 {"status":"UP"}` while Docker answers, otherwise `503 {"status":"DOCKER_UNAVAILABLE"}`. Everything else needs the `X-Runner-Token` header.

`POST /run`:

```json
{
  "solutionSource": "public class Solution { ... }",
  "driverSource": "public class Main { ... }",
  "caseInputs": ["3\n", "5\n"]
}
```

| Field | Meaning |
|-------|---------|
| `solutionSource` | The student's `Solution.java`, required |
| `driverSource` | Optional `Main.java` written by the task author; when present it is the entry point, otherwise `Solution` is |
| `caseInputs` | Stdin of each case, at most 20. Without any, the program runs once with empty stdin (reported as case 1) |

Response `200`:

```json
{
  "compile": { "success": true, "timedOut": false, "message": "" },
  "cases": [
    { "number": 1, "status": "OK", "millis": 120, "stdout": "9\n", "message": "" }
  ]
}
```

`status` is `OK`, `RUNTIME_ERROR`, `TIMEOUT` or `OUT_OF_MEMORY`. `stdout` is filled only for `OK`. If the compilation fails, `compile.message` holds the compiler output and `cases` is empty. All messages are cleaned before they leave the runner (JVM log lines, internal paths and control characters are removed, the length is limited).

| Status | Code | Meaning |
|--------|------|---------|
| 400 | `INVALID_REQUEST` | Malformed JSON, blank source, too many cases, or input over the size limits |
| 401 | `UNAUTHORIZED` | Missing or wrong token |
| 413 | `PAYLOAD_TOO_LARGE` | Request body over the limit (6 times the archive limit plus 16 KiB, because JSON may escape every byte) |
| 500 | `SANDBOX_UNAVAILABLE` | The sandbox itself failed (Docker missing, malformed result). Never a verdict on the student's code, the reason goes only to the log |
| 503 | `SANDBOX_BUSY` | All slots and the queue are taken, retry after the `Retry-After` header |

### Isolation

Each container starts with: no network (`--network none`), read-only root file system, all capabilities dropped except those the launcher needs to hand the program to an unprivileged user, `no-new-privileges`, 512 MB of memory without swap, one CPU, at most 128 processes, non-executable tmpfs for the scratch directories, no log copy on the host. The student's program runs as an unprivileged user with a 64 MB heap and a clean environment, the compiler as another one, and neither can read the launcher's files. These flags are fixed in `DockerRunCommand` and are not configurable on purpose, so a configuration mistake cannot weaken them.

The result is returned as length-prefixed frames, so a program that prints something that looks like a frame cannot forge a result. A container that stays behind is removed by name after each run and by a reaper at startup and every minute.

### Configuration

In `runner/src/main/resources/application.yaml` under `runner.sandbox`:

| Key | Default |
|-----|---------|
| `compile-timeout-seconds` | 10 |
| `run-timeout-seconds` (per case) | 5 |
| `max-output-bytes` (per output stream) | 65536 |
| `max-cases` | 20 |
| `capacity.concurrency` (containers at once, 0 = half of the CPU cores) | 0 |
| `capacity.queue` (requests waiting for a slot) | 10 |
| `capacity.max-source-bytes` / `max-case-input-bytes` / `max-archive-bytes` | 64 KiB / 64 KiB / 1 MiB |

### Tests

The unit tests need no Docker. `DockerSandboxIntegrationTest` and `SandboxAttackIntegrationTest` (a corpus of hostile programs: memory and thread exhaustion, fork bomb, reading secret files, writing outside the scratch space, network access, forged result frames, output floods) run real containers. They skip themselves when Docker or the image is missing; the CI job `sandbox` builds the image and fails if any of them was skipped.

### Before production

Docker Desktop is enough for development. In production run Docker rootless or use gVisor and keep the Docker socket reachable only by the runner process: access to it is equivalent to root on the host. The base image in `runner/sandbox/Dockerfile` is already pinned to a digest (the tag is only for reading); Dependabot proposes new digests, see "Continuous integration".

## Tech stack

- **Backend:** Java 25, Spring Boot 4.1, Spring Data JPA, Spring Security, JJWT, springdoc-openapi, Lombok, Maven
- **Database:** MySQL 8.4 LTS (Docker for local development), Flyway migrations
- **Frontend (planned):** React, TypeScript, Vite, Monaco Editor
- **Code runner:** a separate Spring Boot service (`runner/`) that runs student code in disposable Docker containers
  Architecture: three layers (presentation, application, data) with role-based access control (student, teacher, admin).
## Prerequisites

- JDK 25
- Docker (for the local MySQL container, for the tests, which start their own MySQL container, and for the code runner)
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
| `REFRESH_COOKIE_SECURE` | Optional, `Secure` attribute of the refresh token cookie (default `true`). Set `false` only for plain HTTP development outside localhost; the application logs a warning. Chrome treats `localhost` as secure, so the default works there too |
| `AUTH_ALLOWED_ORIGINS` | Optional, comma separated origins that may call login, refresh and logout (default empty). Together with `CORS_ALLOWED_ORIGINS` they form the trusted set. Empty means every request with an `Origin` header is refused, so set it to the public origin of the frontend, in development `http://localhost:5173`. See [Refresh token cookie](#refresh-token-cookie) |
| `ADMIN_EMAIL` | Optional, e-mail of the first administrator (see [First administrator](#first-administrator)) |
| `ADMIN_PASSWORD` | Optional, password of the first administrator: at least 12 characters, at most 72 bytes. Remove it from the environment after the first start |
| `ADMIN_NAME` | Optional, first name of the first administrator (default `Admin`) |
| `ADMIN_SURNAME` | Optional, surname of the first administrator (default `Administrátor`) |
| `RATE_LIMIT_TRUSTED_PROXIES` | Optional, comma separated addresses of reverse proxies whose `X-Forwarded-For` header is believed (default empty: never) |
| `CORS_ALLOWED_ORIGINS` | Optional, comma separated origins of a frontend served from another origin (default empty: CORS off). See [CORS and the frontend](#cors-and-the-frontend) |
| `API_DOCS_ENABLED` | Optional, `true` turns on the OpenAPI description and Swagger UI, which are then public (default `false`). See [OpenAPI description and Swagger UI](#openapi-description-and-swagger-ui) |
| `RUNNER_TOKEN` | Shared secret between the backend and the code runner, at least 32 characters. Both the backend and the runner refuse to start without it, there is no default. If your `.env` was created before the runner was added, add the line (compare it with `.env.example`) |
| `RUNNER_BASE_URL` | Optional, where the backend finds the runner (default `http://127.0.0.1:8081`). Leave the line out or commented out, an empty value breaks the startup |
| `RUNNER_PORT` | Optional, port of the runner (default `8081`) |
| `RUNNER_BIND_ADDRESS` | Optional, address the runner listens on (default `127.0.0.1`, keep it that way) |

Generate a `JWT_SECRET` (PowerShell):

```
$b = New-Object byte[] 32; (New-Object Security.Cryptography.RNGCryptoServiceProvider).GetBytes($b); [Convert]::ToBase64String($b)
```

Linux/macOS: `openssl rand -base64 32`

Generate a `RUNNER_TOKEN` the same way (any random string of at least 32 characters will do). In PowerShell you can append it to `.env` without ever printing it:

```
$b = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); Add-Content -Path .env -Value "`nRUNNER_TOKEN=$([Convert]::ToBase64String($b))"
```

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

GitHub Actions (`.github/workflows/ci.yml`) runs on every push to any branch, on pull requests to `main` and on demand. Three jobs run in parallel: `backend` (build and all tests, the integration tests start their own MySQL container with Testcontainers and use the test-only configuration from `src/test/resources`, so no database service and no secrets are needed), `runner` (build and unit tests) and `sandbox` (builds the sandbox image and runs the tests that start real containers; it fails if any of them was skipped). Docker is preinstalled on the runner. A newer run of the same branch or pull request cancels the older one, runs on `main` are never cancelled.

The `sandbox` job also lints `runner/sandbox/Dockerfile` with Hadolint before building it (warnings and errors fail the job, style hints do not).

**Supply chain.** Every GitHub Action in `.github/workflows/` is pinned to a full commit SHA with the version in a trailing comment (`uses: actions/checkout@<sha> # v7.0.1`), because a tag can be moved to other code later and a SHA cannot. The sandbox base image is pinned to a digest in the same way (`FROM eclipse-temurin:25-jdk-noble@sha256:...`). Dependabot (`.github/dependabot.yml`) checks weekly: Maven in `/backend` and `/runner`, GitHub Actions (it updates the SHA and the comment together), `docker-compose.yml` and the sandbox `Dockerfile`. Minor and patch updates of one ecosystem come in one pull request. New major versions of MySQL and of Java (`eclipse-temurin`) are ignored on purpose: they are project decisions.

To pin a new action by hand, take the commit of the release tag, for example `git ls-remote --tags https://github.com/<owner>/<action>.git "<tag>*"`; for an annotated tag use the line ending in `^{}`, which is the commit.

## First administrator

Only an administrator can create teachers and other administrators, so the first one is created at startup from the environment:

1. Set `ADMIN_EMAIL` and `ADMIN_PASSWORD` (optionally also `ADMIN_NAME` and `ADMIN_SURNAME`) before starting the backend.
2. If the database contains no administrator, the account is created and the log shows `Created the first administrator account ...`. If an administrator already exists, the variables are ignored: an existing account is never changed or replaced, and an existing student or teacher account is never promoted.
3. Log in with `POST /api/v1/auth/login`.
4. Remove `ADMIN_PASSWORD` from the environment. The password is stored only as a hash, the variable is not needed any more.
   The application refuses to start when the configuration is unusable: only one of `ADMIN_EMAIL` and `ADMIN_PASSWORD` is set, the e-mail is malformed, the password has fewer than 12 characters or more than 72 bytes (characters with diacritics take more than one byte), or an account with that e-mail already exists.
   **Recovery.** A forgotten password of an administrator can be reset by another administrator (`POST /users/{id}/password-reset`). If no administrator can log in at all, delete the administrator in the database and start the application again with `ADMIN_EMAIL` and `ADMIN_PASSWORD` set:

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
│       │   │   │   ├── cookie/       refresh token cookie factory, properties and the Origin check
│       │   │   │   ├── dto/          request and response records
│       │   │   │   ├── exception/    authentication exceptions
│       │   │   │   ├── security/     JWT service, filter, security config, 401/403 JSON handlers
│       │   │   │   └── token/        refresh token entity, repository and service
│       │   │   ├── codingtask/   coding tasks and test cases (admin/ holds their management)
│       │   │   ├── coderunner/   client of the code runner service (HTTP, answer checks)
│       │   │   ├── common/       global exception handler, error body, page response, MVC configuration, CORS filter, OpenAPI configuration
│       │   │   ├── ratelimit/    sliding-window limiter, per-IP filter, body size filter, client IP resolution
│       │   │   ├── user/
│       │   │   │   ├── model/        user entity, role, repository
│       │   │   │   ├── admin/        user management by an administrator
│       │   │   │   ├── dto/          request and response records
│       │   │   │   ├── teacher/      student overview and bulk deactivation by a teacher
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
│       │   │   │   ├── quiz/         quiz draw and evaluation
│       │   │   │   └── coding/       coding tasks of a student: submissions, evaluation, comparison of outputs
│       │   │   └── quizresult/ codingresult/ userprogress/   results and progress of students (entities and repositories)
│       │   └── resources/
│       │       ├── application.yaml
│       │       └── db/migration/ Flyway migrations (V1 to V7)
│       └── test/             unit and integration tests, testsupport/ holds the Testcontainers setup and shared fixtures
├── runner/               Code runner service (Spring Boot)
│   ├── sandbox/              Dockerfile and launcher.sh of the sandbox image
│   └── src/                  run/ (POST /run), sandbox/ (docker, config, exception, result), security/, health/
├── docker-compose.yml    local MySQL
└── .env.example          template of required environment variables
```

Tests lie in the package of the tested class (they need package-private access in some cases). The API integration tests of a feature are in an `api` subpackage, one class per operation, with a shared abstract test support class.

## Architecture rules and nullability

The tests in `backend/src/test/java/com/tgrznar/javalearningapp/architecture/` use [ArchUnit](https://www.archunit.org/) to check the compiled production classes on every test run (locally and in CI). They do not lie in the package of a tested class, because they check the whole application. A rule that fails means the code broke an agreed convention, not that a feature is wrong.

What is checked:

- Controllers are not called by any other class.
- Repositories are used only by `@Service` classes and by four documented exceptions: `JwtAuthenticationFilter` and `SecurityConfig` (the role and the active flag are read on every request), `EnrollmentGateInterceptor` and `JoinCodeGenerator`. The reason of each is in `LayerArchitectureTest`.
- Classes in `dto` packages are records or enums.
- `@Transactional` appears only in services.
- Public controller methods do not take or return JPA entities, also not inside a generic type.
- Every endpoint has `@PreAuthorize`, on the controller or on the method. The exceptions are `AuthController` (public endpoints, listed one by one in `SecurityConfig`) and `UserController` (the signed-in user's own profile and password, open to every role).
- Dependencies are injected through constructors, never through `@Autowired` fields or setters.
- No class starts an operating system process (`ProcessBuilder`, `Runtime.exec`): user code never runs in the application JVM, only the separate runner starts processes.
- No `System.out`, `System.err` or `java.util.logging`.
  A new exception to a rule needs a written reason next to it. Not checked: cycles between feature packages (they exist today, the feature packages and `common` depend on each other), Lombok annotations such as `@Data` (they are not kept in the bytecode) and `@Transactional(readOnly = true)` on services that map lazy associations (code review).

Every package has a `package-info.java` with [JSpecify](https://jspecify.org/) `@NullMarked`: a type is non-null unless it is marked `@Nullable`. A new package needs its own `package-info.java`. The annotations serve the IDE and the reader, there is no compile-time null checker. Spring Data checks them at run time, though: a parameter of a repository method that may be `null` (an optional filter such as `:schoolId is null or ...`) must be marked `@Nullable`, otherwise the call fails with an `IllegalArgumentException`.

## Database migrations

Schema changes are managed by Flyway. An applied migration is never edited, every change is a new versioned file (`V8__...sql`). Foreign keys use `ON DELETE RESTRICT`, records with dependent data are soft-deleted through the `is_active` flag. Two exceptions use `ON DELETE CASCADE`: `refresh_tokens` (session data, V2) and the learning data of a student (`user_progress`, `quiz_results`, `coding_results`, V5), so that deleting a student removes it. A new table that holds a student's data must follow the same rule.

## Development workflow

- GitHub Flow: feature branch, pull request, squash and merge into `main`, delete the branch.
- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `build:`, `refactor:`, `test:`, `docs:`, `ci:`).
- Dependency and build changes are committed separately from feature code.
- Files are staged by explicit path, not with `git add .`.
 