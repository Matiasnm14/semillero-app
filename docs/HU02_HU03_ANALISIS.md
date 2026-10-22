# HU02 y HU03 — Solicitud y consulta de análisis

El backend registra trabajos durables en PostgreSQL, evita solicitudes duplicadas del mismo video y permite consultar estado, avance y finalización. La ejecución se delega al microservicio Python mediante el contrato de este documento.

**Estado de integración:** el servicio Python todavía está en desarrollo. El adaptador HTTP y el worker Java están implementados y se prueban con un servidor controlado. La integración viene deshabilitada; esta entrega no implementa detección, tracking ni cálculo de métricas.

Corresponde a [HU02](https://github.com/s-villanueva/video-data-analysis/issues/6), subtareas SB-004/#20, SB-005/#21 y SB-006/#22, y [HU03](https://github.com/s-villanueva/video-data-analysis/issues/8), subtareas SB-007/#23, SB-008/#24 y SB-009/#25. El [plan original](PLAN_HU02_HU03_ANALISIS.md) conserva el diagnóstico y las decisiones de diseño.

## Preparar la base

Las instalaciones nuevas con Docker Compose ya reciben 000–003 mediante [init.sql](../database/init.sql). Aplicar las instrucciones manuales siguientes solo a una base existente que aún necesite 002; consultar la [guía del equipo](BASE_DATOS_DOCKER.md).

Se requiere el esquema existente de Semillero con [HU01](HU01_CARGA_VIDEOS.md). Aplicar una sola vez [002_analysis_jobs.sql](../database/migrations/002_analysis_jobs.sql), antes de arrancar con `ddl-auto=validate`.

En Bash, con las variables de `.env` exportadas:

```bash
docker compose exec -T db psql -v ON_ERROR_STOP=1 \
  -U "$DB_USER" -d "$DB_NAME" < database/migrations/002_analysis_jobs.sql
```

Si `dev` ya creó `analysis_jobs`, el mismo SQL conserva esa tabla y agrega las comprobaciones e índice único parcial. Se ejecuta una sola vez: las restricciones e índice único no se crean dos veces. Si ya hay datos incompatibles o trabajos activos duplicados, la transacción falla y hay que conciliarlos antes de repetirla; la migración no elimina registros.

`ddl-auto=update` por sí solo no crea el índice único parcial de PostgreSQL. Ejecutar el SQL también en desarrollo para tener la protección completa. Las pruebas H2 comprueban el bloqueo del servicio; la prueba opcional de PostgreSQL comprueba la restricción real, el SQL y el arranque con `validate`.

## API pública

| Método | Ruta | Respuesta |
| --- | --- | --- |
| `POST` | `/api/v1/partidos/{partidoId}/videos/{videoId}/analisis` | `202 Accepted`, `Location` y trabajo en PENDIENTE. No requiere cuerpo ni vuelve a cargar el archivo. |
| `GET` | `/api/v1/analisis/{id}` | `200`, estado persistido del trabajo. |
| `GET` | `/api/v1/partidos/{partidoId}/videos/{videoId}/analisis` | Página de análisis del video, más recientes primero. Admite `page`, `size` y `sort`. |

Ejemplo, después de cargar un MP4 con HU01:

```bash
curl --fail-with-body -i -X POST \
  http://localhost:8080/api/v1/partidos/1/videos/7/analisis

curl --fail-with-body http://localhost:8080/api/v1/analisis/101
curl --fail-with-body http://localhost:8080/api/v1/partidos/1/videos/7/analisis
```

Respuesta de consulta de ejemplo:

```json
{
  "id": 101,
  "partidoId": 1,
  "videoId": 7,
  "estado": "PROCESANDO",
  "porcentaje": 35.0,
  "fotogramasProcesados": 2100,
  "fotogramasTotales": 6000,
  "fechaSolicitud": "2026-10-21T19:00:00Z",
  "fechaInicio": "2026-10-21T19:00:03Z",
  "fechaFin": null,
  "fechaActualizacion": "2026-10-21T19:00:20Z",
  "resultadosDisponibles": false,
  "codigoError": null,
  "mensajeError": null
}
```

El cliente puede consultar cada 3–5 segundos y detener el polling al llegar a COMPLETADO o ERROR. Al volver a la pantalla recupera el ID mediante el listado del video. Ninguna sesión del navegador es dueña del trabajo; abandonar la pantalla no cancela el procesamiento.

Los estados son PENDIENTE, PROCESANDO, COMPLETADO y ERROR. La reserva del worker no cambia PENDIENTE: PROCESANDO solo se registra cuando Python confirma el inicio. Una consulta puede observar directamente un estado terminal si el servicio ya terminó entre dos consultas.

El avance es opcional. Se aceptan porcentaje entre 0 y 100, fotogramas procesados o ambos; el total debe ser positivo si se conoce. Se conservan las últimas mediciones reales si una respuesta posterior las omite. Nunca se estima avance por tiempo transcurrido ni se asigna 100 automáticamente al completar.

COMPLETADO tiene fecha de finalización. `resultadosDisponibles` solo es verdadero cuando el servicio confirma una referencia de resultados para ese trabajo; puede ser falso al terminar. La API pública no expone esa referencia interna ni implementa los endpoints de métricas de HU12/HU13. El worker deja de consultar al alcanzar un estado terminal: la disponibilidad representa la última confirmación recibida; una publicación posterior de resultados requerirá la integración correspondiente de HU12.

### Errores

| Código | Caso |
| --- | --- |
| `400` | IDs no positivos, tipos o parámetros inválidos. |
| `404` | Partido, video o análisis inexistente; video ajeno al partido. |
| `409` | El video ya tiene análisis activo, o requiere reproceso explícito de un análisis anterior. |
| `503` | Integración deshabilitada/no configurada o falta el archivo original al crear la solicitud. |

El conflicto usa `ProblemDetail` con `analysisId` y `analysisUrl`, para recuperar el trabajo existente sin crear otro. Como política inicial se rechaza también una nueva solicitud sobre un video con análisis terminal: HU16 incorporará el nuevo intento confirmado conservando el historial. No hay un flag de reproceso implícito.

Una caída temporal de Python después de aceptar la solicitud no afecta al GET público, que consulta SQL. `fechaActualizacion` indica cuándo cambió por última vez el estado o avance confirmado. Los errores del analizador se traducen a mensajes controlados; el backend no devuelve cuerpos HTTP remotos, credenciales ni trazas.

## Contrato interno para el servicio Python

Estas rutas deben ser implementadas por Python. No son endpoints que exponga el backend Java.

### Registrar o recuperar un trabajo

`PUT {ANALYSIS_BASE_URL}/internal/v1/analisis/{analysisId}`

```json
{
  "analysisId": 101,
  "videoId": 7,
  "partidoId": 1,
  "storageId": "3b4e6f22-4550-4da6-9c99-63085e5d2a3c",
  "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
}
```

Python debe persistir la identidad `analysisId` antes de confirmar y asegurar unicidad de esa clave. Un PUT repetido con el mismo contenido recupera el trabajo y su estado, sin iniciar otro proceso. El mismo ID con contenido diferente debe producir `409`. Esta garantía es necesaria cuando la respuesta se pierde o Java se reinicia después de que Python recibió el trabajo.

El original se resuelve como `{raiz_compartida}/{storageId}/original.mp4`. El servicio valida el UUID, resuelve contra su propia raíz de solo lectura y verifica el checksum. No debe interpretar `storageId` como una ruta arbitraria. Compartir `VIDEO_STORAGE_DIR` entre los procesos; una ruta del host Windows no funciona automáticamente dentro de otro host/contenedor. Si se adopta object storage o transferencia por HTTP, deberá adaptarse este contrato.

### Consultar el mismo trabajo

`GET {ANALYSIS_BASE_URL}/internal/v1/analisis/{analysisId}`

PUT admite `200`, `201` o `202`; GET requiere `200`. Ambos devuelven un snapshot completo con este formato:

```json
{
  "analysisId": 101,
  "revision": 3,
  "estado": "COMPLETADO",
  "porcentaje": 100.0,
  "fotogramasProcesados": 6000,
  "fotogramasTotales": 6000,
  "fechaInicio": "2026-10-21T19:00:03Z",
  "fechaFin": "2026-10-21T19:03:00Z",
  "resultadosDisponibles": true,
  "resultadoRef": "resultados:101",
  "codigoError": null
}
```

- `analysisId`, `revision`, `estado` y `resultadosDisponibles` son obligatorios. `revision` es un entero no negativo, creciente por trabajo; una respuesta con revisión igual o menor se ignora. Python debe incrementarla cuando cambien estado, avance, fechas o disponibilidad.
- Fechas ISO 8601 con zona; se guardan con precisión de microsegundos. PROCESANDO y COMPLETADO requieren `fechaInicio`, que permanece estable. COMPLETADO y ERROR requieren `fechaFin`; no puede ser anterior al inicio. PENDIENTE no lleva inicio ni fin.
- Porcentaje y fotogramas pueden ser null. Las mediciones no retroceden y no pueden contradecir el total conocido, incluso en actualizaciones parciales.
- Solo COMPLETADO puede indicar resultados disponibles y debe acompañarlo con `resultadoRef` no vacía, de hasta 255 caracteres, vinculada al mismo análisis. No basta con que el proceso haya salido con código cero.
- Para ERROR, `codigoError="VIDEO_INVALIDO"` produce el mensaje específico; otros códigos se traducen a `ANALISIS_FALLIDO`. No es necesario enviar una traza o mensaje interno.
- La respuesta JSON tiene un límite de 64 KiB. HTTP 408, 429 y 5xx se consideran fallos transitorios; otros códigos inesperados y datos incompatibles producen ERROR. No se siguen redirecciones.
- Con `ANALYSIS_TOKEN` configurado, Java envía `Authorization: Bearer ...`. Python debe comprobarlo. En despliegues remotos, configurar HTTPS y el mecanismo de acceso entre servicios; no publicar el volumen de originales como un directorio web.

## Worker y configuración

La tabla `analysis_jobs` es la cola durable. El worker reclama un trabajo vencido mediante bloqueo de fila, token y reserva; realiza una única llamada HTTP fuera de la transacción y confirma la respuesta en una transacción corta. Una respuesta de una reserva vencida o reemplazada no puede escribir.

Después de reiniciar se recuperan las reservas vencidas. Si Java no confirmó el PUT, lo reenvía con el mismo ID; si ya lo confirmó, utiliza GET. El mismo trabajo soporta reintentos técnicos con espera creciente. El scheduler de análisis está separado del de YouTube.

| Variable | Por defecto | Uso |
| --- | --- | --- |
| `ANALYSIS_ENABLED` | `false` | Habilitar creación y ejecución cuando Python esté listo. |
| `ANALYSIS_BASE_URL` | Vacío | Base HTTP(S), opcionalmente con prefijo de ruta; sin usuario, query ni fragmento. Obligatoria al habilitar. |
| `ANALYSIS_TOKEN` | Vacío | Token interno, cuando el despliegue lo requiere. |
| `ANALYSIS_WORKER_ENABLED` | `true` | Permite detener el scheduler conservando trabajos/consultas. |
| `ANALYSIS_POLL_INTERVAL` | `5s` | Espera entre ciclos y consultas normales. Se atiende un trabajo por ciclo. |
| `ANALYSIS_REQUEST_TIMEOUT` | `10s` | Máximo de una llamada HTTP, incluido el cuerpo. |
| `ANALYSIS_LEASE_DURATION` | `30s` | Reserva, mayor que dos timeouts HTTP. |
| `ANALYSIS_MAX_DURATION` | `6h` | Plazo máximo desde la solicitud para seguir el trabajo. |
| `ANALYSIS_MAX_FAILURES` | `5` | Fallos de comunicación consecutivos antes de ERROR. |

Todos los intervalos deben ser positivos. La espera por errores crece desde el intervalo de polling hasta un máximo de cinco minutos; las respuestas válidas restablecen el contador. El plazo general se comprueba al recuperar cada trabajo.

Al agotar recuperación/plazo, Java deja de seguir ese trabajo y registra ERROR; eso no constituye una cancelación remota. Antes de un futuro reproceso debe conciliarse con Python cualquier ejecución cuyo resultado haya quedado incierto. Esta entrega no incluye cancelación ni el nuevo intento de HU16.

Activar requiere configurar la URL, compartir el original y reiniciar el backend para cargar variables. No hace falta activar YouTube. Con integración deshabilitada, los CRUD, cargas HU01 y consultas de análisis previos siguen disponibles.

## Pruebas

```powershell
.\mvnw.cmd verify
```

La suite normal usa H2, MockMvc, un gateway controlado y un servidor HTTP de loopback. Cubre aceptación 202, consulta/listado, errores, dos solicitudes concurrentes, dos workers, reservas vencidas, avance real, snapshots inválidos/obsoletos, timeouts de cuerpo, reenvío idempotente, errores públicos y finalización.

La prueba PostgreSQL se habilita solo cuando `ANALYSIS_PG_TEST_URL` apunta a una **base desechable local** llamada `analysis_hu0203_test`, con usuario `analysis_test`. Recrea sus tablas. No usar la base de desarrollo del proyecto. Con ese servidor preparado:

```powershell
$env:ANALYSIS_PG_TEST_URL = 'jdbc:postgresql://127.0.0.1:55437/analysis_hu0203_test'
.\mvnw.cmd verify
```

Comprueba las migraciones 001/002, el índice único parcial, validación del esquema, concurrencia, carga de un MP4 y recuperación tras cierres/reaperturas reales del contexto de aplicación con el mismo PostgreSQL y originales. El servidor HTTP de esa prueba representa el futuro Python y conserva una sola identidad remota aunque falle la primera confirmación.

En entornos Windows cuyo `TEMP` sea muy largo, Java puede fallar al crear sockets locales con `Unable to establish loopback connection`. Para esa ejecución puede fijarse un directorio corto existente mediante `-DargLine="-Djdk.net.unixdomain.tmpdir=C:/ruta/corta"`. No se requiere modificar el código del analizador para resolver ese límite local.

Las pruebas del uploader Python de YouTube mantienen el requisito POSIX descrito en HU01. No forman parte del contrato del analizador Python.

### Verificación realizada el 2026-10-21

- Maven 3.9.16 ya instalado, modo offline, JDK 25.0.2 y compilación objetivo Java 21: `verify` terminó con BUILD SUCCESS.
- 87 pruebas correctas, sin errores ni omitidas: las 47 de regresión y 40 nuevas (15 de API/worker, 19 de validación de snapshots, 5 del cliente HTTP y 1 escenario integrado de PostgreSQL con varios reinicios).
- PostgreSQL 17 temporal, distinto de la base del proyecto: migraciones 001/002 aplicadas, arranques posteriores con `ddl-auto=validate`, índice parcial y restricciones comprobados directamente, y recuperación de una solicitud cuya primera confirmación remota falló. El proveedor controlado mantuvo una sola identidad remota.
- En este entorno se configuró un directorio corto de sockets para superar el límite del TEMP impuesto al proceso Windows. No se cambiaron las versiones del wrapper ni del proyecto.
- El servicio Python real todavía no está disponible. Quedan pendientes su implementación del contrato, el montaje compartido y la validación con ese servicio. No se habilitó la integración ni se modificaron los issues del tablero.
