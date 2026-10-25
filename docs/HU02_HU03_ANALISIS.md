# HU02 y HU03 — Evaluación asíncrona con Future

Spring Boot crea y administra `AnalysisJob`, su relación con el video/partido y sus estados. El transporte envía el video a Python mediante HTTP y recibe un `CompletableFuture<String>` que se completa con el JSON final. Python no tiene que devolver IDs de análisis, partido, torneo o jugador ni implementar una API de consulta de estados.

**Actualización del 2026-10-23:** este contrato sustituye el protocolo anterior de PUT/GET con snapshots remotos. El servicio real y el JSON de métricas siguen en desarrollo; la integración permanece deshabilitada por defecto. El [plan anterior](PLAN_HU02_HU03_ANALISIS.md) se conserva como antecedente.

## Cargas nuevas: análisis automático antes del 201

El [flujo automático](INTEGRACION_AUTOMATICA_VIDEO.md) es la entrada principal: `POST /api/v1/partidos/{partidoId}/videos` valida y prepara el archivo, lo envía a Python y espera su Future con timeout fuera de SQL. La petición permanece pendiente. Una respuesta JSON válida permite guardar video y análisis `COMPLETADO` en una transacción corta; solo entonces devuelve `201` y habilita la cola opcional de YouTube.

Si la evaluación falla, limpia la preparación y devuelve `502`, `504` por timeout o `503` por integración deshabilitada/falta de capacidad. No quedan un video aceptado ni una publicación pendiente. Un fallo posterior de YouTube conserva la carga y el análisis.

El DTO del video no cambia. Tras el `201`, usar el GET de listado para conocer el ID del análisis; no hacer otro POST de análisis. El `AnalysisConcurrencyLimiter` comparte el cupo por instancia entre estas cargas y el worker manual.

## Videos anteriores: solicitud manual con 202

1. El cliente solicita un análisis a Spring. Spring guarda el trabajo `PENDIENTE` y devuelve `202 Accepted` con su ID.
2. El worker confirma en SQL la intención de enviar ese trabajo y lo pasa a `PROCESANDO`.
3. `HttpClient.sendAsync` transmite el archivo original por multipart y devuelve un Future sin esperar el procesamiento. El scheduler queda libre para mantener las reservas y atender otros trabajos dentro del límite configurado.
4. Python termina la evaluación y responde `200 application/json` con un objeto o arreglo de métricas.
5. Spring conserva el JSON en `analysis_jobs.respuesta_json`, registra `COMPLETADO` y marca `respuestaRecibida=true`. El resultado se asocia al análisis a través de la llamada que lo originó; Python no necesita repetir ese contexto.

Se usa [HttpClient.sendAsync](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html) y [CompletableFuture](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html). No se mantiene una transacción SQL abierta mientras se espera a Python. Las confirmaciones del Future se ejecutan en un executor de hilos virtuales separado del scheduler y del temporizador HTTP.

El frontend consulta **a Spring** para mostrar el estado de un análisis manual. En las cargas nuevas espera el `201` de carga, que ya acredita recepción del JSON. Spring no hace GET de seguimiento a Python. Mientras la única respuesta de Python sea el resultado final, porcentaje y fotogramas permanecen `null`; no se inventa avance ni se asigna 100 automáticamente.

`PROCESANDO` significa que Spring inició la invocación y está esperando; no acredita el instante exacto en que Python comenzó la inferencia. Las fechas de solicitud, inicio y fin las registra Spring.

## API pública de Spring

| Método | Ruta | Respuesta |
| --- | --- | --- |
| `POST` | `/api/v1/partidos/{partidoId}/videos/{videoId}/analisis` | Manual, para videos anteriores sin análisis: `202`, `Location` y trabajo persistido; si ya tiene análisis, `409` |
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
- `X-Analysis-Id` es una referencia textual de correlación informativa: un UUID para cargas aún no persistidas y el ID convertido a texto para el worker manual. No exige que Python la devuelva ni supone que deduplique solicitudes.
- Con `ANALYSIS_TOKEN` configurado se envía `Authorization: Bearer ...`; Python debe verificarlo.
- Se espera una única respuesta final `200` con `Content-Type: application/json` o un tipo JSON compatible. `202` no es un resultado final y se rechaza en este contrato.
- No se siguen redirecciones ni se reenvía automáticamente una evaluación fallida desde el worker.

El gateway acepta objetos o arreglos JSON, incluidos vacíos, sin imponer todavía nombres de campos, listas de jugadores o unidades. Rechaza respuestas vacías, JSON mal formado, valores escalares, contenido adicional tras el documento, UTF-8 inválido y respuestas que superen el límite configurado. Conserva el texto recibido sin convertir números ni renombrar campos.

Los campos usados en fixtures de pruebas son ejemplos controlados, no el contrato definitivo del productor. Un JSON sintácticamente correcto podría contener errores de negocio; la publicación futura debe validarlo.

El servidor Python y cualquier proxy intermedio deben permitir la duración de esta llamada. `async`/`await` en Python o un Future en Java no cierran anticipadamente esa conexión HTTP: permanece pendiente hasta el resultado, error, cancelación local o timeout.

## Durabilidad, reservas y recuperación

