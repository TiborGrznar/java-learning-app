# Image for running untrusted student code. Contains a JDK and the launcher, nothing else:
# no secrets, no application code, no package manager hooks. Built once, started per submission.
#
# The base image is pinned to a digest: the tag (25-jdk-noble) is only there for people to read, the
# digest decides which image is really used, so a re-published tag cannot change the sandbox
# silently. Dependabot (docker ecosystem, /runner/sandbox) proposes new digests; read such a PR
# as "a new build of the same Java 25 image" and run the sandbox tests before merging.
# To update by hand:
#   docker pull eclipse-temurin:25-jdk-noble
#   docker buildx imagetools inspect eclipse-temurin:25-jdk-noble
# and copy the "Digest:" line (the digest of the manifest list, not of one platform).
FROM eclipse-temurin:25-jdk-noble@sha256:80f67fb3d6f76e2d817d98a4cc2a9cf05f054d4e6adc5e2144bde671d722e63f

# Two unprivileged uids, used by the launcher through setpriv (see launcher.sh):
#   10002 runs the student's program, 10003 runs the compiler and owns /build.
# The image has no USER on purpose: the launcher starts as root, and the runner starts the container
# with every capability dropped except SETUID, SETGID and KILL (and no-new-privileges).
RUN groupadd --gid 10002 student \
 && useradd --uid 10002 --gid 10002 --no-create-home --home-dir /nonexistent --shell /usr/sbin/nologin student \
 && groupadd --gid 10003 compiler \
 && useradd --uid 10003 --gid 10003 --no-create-home --home-dir /nonexistent --shell /usr/sbin/nologin compiler \
 && mkdir -p /work /build /opt/jla

# Fail the build early if the tool the launcher needs to drop privileges is missing.
RUN setpriv --version

# Drop setuid/setgid bits (belt and braces; the runner also starts containers with no-new-privileges).
RUN find / -xdev -perm /6000 -type f -exec chmod a-s {} + 2>/dev/null || true

# sed strips a UTF-8 BOM and CRLF line endings in case the file was saved or checked out on Windows
# (.gitattributes also forces LF).
COPY launcher.sh /opt/jla/launcher.sh
RUN sed -i '1s/^\xEF\xBB\xBF//; s/\r$//' /opt/jla/launcher.sh && chmod 0555 /opt/jla/launcher.sh

WORKDIR /work
ENTRYPOINT ["/opt/jla/launcher.sh"]