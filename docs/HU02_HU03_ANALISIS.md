# HU02 y HU03 — Evaluación asíncrona con Future

Spring Boot crea y administra `AnalysisJob`, su relación con el video/partido y sus estados. El worker envía el video a Python mediante HTTP y recibe un `CompletableFuture<String>` que se completa con el JSON final. Python no tiene que devolver IDs de análisis, partido, torneo o jugador ni implementar una API de consulta de estados.

**Actualización del 2026-10-23:** este contrato sustituye el protocolo anterior de PUT/GET con snapshots remotos. El servicio real y el JSON de métricas siguen en desarrollo; la integración permanece deshabilitada por defecto. El [plan anterior](PLAN_HU02_HU03_ANALISIS.md) se conserva como antecedente.

## Flujo

1. El cliente solicita un análisis a Spring. Spring guarda el trabajo `PENDIENTE` y devuelve `202 Accepted` con su ID.
2. El worker confirma en SQL la intención de enviar ese trabajo y lo pasa a `PROCESANDO`.
3. `HttpClient.sendAsync` transmite el archivo original por multipart y devuelve un Future sin esperar el procesamiento. El scheduler queda libre para mantener las reservas y atender otros trabajos dentro del límite configurado.
4. Python termina la evaluación y responde `200 application/json` con un objeto o arreglo de métricas.
5. Spring conserva el JSON en `analysis_jobs.respuesta_json`, registra `COMPLETADO` y marca `respuestaRecibida=true`. El resultado se asocia al análisis a través de la llamada que lo originó; Python no necesita repetir ese contexto.

Se usa [HttpClient.sendAsync](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html) y [CompletableFuture](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html). No se mantiene una transacción SQL abierta mientras se espera a Python. Las confirmaciones del Future se ejecutan en un executor de hilos virtuales separado del scheduler y del temporizador HTTP.

El frontend sigue consultando **a Spring** para mostrar el estado. Spring ya no hace GET de seguimiento a Python. Mientras la única respuesta de Python sea el resultado final, porcentaje y fotogramas permanecen `null`; no se inventa avance ni se asigna 100 automáticamente.

`PROCESANDO` significa que Spring inició la invocación y está esperando; no acredita el instante exacto en que Python comenzó la inferencia. Las fechas de solicitud, inicio y fin las registra Spring.

## API pública de Spring

| Método | Ruta | Respuesta |
| --- | --- | --- |
| `POST` | `/api/v1/partidos/{partidoId}/videos/{videoId}/analisis` | `202`, `Location` y trabajo persistido; sin cuerpo de solicitud |
| `GET` | `/api/v1/analisis/{id}` | Estado persistido, fechas, errores e indicadores de disponibilidad |
| `GET` | `/api/v1/partidos/{partidoId}/videos/{videoId}/analisis` | Listado paginado, más recientes primero |

```bash
curl --fail-with-body -i -X POST http://localhost:8080/api/v1/partidos/1/videos/7/analisis
curl --fail-with-body http://localhost:8080/api/v1/analisis/101
```

Ejemplo de consulta mientras se espera el Future:

```json
{
  "id": 101,
  "partidoId": 1,
  "videoId": 7,
  "estado": "PROCESANDO",
  "porcentaje": null,
  "fotogramasProcesados": null,
  "fotogramasTotales": null,
  "fechaSolicitud": "2026-10-23T13:00:00Z",
  "fechaInicio": "2026-10-23T13:00:05Z",
  "fechaFin": null,
  "fechaActualizacion": "2026-10-23T13:00:05Z",
  "resultadosDisponibles": false,
  "respuestaRecibida": false,
  "codigoError": null,
  "mensajeError": null
}
```

Al recibir y guardar el JSON final: `estado=COMPLETADO`, `fechaFin` informada y `respuestaRecibida=true`. **`resultadosDisponibles` sigue siendo `false`** hasta que HU12 valide, relacione y publique métricas definitivas. Estos indicadores distinguen recepción de publicación. La respuesta cruda se conserva internamente; el GET público no devuelve ese JSON, rutas, tokens ni reservas.

`COMPLETADO` en esta entrega confirma una respuesta HTTP exitosa con JSON estructurado, no la validez semántica de las métricas o su asociación con jugadores. La estructura de negocio aún debe acordarse.

Los trabajos históricos del protocolo anterior conservan sus valores, referencias y progreso. Pueden tener `resultadosDisponibles=true` y `respuestaRecibida=false`, porque no recibieron la nueva columna de respuesta cruda.

## Transporte propuesto para Python

