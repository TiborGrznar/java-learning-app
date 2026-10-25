# Security Policy

This is a bachelor's thesis project and is not run as a production service. Security reports are still welcome.

## Reporting a vulnerability

Please do **not** open a public issue for a security problem.

Use GitHub's private reporting: open the **Security** tab of this repository and choose **Report a vulnerability**.

Include what you found, how to reproduce it, and which version or commit it affects. Reports are handled on a best-effort basis by the maintainers.

## Scope

Of particular interest:

- authentication and token handling (JWT access and refresh tokens),
- authorization between roles (student, teacher, administrator),
- the code execution sandbox (`runner`), once it is merged.

## Supported versions

Only the latest commit on `main` is supported.