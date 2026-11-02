FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ffmpeg \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 semillero \
    && useradd --uid 10001 --gid 10001 --no-log-init --home-dir /app --shell /usr/sbin/nologin semillero \
    && mkdir -p /app /var/lib/semillero/videos /var/lib/semillero/analysis \
        /var/lib/semillero/tmp/multipart /var/lib/semillero/tmp/runtime \
    && chown -R 10001:10001 /app /var/lib/semillero \
    && chmod -R 750 /var/lib/semillero

WORKDIR /app
COPY --chown=10001:10001 semillero-api/target/semillero-api-0.0.1-SNAPSHOT.jar /app/app.jar
COPY --chown=10001:10001 VPS-Implementacion/backend-compartido/entrypoint.sh /app/entrypoint.sh
RUN chmod 550 /app/entrypoint.sh

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["/app/entrypoint.sh"]
