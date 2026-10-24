# HU01 — Carga de videos de partidos

La API recibe un MP4, valida el partido y el contenido y espera el JSON final del analizador Python. Solo acepta la carga cuando conserva el original y guarda sus metadatos junto con un análisis `COMPLETADO` y su respuesta. La publicación opcional en YouTube empieza después del commit, mediante una cola persistida en PostgreSQL. Ver [integración automática](INTEGRACION_AUTOMATICA_VIDEO.md).

Corresponde a SB-001 (validación), SB-002 (registro y metadatos) y SB-003 (pruebas). La integración con YouTube conserva el trabajo del piloto: OAuth del propietario, canal verificado y subida reanudable con visibilidad No listado.

## Preparación

1. Instalar FFmpeg, incluyendo `ffmpeg` y `ffprobe`, en el host del backend. Deben poder ejecutarse desde su usuario.
2. Configurar `.env` a partir de `.env.example` y exportar sus variables. El directorio de trabajo de los comandos siguientes es la raíz del proyecto.
3. Levantar el analizador Python y configurar `ANALYSIS_ENABLED=true`, `ANALYSIS_BASE_URL` y su ruta multipart. Mientras esté deshabilitado, la carga devuelve `503`; los CRUD siguen disponibles.
4. Para publicar en YouTube, preparar el entorno Python y autorizar una vez el canal según [YOUTUBE_SETUP.md](YOUTUBE_SETUP.md). Usar rutas absolutas en la configuración cuando se ejecute desde otro directorio.

```bash
python3 -m venv .local/youtube/venv
.local/youtube/venv/bin/python -m pip install -r scripts/youtube-requirements.txt
.local/youtube/venv/bin/python scripts/youtube_upload.py authorize
```

En `.env`, activar `VIDEO_YOUTUBE_ENABLED=true` después de autorizar. Reiniciar el backend para aplicar la configuración. Los compañeros que no tengan acceso al canal pueden trabajar con `false`: la carga y el análisis funcionan con Python disponible, sin publicar externamente.

El backend invoca el uploader Python mediante una lista de argumentos, sin shell, con `--non-interactive`. Si Google exige renovar el consentimiento, el video queda en `ERROR`; el propietario ejecuta `authorize --reauthorize` y luego solicita el reintento. Una petición HTTP nunca abre una sesión de consentimiento OAuth.

## Esquema existente

Las instalaciones nuevas con el Compose actual ya cargan 000–004 mediante [init.sql](../database/init.sql). Las instrucciones manuales siguientes son para bases existentes que todavía necesiten el cambio; consultar la [guía del equipo](BASE_DATOS_DOCKER.md).

Se añade `videos_partido`, con una FK a `partidos`. El CRUD `/api/v1/videos` conserva su modelo de enlaces de videos de jugadores.

En desarrollo, el perfil `dev` puede crear la tabla mediante `ddl-auto=update`. Para una base existente que arranque con `ddl-auto=validate`, aplicar **una sola vez**, antes del arranque, [001_videos_partido.sql](../database/migrations/001_videos_partido.sql). No ejecutar ese SQL después de que `dev` ya haya creado la tabla.

Con PostgreSQL de Docker Compose, desde la raíz y con `.env` exportado:

```bash
docker compose exec -T db psql -v ON_ERROR_STOP=1 \
  -U "$DB_USER" -d "$DB_NAME" < database/migrations/001_videos_partido.sql
```

El esquema completo está versionado en 000–004; las bases existentes aplican manualmente sus migraciones pendientes. La aceptación conjunta de video y JSON reutiliza ese esquema y no requiere una migración adicional.

## API