En solicitudes manuales, PostgreSQL conserva la cola y los estados; los Futures representan conexiones activas dentro del proceso de Spring. Las cargas automáticas todavía no tienen registro de negocio mientras esperan: conservan el archivo preparado y el contexto solo dentro de su petición. Su caída no crea un `AnalysisJob` huérfano; puede dejar archivos sin registro que requieren conciliación operativa.

- El cupo de evaluaciones por instancia se comparte entre cargas y worker. Cada trabajo manual tiene token y reserva SQL, renovada periódicamente mientras el Future sigue pendiente; las cargas directas no insertan un pendiente que el worker pueda duplicar.
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
| `ANALYSIS_ENABLED` | `false` | Habilitar evaluación automática y manual; con `false` la carga de videos devuelve `503` |
| `ANALYSIS_BASE_URL` | Vacío | Base HTTP(S), con prefijo opcional, sin usuario/query/fragmento |
| `ANALYSIS_EVALUATION_PATH` | `/internal/v1/evaluaciones` | Ruta del POST multipart; configurable para el servicio real |
| `ANALYSIS_TOKEN` | Vacío | Autorización interna opcional |
| `ANALYSIS_WORKER_ENABLED` | `true` | Despacho manual, mantenimiento de reservas y confirmaciones SQL pendientes; no desactiva la evaluación directa de una carga |
| `ANALYSIS_POLL_INTERVAL` | `5s` | Intervalo del worker local; no consulta estados a Python |
| `ANALYSIS_CONNECT_TIMEOUT` | `10s` | Máximo para establecer conexión |
| `ANALYSIS_REQUEST_TIMEOUT` | `2h` | Máximo para recibir el resultado completo, incluido el cuerpo |
| `ANALYSIS_LEASE_DURATION` | `30s` | Reserva renovable, mayor que dos intervalos del worker |
| `ANALYSIS_MAX_DURATION` | `6h` | Plazo total desde la solicitud, incluida la espera en cola |
| `ANALYSIS_MAX_CONCURRENT_JOBS` | `1` | Evaluaciones simultáneas compartidas por cargas y worker, por instancia de Spring |
| `ANALYSIS_MAX_RESPONSE_BYTES` | `1048576` | Máximo del JSON recibido, inicialmente 1 MiB |

Los intervalos deben ser positivos y conexión no debe superar la espera de respuesta. El timeout de evaluación ahora es independiente de la duración de la reserva, porque esta se renueva. Ajustar concurrencia con el responsable de GPU; varias instancias de Spring multiplican ese límite local.

El timeout del analizador no incluye la recepción multipart y la validación con FFmpeg. Cliente, proxy y servidor deben permitir la duración total de la carga. La implementación automática espera de forma síncrona en el hilo servlet, sin mantener una transacción SQL abierta.

**Para un `.env` anterior:** revisar `ANALYSIS_REQUEST_TIMEOUT`, que antes era `10s`, y reemplazarlo por el plazo acordado de evaluación. Agregar conexión, ruta, concurrencia y límite de respuesta. `ANALYSIS_MAX_FAILURES` ya no se utiliza: no hay reenvío automático de evaluaciones. Reiniciar el backend para cargar los cambios.

## Migración automática

Flyway aplica [V004__analysis_future_response.sql](../database/migrations/V004__analysis_future_response.sql) al arrancar Spring. Agrega `respuesta_json` y comprueba que una respuesta guardada sea un objeto/arreglo JSON de un trabajo completado. Reconoce también instalaciones anteriores que ya aplicaron 004 o donde Hibernate creó la columna, conserva sus datos y completa restricciones ausentes. No es necesario ejecutarla a mano.

Las migraciones V000–V004 se empaquetan en el JAR y se registran en `flyway_schema_history`; Hibernate valida después. La integración automática reutiliza estas tablas y columna. Ver [bases existentes y diagnóstico](BASE_DATOS_DOCKER.md).

## Errores y conflictos

El POST manual mantiene `400` para IDs inválidos, `404` para recursos inexistentes/ajenos, `409` si ya existe un trabajo y `503` para integración deshabilitada o falta del original. El conflicto incluye `analysisId` y `analysisUrl`. Un trabajo terminal requiere el futuro reproceso explícito de HU16. Para la carga automática, los fallos de Python se devuelven en el POST de carga como `502` o `504`; no se confirman registros incompletos.

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

**Verificación del 2026-10-23:** la adaptación inicial a Future completó 106 pruebas. Tras incorporar la carga automática, `clean verify` completó **123 pruebas**, sin fallos ni omisiones, con ambas pruebas PostgreSQL habilitadas sobre bases desechables en PostgreSQL 17. Incluye cancelación del Future, timeout del cuerpo HTTP, migración 004 sobre trabajos históricos y persistencia conjunta de video y JSON. Ver [verificación de la carga automática](INTEGRACION_AUTOMATICA_VIDEO.md#10-verificación-realizada).

**Pendientes del equipo:** JSON de métricas, unidades e intervalos; identificación por dorsal distinguiendo los dos equipos; publicación y versiones de HU12. Un dorsal puede repetirse en el mismo partido, por lo que Spring no deduce automáticamente un `Jugador.id` usando únicamente ese número. Se conserva la respuesta hasta acordar el criterio y comprobarlo contra `ParticipacionPartido`.