**Ruta configurable:** `POST {ANALYSIS_BASE_URL}{ANALYSIS_EVALUATION_PATH}`. El valor inicial de la ruta es `/internal/v1/evaluaciones`; cambiarlo para coincidir con el endpoint que implemente el equipo.

- Cuerpo multipart con un campo `file`, archivo `video.mp4`, tipo `video/mp4`.
- Se transmiten los bytes del original, sin cargar todo el video en memoria. Python ya no necesita el volumen compartido del protocolo anterior.
- `X-Analysis-Id` es una referencia de correlación informativa del backend; no exige que Python la devuelva ni supone que deduplique solicitudes.
- Con `ANALYSIS_TOKEN` configurado se envía `Authorization: Bearer ...`; Python debe verificarlo.
- Se espera una única respuesta final `200` con `Content-Type: application/json` o un tipo JSON compatible. `202` no es un resultado final y se rechaza en este contrato.
- No se siguen redirecciones ni se reenvía automáticamente una evaluación fallida desde el worker.

El gateway acepta objetos o arreglos JSON, incluidos vacíos, sin imponer todavía nombres de campos, listas de jugadores o unidades. Rechaza respuestas vacías, JSON mal formado, valores escalares, contenido adicional tras el documento, UTF-8 inválido y respuestas que superen el límite configurado. Conserva el texto recibido sin convertir números ni renombrar campos.

Los campos usados en fixtures de pruebas son ejemplos controlados, no el contrato definitivo del productor. Un JSON sintácticamente correcto podría contener errores de negocio; la publicación futura debe validarlo.

El servidor Python y cualquier proxy intermedio deben permitir la duración de esta llamada. `async`/`await` en Python o un Future en Java no cierran anticipadamente esa conexión HTTP: permanece pendiente hasta el resultado, error, cancelación local o timeout.

## Durabilidad, reservas y recuperación

PostgreSQL conserva la cola y los estados; los Futures representan conexiones activas dentro del proceso de Spring.

- El worker limita las evaluaciones concurrentes por instancia. Cada una tiene token y reserva SQL, renovada periódicamente mientras el Future sigue pendiente.
- Las respuestas tardías no pueden completar una reserva vencida o reemplazada.
- Si falla la confirmación SQL de un Future ya completado, el siguiente ciclo intenta guardar la respuesta en memoria, sin llamar otra vez a Python, mientras conserva la reserva válida.
- Los trabajos `PENDIENTE` nunca enviados pueden arrancar después de reiniciar.
- Un Future activo no puede reconstruirse tras perder el proceso. Al cerrar ordenadamente, se registra `ERROR / EVALUACION_INTERRUMPIDA`; tras un cierre abrupto, otro worker registra ese error al vencer la reserva. También se revisan así los trabajos activos del protocolo anterior.
- Timeout o pérdida de comunicación terminan en `ERROR`. El contrato actual de Python no permite recuperar un trabajo remoto ni garantiza deduplicación; por eso el backend no reenvía silenciosamente una evaluación de resultado incierto.
- Cancelar el Future cancela el intercambio HTTP local en la medida que permita el cliente; **no confirma la cancelación de la inferencia en Python**. Conciliarla con el productor antes de un reproceso. El nuevo intento explícito se implementará con HU16.

No se pierde el historial de trabajos ni los JSON ya guardados. Una caída entre recibir un resultado y persistirlo puede dejar una evaluación incierta; recuperar ese resultado tras perder el proceso requeriría apoyo del servicio Python o un transporte durable adicional.

## Configuración

| Variable | Por defecto | Uso |
| --- | --- | --- |
| `ANALYSIS_ENABLED` | `false` | Habilitar creación y despacho cuando Python esté disponible |
| `ANALYSIS_BASE_URL` | Vacío | Base HTTP(S), con prefijo opcional, sin usuario/query/fragmento |
| `ANALYSIS_EVALUATION_PATH` | `/internal/v1/evaluaciones` | Ruta del POST multipart; configurable para el servicio real |
| `ANALYSIS_TOKEN` | Vacío | Autorización interna opcional |
| `ANALYSIS_WORKER_ENABLED` | `true` | Ejecuta despacho, mantenimiento de reservas y confirmaciones SQL pendientes |
| `ANALYSIS_POLL_INTERVAL` | `5s` | Intervalo del worker local; no consulta estados a Python |
| `ANALYSIS_CONNECT_TIMEOUT` | `10s` | Máximo para establecer conexión |
| `ANALYSIS_REQUEST_TIMEOUT` | `2h` | Máximo para recibir el resultado completo, incluido el cuerpo |
| `ANALYSIS_LEASE_DURATION` | `30s` | Reserva renovable, mayor que dos intervalos del worker |
| `ANALYSIS_MAX_DURATION` | `6h` | Plazo total desde la solicitud, incluida la espera en cola |
| `ANALYSIS_MAX_CONCURRENT_JOBS` | `1` | Evaluaciones simultáneas por instancia de Spring |
| `ANALYSIS_MAX_RESPONSE_BYTES` | `1048576` | Máximo del JSON recibido, inicialmente 1 MiB |

