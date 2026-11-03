FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ffmpeg python3 python3-venv \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 semillero \
    && useradd --uid 10001 --gid 10001 --no-log-init --home-dir /app --shell /usr/sbin/nologin semillero \
    && mkdir -p /app/scripts /var/lib/semillero/videos /var/lib/semillero/analysis /var/lib/semillero/youtube \
        /var/lib/semillero/tmp/multipart /var/lib/semillero/tmp/runtime \
    && chown -R 10001:10001 /app /var/lib/semillero \
    && chmod -R 750 /var/lib/semillero

WORKDIR /app
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 PIP_DISABLE_PIP_VERSION_CHECK=1
COPY scripts/youtube-requirements.lock /app/scripts/youtube-requirements.lock
RUN python3 -m venv /opt/youtube \
    && /opt/youtube/bin/pip install --no-cache-dir -r /app/scripts/youtube-requirements.lock \
    && /opt/youtube/bin/pip check
COPY --chown=10001:10001 scripts/youtube_upload.py /app/scripts/youtube_upload.py
COPY --chown=10001:10001 semillero-api/target/semillero-api-0.0.1-SNAPSHOT.jar /app/app.jar
COPY --chown=10001:10001 VPS-Implementacion/backend-compartido/entrypoint.sh /app/entrypoint.sh
RUN chmod 550 /app/entrypoint.sh /app/scripts/youtube_upload.py

USER 10001:10001
EXPOSE 18083
ENTRYPOINT ["/app/entrypoint.sh"]
