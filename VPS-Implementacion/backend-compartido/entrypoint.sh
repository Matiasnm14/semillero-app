#!/bin/sh
set -eu
umask 077

# Also initialize an existing volume from the previous image, without running as root.
semillero_runtime_tmp="${JAVA_IO_TMPDIR:-/var/lib/semillero/tmp/runtime}"
for semillero_directory in \
    "${VIDEO_STORAGE_DIR:-/var/lib/semillero/videos}" \
    "${ANALYSIS_ARTIFACTS_DIR:-/var/lib/semillero/analysis}" \
    "${SPRING_SERVLET_MULTIPART_LOCATION:-/var/lib/semillero/tmp/multipart}" \
    "$semillero_runtime_tmp"
do
    mkdir -p "$semillero_directory"
    if [ ! -w "$semillero_directory" ]; then
        printf 'No se puede escribir en el almacenamiento configurado: %s\n' "$semillero_directory" >&2
        exit 1
    fi
done

exec java "-Djava.io.tmpdir=$semillero_runtime_tmp" -jar /app/app.jar "$@"
