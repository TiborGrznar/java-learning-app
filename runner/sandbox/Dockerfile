# Image for running untrusted student code. Contains a JDK and the launcher, nothing else:
# no secrets, no application code, no package manager hooks. Built once, started per submission.
#
# The tag is a floating minor tag for the spike. Before the demo is considered done, pin it to a digest:
#   docker pull eclipse-temurin:25-jdk-noble
#   docker inspect --format "{{index .RepoDigests 0}}" eclipse-temurin:25-jdk-noble
FROM eclipse-temurin:25-jdk-noble

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