# Entrega de Spark y YouTube para el VPS

Esta entrega completa el encadenamiento automático: carga autorizada → análisis Spark → JSON y mapa persistidos → publicación no listada en YouTube → ID y enlace guardados. El modelo sigue entregando segmentos y dorsales candidatos; no se publican automáticamente métricas deportivas vinculadas a identidades reales.

## Cambios incluidos

- El JSON se confirma en PostgreSQL antes de pedir el mapa, porque Spark elimina el trabajo después de ambas entregas. Los checkpoints y el estado del mapa permiten recuperar el proceso sin volver a subir el video.
- La publicación espera a que termine la recepción de resultados. Los workers persistidos conservan original, fecha del partido y recibo reanudable; el recibo permite consultar una publicación ya creada sin duplicarla.
- La imagen incluye Java 21, FFmpeg/FFprobe y Python con dependencias de YouTube fijadas en `scripts/youtube-requirements.lock`. El uploader y su intérprete están en `/app/scripts/youtube_upload.py` y `/opt/youtube/bin/python`.
- El perfil VPS y Compose permiten habilitar ambos workers por variables; los interruptores siguen desactivados por defecto. La raíz de la imagen continúa siendo de solo lectura y la aplicación corre como UID/GID 10001.
- OAuth, metadatos del canal, originales, recibos y PNG se guardan en el volumen persistente. El uploader ya no intenta escribir `channel.json` dentro de `/app`.

## Migraciones antes de actualizar

El migrador central debe confirmar **V013 y V015** antes de iniciar la imagen. V015 permite guardar el JSON mientras el trabajo aún está `PROCESANDO` y agrega el estado del mapa. V000–V013 no se modifican. V014 perteneció a una propuesta retirada y no se reutiliza.

Conservar **Flyway desactivado en el runtime e Hibernate `validate`**. Si faltan tablas o columnas, coordinar con el administrador; no usar `init.sql` sobre la base compartida ni cambiar Hibernate a `update`. `init.sql` es solamente para bases vacías.

## Preparación del runtime

La configuración de red acordada no cambia: Docker rootless, `network_mode: host`, Spring en `127.0.0.1:18083` y PostgreSQL en `jdbc:postgresql://127.0.0.1:5432/semillero`. Las credenciales SQL siguen en `/home/backend/proyecto/.env.db`. Conservar `JWT_SECRET`, `PUBLIC_WEB_ORIGIN`, imagen anterior y volumen para la actualización.

Construir desde un commit publicado y verificado:

```bash
bash ./mvnw --batch-mode --no-transfer-progress clean verify
VERSION=$(git rev-parse --short=12 HEAD)
REVISION=$(git rev-parse HEAD)
docker build --platform linux/amd64 \
  --label "org.opencontainers.image.revision=$REVISION" \
  --label "org.opencontainers.image.source=https://github.com/Matiasnm14/semillero-app" \
  -f VPS-Implementacion/backend-compartido/Dockerfile \
  -t "semillero-backend:$VERSION" .
docker save --output imagen.tar "semillero-backend:$VERSION"
```

El contexto de Docker admite únicamente el JAR, entrypoint, uploader y lock de Python; excluye `.local`, `.env`, OAuth y videos de prueba. Para validar las migraciones, ejecutar la suite con las tres bases PostgreSQL desechables indicadas en [BASE_DATOS_DOCKER.md](BASE_DATOS_DOCKER.md); sin esas variables las pruebas PostgreSQL se omiten.

## Instalar OAuth sin incluirlo en la imagen

Primero iniciar el contenedor con YouTube deshabilitado. Autorizar y verificar el canal en una computadora con navegador:

```bash
.local/youtube/venv/bin/python -u scripts/youtube_upload.py authorize --reauthorize
```

La cuenta debe corresponder a `@Semillero-Video-Analysis`. Transferir `client_secret.json` y `tokens.json` por el canal privado aprobado al usuario `backend`, con permisos 0600. No incluirlos en el paquete compartible ni imprimir su contenido. Si Google vuelve a rechazar la renovación, repetir el consentimiento en la computadora y reemplazar los tokens de manera controlada; el worker nunca abre un flujo interactivo en el servidor.

Desde `~/proyecto/backend`, con el contenedor iniciado y ambos interruptores de YouTube desactivados, instalar los archivos como el usuario de la aplicación. Sustituir las rutas de entrada por los archivos privados transferidos:

```bash
docker --context rootless compose --project-name semillero-backend \
  --env-file /home/backend/proyecto/.env.db --env-file .env \
  exec -T backend sh -c 'umask 077; cat > /var/lib/semillero/youtube/client_secret.json' \
  < /ruta/privada/client_secret.json

docker --context rootless compose --project-name semillero-backend \
  --env-file /home/backend/proyecto/.env.db --env-file .env \
  exec -T backend sh -c 'umask 077; cat > /var/lib/semillero/youtube/tokens.json' \
  < /ruta/privada/tokens.json
```

El directorio debe ser escribible por UID 10001: Google puede renovar el token y el uploader escribe mediante reemplazo atómico. Montar solo `tokens.json` como archivo de solo lectura impide esa renovación. Los recibos se conservan junto a cada original, dentro de `/var/lib/semillero/videos/<storageId>/youtube-upload.json`.

Comprobar OAuth desde el contenedor antes de habilitar los workers:

```bash
docker --context rootless compose --project-name semillero-backend \
  --env-file /home/backend/proyecto/.env.db --env-file .env exec -T backend \
  /opt/youtube/bin/python /app/scripts/youtube_upload.py authorize --non-interactive \
  --client /var/lib/semillero/youtube/client_secret.json \
  --tokens /var/lib/semillero/youtube/tokens.json \
  --channel Semillero-Video-Analysis
```

## Activar en el .env privado

```dotenv
ANALYSIS_ENABLED=true
ANALYSIS_WORKER_ENABLED=true
ANALYSIS_BASE_URL=https://spark-0aa4.tail763771.ts.net
ANALYSIS_TOKEN=<credencial privada de Spark>
VIDEO_YOUTUBE_ENABLED=true
VIDEO_YOUTUBE_WORKER_ENABLED=true
VIDEO_YOUTUBE_CHANNEL=Semillero-Video-Analysis
```

Después de confirmar esquema, credenciales, conectividad y disco, recrear la versión:

```bash
docker --context rootless compose --project-name semillero-backend \
  --env-file /home/backend/proyecto/.env.db --env-file .env config --quiet
docker --context rootless compose --project-name semillero-backend \
  --env-file /home/backend/proyecto/.env.db --env-file .env \
  up -d --no-build --pull never --wait --wait-timeout 180
curl --fail --show-error 'http://127.0.0.1:18083/api/v1/paises?size=1'
```

No usar `down -v`; conservar los datos y recibos entre versiones. Antes de una reversión, desactivar los workers y comprobar la compatibilidad del esquema con la imagen anterior. El video de prueba en producción debe ser pequeño: el límite de 2 GiB de Spring no elimina los límites de Cloudflare, del proxy ni del disco.

## Validación repetible del flujo real

La prueba optativa `SparkLiveIT` y su lanzador quedan fuera de la selección normal de Surefire. Preparar una base local desechable `spark_live_test`, usuario homónimo, y OAuth válido en `.local/youtube`:

```bash
python3 scripts/test_spark_live.py \
  --video /ruta/a/clip-corto.mp4 \
  --db-url jdbc:postgresql://127.0.0.1:55439/spark_live_test \
  --with-youtube
```

Con `--with-youtube`, la prueba activa los dos workers programados. El cliente solo carga y consulta; no invoca `processNext` ni ejecuta el uploader manualmente. Comprueba el estado `ESPERANDO_ANALISIS` al aceptar la carga, resultados persistidos, borrado remoto, publicación `DISPONIBLE`, título con fecha del partido, canal correcto y privacidad `unlisted`. Los tokens y recibos completos quedan en carpetas ignoradas. La ejecución publica un video real no listado.

El 2026-11-02 se aprobó este flujo con el clip de fútbol de 8 segundos: análisis completado, mapa disponible, YouTube disponible, título `2026-11-02` y privacidad `unlisted`, usando únicamente los workers programados. La evidencia privada está en `.local/analysis/flujo-automatico-20261102/`; no se realizaron cambios en el VPS.

La validación de entrega completó **521 pruebas Java y 15 pruebas Python**, sin fallos, errores ni omisiones. La imagen Docker se construyó y comprobó localmente con el perfil VPS, usuario 10001, raíz de solo lectura, red host y límite de 1 GiB. Se verificaron login con contraseña, JWT, CORS, JSON/PNG persistidos, OAuth privado renovable, recuperación del mismo ID de YouTube sin duplicación y persistencia después de reiniciar. Flyway estuvo desactivado y Hibernate en `validate` en el contenedor. Evidencia privada: `.local/vps/entrega-completa-20261102/`. Esta prueba local no acredita un despliegue ya realizado en el VPS.
