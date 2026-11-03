#!/bin/sh
set -eu
umask 077

# Also initialize an existing volume from the previous image, without running as root.
semillero_runtime_tmp="${JAVA_IO_TMPDIR:-/var/lib/semillero/tmp/runtime}"
for semillero_directory in \
    "${VIDEO_STORAGE_DIR:-/var/lib/semillero/videos}" \
    "${ANALYSIS_ARTIFACTS_DIR:-/var/lib/semillero/analysis}" \
    /var/lib/semillero/youtube \
    "${SPRING_SERVLET_MULTIPART_LOCATION:-/var/lib/semillero/tmp/multipart}" \
    "$semillero_runtime_tmp"
do
    mkdir -p "$semillero_directory"
    if [ ! -w "$semillero_directory" ]; then
        printf 'No se puede escribir en el almacenamiento configurado: %s\n' "$semillero_directory" >&2
        exit 1
    fi
done

if [ "${VIDEO_YOUTUBE_ENABLED:-false}" = "true" ]; then
    semillero_youtube_client="${VIDEO_YOUTUBE_CLIENT:-/var/lib/semillero/youtube/client_secret.json}"
    semillero_youtube_tokens="${VIDEO_YOUTUBE_TOKENS:-/var/lib/semillero/youtube/tokens.json}"
    if [ ! -r "$semillero_youtube_client" ] || [ ! -r "$semillero_youtube_tokens" ]; then
        printf 'YouTube requiere cliente OAuth y tokens legibles en el volumen privado; iniciar deshabilitado para instalarlos.\n' >&2
        exit 1
    fi
    if [ ! -w "$(dirname "$semillero_youtube_tokens")" ]; then
        printf 'YouTube requiere un directorio de tokens escribible para renovar OAuth.\n' >&2
        exit 1
    fi
fi

exec java "-Djava.io.tmpdir=$semillero_runtime_tmp" -jar /app/app.jar "$@"
