# Image for running untrusted student code. Contains a JDK and the launcher, nothing else:
# no secrets, no application code, no package manager hooks. Built once, started per submission.
#
# The tag is a floating minor tag for the spike. Before the demo is considered done, pin it to a digest:
#   docker pull eclipse-temurin:25-jdk-noble
#   docker inspect --format "{{index .RepoDigests 0}}" eclipse-temurin:25-jdk-noble
FROM eclipse-temurin:25-jdk-noble

# Fixed unprivileged uid/gid; the runner starts the container with --user 10001:10001 as well.
RUN groupadd --gid 10001 sandbox \
 && useradd --uid 10001 --gid 10001 --no-create-home --home-dir /nonexistent --shell /usr/sbin/nologin sandbox \
 && mkdir -p /work /opt/jla \
 && chown 10001:10001 /work

# Drop setuid/setgid bits (belt and braces; the runner also starts containers with no-new-privileges).
RUN find / -xdev -perm /6000 -type f -exec chmod a-s {} + 2>/dev/null || true

# sed strips a UTF-8 BOM and CRLF line endings in case the file was saved or checked out on Windows
# (.gitattributes also forces LF).
COPY launcher.sh /opt/jla/launcher.sh
RUN sed -i '1s/^\xEF\xBB\xBF//; s/\r$//' /opt/jla/launcher.sh && chmod 0555 /opt/jla/launcher.sh
USER 10001:10001
WORKDIR /work
ENTRYPOINT ["/opt/jla/launcher.sh"]