| Método | Ruta | Resultado |
| --- | --- | --- |
| `POST` | `/api/v1/partidos/{partidoId}/videos` | Recibe multipart `file`, espera el JSON y confirma video y análisis; devuelve `201 Created`, metadatos y `Location` |
| `GET` | `/api/v1/partidos/{partidoId}/videos` | Lista paginada con `content`, `page`, `size`, `totalElements`, `totalPages` |
| `GET` | `/api/v1/partidos/{partidoId}/videos/{id}` | Recupera metadatos y estado de publicación |
| `POST` | `/api/v1/partidos/{partidoId}/videos/{id}/youtube/reintentar` | Encola un reintento de publicación; devuelve `202 Accepted` |

Primero crear un partido con fecha y clubes válidos mediante el CRUD existente. Reemplazar `1` por el ID devuelto:

```bash
curl --fail-with-body -i \
  -F 'file=@cronoshift.mp4;type=video/mp4' \
  http://localhost:8080/api/v1/partidos/1/videos

curl --fail-with-body http://localhost:8080/api/v1/partidos/1/videos/1

# El análisis ya fue creado; consultar el listado, sin un segundo POST.
curl --fail-with-body http://localhost:8080/api/v1/partidos/1/videos/1/analisis
```

Ejemplo de respuesta inicial, con YouTube habilitado:

```json
{
  "id": 1,
  "partidoId": 1,
  "nombreOriginal": "cronoshift.mp4",
  "formato": "mp4",
  "tamanoBytes": 60013506,
  "duracionSegundos": 154.922667,
  "sha256": "<checksum SHA-256 del original>",
  "fechaCarga": "2026-10-21T15:00:00Z",
  "disponibleParaAnalisis": true,
  "tituloYoutube": "2026-10-21",
  "estadoYoutube": "PENDIENTE",
  "youtubeId": null,
  "youtubeUrl": null,
  "errorYoutube": null
}
```

`tituloYoutube` se obtiene de la fecha de inicio del partido en formato `AAAA-MM-DD`, no del día de carga. Se conserva al registrar el archivo. El nombre original del archivo se guarda por separado. Varios videos de la misma fecha pueden compartir título; sus IDs y archivos son distintos.

`disponibleParaAnalisis` indica que el original validado sigue presente en el almacenamiento. Las cargas nuevas ya tienen un análisis `COMPLETADO` con `respuestaRecibida=true`; las antiguas pueden no tenerlo. Este campo no indica publicación de métricas de HU12. El estado de YouTube describe únicamente la publicación externa y no es un estado de `AnalysisJob`.

## Validación y almacenamiento

