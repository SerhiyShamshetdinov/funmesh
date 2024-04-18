# syntax=docker/dockerfile:1

#FROM eclipse-temurin:16-jre-jammy AS final
FROM eclipse-temurin:latest AS final

# Create a non-privileged user that the app will run under.
# See https://docs.docker.com/go/dockerfile-user-best-practices/
ARG UID=10001
RUN adduser \
    --disabled-password \
    --gecos "" \
    --home "/nonexistent" \
    --shell "/sbin/nologin" \
    --no-create-home \
    --uid "${UID}" \
    appuser
USER appuser

WORKDIR /funmesh

COPY ./funmesh.jar funmesh.jar

#ARG basePort=8080
# the following expose is informative only:
#EXPOSE $basePort

# "-Djava.net.preferIPv4Stack=true",
CMD [ "headless=true" ]
ENTRYPOINT [ "java", "-jar", "funmesh.jar"]