Los intervalos deben ser positivos y conexión no debe superar la espera de respuesta. El timeout de evaluación ahora es independiente de la duración de la reserva, porque esta se renueva. Ajustar concurrencia con el responsable de GPU; varias instancias de Spring multiplican ese límite local.

**Para un `.env` anterior:** revisar `ANALYSIS_REQUEST_TIMEOUT`, que antes era `10s`, y reemplazarlo por el plazo acordado de evaluación. Agregar conexión, ruta, concurrencia y límite de respuesta. `ANALYSIS_MAX_FAILURES` ya no se utiliza: no hay reenvío automático de evaluaciones. Reiniciar el backend para cargar los cambios.

## Migración de una base existente

Aplicar una vez [004_analysis_future_response.sql](../database/migrations/004_analysis_future_response.sql) después de 003. Agrega `respuesta_json` y comprueba que una respuesta guardada sea un objeto/arreglo JSON y pertenezca a un trabajo completado. Conserva los datos anteriores. Ejecutarla también si `dev` ya añadió la columna, para incorporar la restricción SQL.

```bash
docker compose exec -T db psql -v ON_ERROR_STOP=1 -U semillero -d semillero -f /docker-entrypoint-initdb.d/migrations/004_analysis_future_response.sql
```

Cambiar usuario/base si difieren en `.env`. Las instalaciones nuevas reciben 000–004 mediante [init.sql](../database/init.sql); no ejecutar el archivo de inicialización sobre una base con datos. Ver [guía de Docker](BASE_DATOS_DOCKER.md).

## Errores y conflictos

El POST público mantiene `400` para IDs inválidos, `404` para recursos inexistentes/ajenos, `409` si ya existe un trabajo y `503` para integración deshabilitada o falta del original. El conflicto incluye `analysisId` y `analysisUrl`. Un trabajo terminal requiere el futuro reproceso explícito de HU16.

El GET público consulta SQL y sigue disponible si Python falla. Los errores de ejecución figuran en el trabajo con mensajes controlados: `ANALIZADOR_NO_DISPONIBLE`, `SOLICITUD_ANALIZADOR_RECHAZADA`, `RESPUESTA_ANALIZADOR_INVALIDA`, `ESPERA_EVALUACION_AGOTADA`, `TIEMPO_ANALISIS_AGOTADO`, `ORIGINAL_NO_DISPONIBLE` o `EVALUACION_INTERRUMPIDA`. No se devuelven cuerpos HTTP remotos ni excepciones originales.

## Pruebas y próximos acuerdos

```bash
./mvnw clean verify
```

La suite normal usa H2, Futures controlados y un servidor HTTP de loopback. Comprueba API 202, espera sin bloquear los CRUD, finalización fuera de orden, concurrencia limitada, renovación de reservas, respuesta tardía, errores, timeout de cuerpo, cancelación y reintento exclusivo de la confirmación SQL.

Las pruebas PostgreSQL son opcionales y requieren bases locales desechables con los nombres y usuarios documentados. Recrean su esquema:

```bash
ANALYSIS_PG_TEST_URL=jdbc:postgresql://127.0.0.1:55437/analysis_hu0203_test \
METRICS_PG_TEST_URL=jdbc:postgresql://127.0.0.1:55437/metrics_hu12_test \
./mvnw clean verify
```

Usuarios: `analysis_test` y `metrics_test`, respectivamente, sin contraseña en ese entorno de prueba aislado. Se comprueba 000–004, validación JPA, conservación de trabajos históricos, persistencia del JSON tras reinicios y ausencia de reenvío de trabajos interrumpidos. El servidor controlado no sustituye la validación con Python real.

**Verificación del 2026-10-23:** `clean verify` completó 106 pruebas, sin fallos ni omisiones, con ambas pruebas PostgreSQL habilitadas sobre bases desechables en PostgreSQL 17. Incluye cancelación del Future, timeout del cuerpo HTTP y migración 004 sobre trabajos históricos.

**Pendientes del equipo:** JSON de métricas, unidades e intervalos; identificación por dorsal distinguiendo los dos equipos; publicación y versiones de HU12. Un dorsal puede repetirse en el mismo partido, por lo que Spring no deduce automáticamente un `Jugador.id` usando únicamente ese número. Se conserva la respuesta hasta acordar el criterio y comprobarlo contra `ParticipacionPartido`.