- Se admite inicialmente MP4 con una pista de video que FFmpeg pueda decodificar. Un archivo de solo audio se rechaza.
- Se verifica partido existente, archivo no vacío y tamaño máximo. Se comprueba el contenido con `ffprobe` y se decodifican todas las pistas de video principal/audio con FFmpeg para detectar paquetes corruptos; aceptar la extensión o leer solo metadatos no basta. Véase la [documentación de ffprobe](https://ffmpeg.org/ffprobe.html).
- Se conservan nombre, formato, tamaño real, duración cuando se puede extraer, checksum y fecha de carga. El límite también se comprueba durante la copia, sin confiar únicamente en el tamaño declarado.
- El archivo recibido se copia a un directorio UUID como `upload.part`; se valida y se envía ese temporal por multipart a Python. Solo después de recibir JSON válido se confirma como `original.mp4`. Un fallo normal de validación, evaluación o persistencia elimina la carga sin confirmar.
- Copia, decodificación y espera del `CompletableFuture` ocurren fuera de la transacción SQL; video y análisis completado se guardan en una única transacción corta. La integración usa [TransactionTemplate](https://docs.spring.io/spring-framework/reference/data-access/transaction/programmatic.html) para compensar también un error al confirmar la transacción. El worker de YouTube no puede ver una publicación pendiente antes del commit.
- Los nombres del cliente no se utilizan como rutas de almacenamiento. La API no devuelve rutas locales, tokens, URI de sesión ni trazas internas.

El directorio `VIDEO_STORAGE_DIR` debe ser persistente, con espacio para originales y acceso para el usuario del backend. Su contenido está ignorado por Git y no se sirve como directorio web. Si hay varias instancias, deben compartir el almacenamiento y los recibos correspondientes. Mantener juntos respaldo de la base y de los archivos.

Validación, evaluación y commit suceden antes de responder `201`; la petición mantiene ocupado su hilo servlet mientras espera el Future con timeout. Para videos largos hay que ajustar los plazos del proxy/cliente/servidor al tiempo total, no solo a la transferencia. Un cierre abrupto entre escribir el archivo y confirmar SQL puede dejar un directorio sin registro: no queda disponible por la API y requiere conciliación operativa. No se incorpora borrado automático ni retención en HU01. Cancelar el intercambio local no garantiza detener Python ni recuperar su resultado después de un reinicio.

## Publicación en YouTube

El worker consulta cada 15 segundos los registros pendientes. Solo encuentra videos y JSON ya aceptados. Reclama cada trabajo con bloqueo de fila y token; realiza la llamada externa fuera de la transacción SQL. La cola sobrevive a reinicios. Un trabajo interrumpido se recupera al vencer su reserva, cuyo plazo es el timeout de YouTube más 60 segundos; con los valores por defecto, hasta una hora y un minuto. Si YouTube falla, se conservan el video y el análisis aceptados.

Cada video tiene su propio recibo `youtube-upload.json` junto al original. Se reutiliza para consultar y reanudar la [subida reanudable de YouTube](https://developers.google.com/youtube/data/guides/using_resumable_upload_protocol). El script comprueba el canal autorizado, usa categoría Deportes (`17`), No listado (`unlisted`), no notifica a suscriptores y verifica título, canal, privacidad y procesamiento remoto.

| Estado | Significado / siguiente paso |
| --- | --- |
| `DESHABILITADO` | Publicación desactivada; activar configuración y solicitar reintento si se desea publicar |
| `PENDIENTE` | Registro confirmado, esperando al worker |
| `SUBIENDO` | Worker con reserva activa; puede estar transfiriendo o verificando el video |
| `PROCESANDO` | YouTube recibió el video; se vuelve a consultar después de 30 segundos |
| `DISPONIBLE` | YouTube confirmó procesamiento y metadatos esperados |
| `ERROR` | Falló la operación; original conservado y reintento manual disponible |
| `REVISION_REQUERIDA` | Sesión expirada, metadatos inesperados o rechazo remoto; revisar canal/recibo antes de otra publicación |

Un reintento reutiliza original, título y recibo. Si ya existe un `videoId` confirmado en el recibo, el uploader consulta ese video. No borrar recibos para forzar un reintento: podría duplicar una subida cuya respuesta se perdió. Los casos de revisión requieren conciliación por el propietario; no se habilita un reintento automático ciego.

```bash
curl --fail-with-body -i -X POST \
  http://localhost:8080/api/v1/partidos/1/videos/1/youtube/reintentar
```

El endpoint permite reintentar desde `ERROR` o `DESHABILITADO` con YouTube habilitado. Devuelve `409` si ya está en curso, publicado, deshabilitado por configuración o requiere revisión; `503` si el original falta. Este reintento es de publicación en YouTube; HU16 cubrirá los reintentos de análisis.

## Configuración

| Variable | Valor por defecto / uso |
| --- | --- |
| `VIDEO_STORAGE_DIR` | `.local/videos`; almacenamiento persistente del original y recibo |
| `VIDEO_MAX_BYTES` | `2147483648` (2 GiB); límite comprobado al copiar |
| `VIDEO_MAX_FILE_SIZE` | `2GB`; límite multipart por archivo |
| `VIDEO_MAX_REQUEST_SIZE` | `2049MB`; límite multipart total |
| `VIDEO_FFPROBE`, `VIDEO_FFMPEG` | `ffprobe`, `ffmpeg`; rutas de las herramientas |
| `VIDEO_VALIDATION_TIMEOUT` | `1h`; tiempo máximo de decodificación; inspección inicial limitada a 60 segundos |
| `VIDEO_YOUTUBE_ENABLED` | `false`; activa publicación en segundo plano |
| `VIDEO_YOUTUBE_PYTHON` | `.local/youtube/venv/bin/python` |
| `VIDEO_YOUTUBE_SCRIPT` | `scripts/youtube_upload.py` |
| `VIDEO_YOUTUBE_CLIENT` | `.local/youtube/client_secret.json` |
| `VIDEO_YOUTUBE_TOKENS` | `.local/youtube/tokens.json` |
| `VIDEO_YOUTUBE_CHANNEL` | `Semillero-Video-Analysis` |
| `VIDEO_YOUTUBE_TIMEOUT` | `1h`; máximo de ejecución del uploader |
| `ANALYSIS_ENABLED` | `false`; activar para aceptar nuevas cargas |
| `ANALYSIS_BASE_URL` | Base del analizador; obligatoria con análisis habilitado |
| `ANALYSIS_REQUEST_TIMEOUT` | `2h`; espera máxima del JSON final |
| `ANALYSIS_MAX_CONCURRENT_JOBS` | `1`; cupo compartido entre cargas y worker por instancia |

Al cambiar tamaño máximo, ajustar los tres límites de tamaño de forma consistente y los del proxy. Los tests desactivan el scheduler con `video.youtube.worker-enabled=false`; ejercitan el worker explícitamente y sustituyen el publicador para no consumir cuota ni crear videos.

## Errores HTTP y pruebas

| Código | Casos |
| --- | --- |
| `400` | Falta `file`, nombre/formato inválido, archivo vacío, corrupto o sin video |
| `404` | Partido inexistente o video que no pertenece al partido solicitado |
| `409` | Restricción relacional o reintento incompatible con el estado |
| `413` | Tamaño excedido |
| `502` | Error remoto, fallo de transporte o JSON del analizador inválido |
| `503` | Analizador deshabilitado o sin cupo; almacenamiento o herramientas no disponibles; validación interrumpida o agotada |
| `504` | El analizador no terminó dentro del plazo |

```bash
./mvnw clean verify
.local/youtube/venv/bin/python -m unittest discover -s scripts/tests -v
```

Las pruebas Java requieren `ffmpeg` y `ffprobe`: incluyen archivos MP4 sintéticos pequeños y casos de corrupción de paquetes. Se cubren metadatos, FK, límites, compensación de fallos, cola, reanudación de reservas, publicación concurrente y protección frente a recibos remotos inválidos. Las pruebas del script cubren rangos, respuesta final perdida, verificación de canal/privacidad y ausencia de consentimiento interactivo en el worker.

### Verificación realizada el 2026-10-21

- `./mvnw verify`: 47 pruebas Java correctas (23 del CRUD existente, 17 de integración HU01 y 7 del adaptador de publicación).
- Script Python: 14 pruebas correctas, sin llamadas a Google durante las pruebas unitarias.
- PostgreSQL 17 temporal: aplicación manual del SQL sobre el esquema base existente y arranque del JAR empaquetado con `ddl-auto=validate`.
- Carga HTTP de `cronoshift.mp4`: 60.013.506 bytes, duración 154,922667 segundos, título `2026-10-21`, vínculo al partido y original conservados tras reiniciar.
- Worker Java → uploader Python no interactivo → API real de YouTube: reutilización del recibo confirmado del piloto, mismo video remoto, estado final `DISPONIBLE` y privacidad real `unlisted`. Esta comprobación no creó otra subida; la transferencia real original se verificó en el piloto y la recuperación se comprobó con este flujo.

El backend y PostgreSQL temporales se detuvieron después de la verificación. La base del proyecto y los issues remotos no se modificaron.
