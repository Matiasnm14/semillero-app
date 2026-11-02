# HU02 y HU03 — Integración HTTP con la Spark

Contrato vigente: [spark-implementation.md](spark-implementation.md), incorporado el 2026-11-02. Sustituye el transporte anterior que mantenía un solo POST abierto hasta recibir un JSON libre. Los endpoints aún deben implementarse en la Spark: las pruebas de Spring usan un servidor HTTP controlado.

## Flujo entre Spring y la Spark

1. Spring genera una identidad remota y la envía con `POST /analyses`, multipart con `video`, `analysisId` y `readJerseys` (por defecto `true`). El archivo se transmite desde disco. Los IDs de partido, torneo y jugador permanecen en Spring.
2. La Spark contesta `202` con el mismo `analysisId` y `status=QUEUED`. Eso confirma recepción del archivo, todavía no resultados.
3. Spring consulta `GET /analyses/{analysisId}`. `QUEUED` y `PROCESSING` mantienen el Future pendiente. `FAILED` termina con un error controlado; los detalles internos de la Spark no llegan al cliente.
4. Con `COMPLETED`, Spring obtiene `GET /analyses/{analysisId}/results`. Comprueba identidad, estado, `summary` y `players`, y conserva el texto JSON original en SQL.
5. Descarga `GET /analyses/{analysisId}/heatmap` como PNG a almacenamiento local. No acepta URLs de artifacts ni celdas de heatmap dentro del JSON. Valida formato, tamaño y dimensiones antes de guardar el archivo mediante un movimiento atómico.
6. Un `422 / HEATMAP_UNAVAILABLE` permite completar el análisis sin imagen: no se inventa una vista superior cuando falta calibración. Otros errores de descarga impiden confirmar el resultado.
7. Después de confirmar la transacción SQL, el worker solicita `DELETE /analyses/{analysisId}`. Una marca persistida permite reintentar la limpieza si falla la comunicación o se reinicia Spring. Nunca se borra el trabajo remoto desde el gateway al recibir el JSON.

Todas las llamadas llevan `Authorization: Bearer ${ANALYSIS_TOKEN}` cuando se configura la credencial. No se siguen redirecciones. El JSON y la imagen tienen límites separados y no se carga el video completo en memoria.

La implementación usa `CompletableFuture`, peticiones HTTP asíncronas y hilos virtuales. La espera y las consultas remotas ocurren fuera de una transacción SQL. Cancelar el Future detiene la espera local; no cancela la inferencia remota.

## Entrada desde el frontend

`POST /api/v1/partidos/{partidoId}/videos` recibe y valida el MP4, guarda el original y confirma video + análisis `PENDIENTE` en SQL. Devuelve `202 Accepted` con `analysisId` y `Location` de consulta; el worker realiza después el ciclo Spark. Un fallo remoto no elimina una carga aceptada. YouTube espera a que termine el análisis. Ver [permisos y cargas persistentes](CARGAS_PERSISTENTES_Y_PERMISOS.md).
Los videos anteriores sin análisis usan `POST /api/v1/partidos/{partidoId}/videos/{videoId}/analisis`: Spring responde `202` inmediatamente con un trabajo persistido y el worker lo procesa en segundo plano. No llamar este POST después de una carga nueva, que ya tiene análisis.

El worker procesa ambos flujos desde la cola persistida con el mismo límite de concurrencia. Un `409 / GPU_BUSY` permite volver a enviar el mismo identificador después del intervalo de consulta; nunca se repite un POST por un timeout o un error incierto. Un `409 / ANALYSIS_ID_EXISTS` recupera el trabajo existente mediante GET. Los resultados que aún no estén listos (`409 / ANALYSIS_NOT_READY`) vuelven al ciclo de consulta.

## Estados y progreso

Spring conserva `PENDIENTE`, `PROCESANDO`, `COMPLETADO` y `ERROR`. `PROCESANDO` incluye la recepción/cola de la Spark y la espera de resultados; no acredita que la GPU ya haya empezado. El worker persiste `segundosProcesados`, `segundosTotales` y `porcentaje` cuando la Spark los informa; pueden ser `null`. No calcula fotogramas desde segundos ni inventa porcentajes.

Al confirmar el JSON, `respuestaRecibida=true`. `heatmapDisponible` indica si hay PNG guardado. `resultadosDisponibles` sigue en `false` hasta que HU12 valide y publique métricas asociadas a jugadores reales.

Los elementos de `players` son segmentos de seguimiento. `playerId` pertenece a la Spark y no es `Jugador.id`. Un dorsal `consensus_unverified` no verifica identidad ni distingue por sí solo a ambos equipos. Se conservan `team=null`, distancias en píxeles y métricas físicas `null` por falta de calibración. No se convierten píxeles a metros ni se asignan segmentos automáticamente a jugadores.

## API de Spring

| Método | Ruta | Resultado |
| --- | --- | --- |
| POST | `/api/v1/partidos/{partidoId}/videos` | Original validado y análisis pendiente, `202` antes de la inferencia |
| POST | `/api/v1/partidos/{partidoId}/videos/{videoId}/analisis` | Análisis manual de un video anterior, `202` |
| GET | `/api/v1/partidos/{partidoId}/videos/{videoId}/analisis` | Listado paginado de trabajos |
| GET | `/api/v1/analisis/{id}` | Estado persistido, progreso y disponibilidad; requiere acceso al partido |
| GET | `/api/v1/analisis/{id}/resultados` | JSON conservado de la Spark; requiere sesión JWT |
| GET | `/api/v1/analisis/{id}/heatmap` | Descarga PNG; requiere sesión JWT |

Todas las rutas de video/análisis verifican sesión y pertenencia al partido: `401` sin sesión, `403` sin permiso y `404` para un análisis inexistente. Resultados pendientes o sin JSON devuelven `409`; una imagen ausente en un análisis completado devuelve `404`. Los resultados y PNG guardados siguen disponibles después de limpiar la Spark.

```bash
curl --fail-with-body http://localhost:8080/api/v1/analisis/101
curl --fail-with-body -H "Authorization: Bearer $JWT" \
  http://localhost:8080/api/v1/analisis/101/resultados
curl --fail-with-body -H "Authorization: Bearer $JWT" \
  http://localhost:8080/api/v1/analisis/101/heatmap -o heatmap.png
```

## Recuperación y almacenamiento

El worker guarda `remote_analysis_id` antes del envío. Tras reiniciar, recupera un trabajo con esa identidad mediante GET, sin volver a subir el video. Puede hacerlo aunque el original local ya no exista. Si la caída ocurrió antes de que la Spark recibiera el POST, un GET puede responder `404`: no se crea silenciosamente otra inferencia.

Los trabajos históricos sin identidad remota no se reenvían: una espera interrumpida conserva la regla anterior de terminar con `EVALUACION_INTERRUMPIDA`. Los JSON históricos no se reescriben ni se obliga a que tengan el formato nuevo.

Las cargas nuevas también tienen fila SQL y trabajo pendiente antes de responder `202`. Su cola se recupera tras reiniciar igual que la manual. La copia y validación del original ocurren antes de ese commit; una caída anterior puede dejar archivos sin fila que requieren conciliación. Si la carga falla en el commit, no se inicia ninguna llamada a Spark.
Montar `ANALYSIS_ARTIFACTS_DIR` en almacenamiento persistente y respaldarlo junto con PostgreSQL: el PNG no se almacena en SQL. El video original y la publicación opcional en YouTube conservan su flujo actual (no listado, título con fecha del partido). La limpieza remota no elimina la copia local ni el video de YouTube.

## Configuración

La integración sigue deshabilitada por defecto. El perfil VPS continúa con análisis y workers de video deshabilitados hasta disponer del servicio real.

| Variable | Por defecto | Uso |
| --- | --- | --- |
| `ANALYSIS_ENABLED` | `false` | Habilita el despacho; deshabilitada, las cargas válidas se aceptan y permanecen pendientes |
| `ANALYSIS_BASE_URL` | vacío | URL de la Spark accesible desde Spring, con prefijo opcional |
| `ANALYSIS_EVALUATION_PATH` | `/analyses` | Raíz de los cinco endpoints de la Spark |
| `ANALYSIS_TOKEN` | vacío | Configurar el Bearer acordado con el administrador de la Spark |
| `ANALYSIS_READ_JERSEYS` | `true` | Lectura de dorsales |
| `ANALYSIS_POLL_INTERVAL` | `5s` | Consultas remotas, reintento de GPU ocupada y ciclo del worker |
| `ANALYSIS_CONNECT_TIMEOUT` | `10s` | Establecimiento de conexión |
| `ANALYSIS_REQUEST_TIMEOUT` | `2h` | Plazo por intercambio HTTP; la carga pública ya no espera inferencia |
| `ANALYSIS_MAX_DURATION` | `6h` | Plazo total del trabajo manual, incluida la cola local |
| `ANALYSIS_LEASE_DURATION` | `30s` | Reserva del worker; mayor que dos intervalos de consulta |
| `ANALYSIS_MAX_CONCURRENT_JOBS` | `1` | Límite compartido de evaluaciones por instancia de Spring |
| `ANALYSIS_MAX_RESPONSE_BYTES` | `1048576` | Límite por respuesta JSON |
| `ANALYSIS_ARTIFACTS_DIR` | `.local/analysis` | Almacenamiento local de PNG, excluido de Git |
| `ANALYSIS_MAX_HEATMAP_BYTES` | `10485760` | Límite por imagen PNG |
| `ANALYSIS_WORKER_ENABLED` | `true` | Despacho manual, reservas y reintentos de limpieza remota |

Para un `.env` existente, reemplazar `/internal/v1/evaluaciones` por `/analyses`, configurar URL/token y agregar las variables nuevas. Si se deshabilita el worker, tampoco se ejecuta la limpieza periódica; permanece marcada para cuando vuelva a habilitarse.

## Base de datos y pruebas

[V011__spark_analysis_contract.sql](../database/migrations/V011__spark_analysis_contract.sql) agrega identidad remota, progreso en segundos y marca de limpieza, con sus restricciones e índices. Flyway la aplica al iniciar el backend. V012 incorpora la procedencia de la carga y la espera de YouTube; `database/init.sql` incluye ahora 000–012 para bases vacías. No modificar migraciones aplicadas ni ejecutar el inicializador sobre una base existente.

```bash
./mvnw clean verify
```

Las pruebas cubren multipart real, `202`, cola/progreso, GPU ocupada, identidad existente, recuperación sin reenvío, errores, límites, PNG, conservación de `null`, JWT en las nuevas descargas y limpieza posterior al commit. Las pruebas opcionales de PostgreSQL verifican migraciones y recuperación entre reinicios sobre bases locales desechables; consultar [BASE_DATOS_DOCKER.md](BASE_DATOS_DOCKER.md).

Queda pendiente probar la comunicación con la Spark real cuando su equipo implemente y publique los endpoints del contrato. Tampoco se implementa aquí la asociación definitiva de segmentos a jugadores ni la publicación de HU12–HU16.

Verificación local del 2026-11-02: `verify` completó **384 pruebas, sin fallos, errores ni omisiones**, con las tres suites opcionales habilitadas sobre PostgreSQL 17 desechable. Incluye 15 casos de migraciones, recuperación del análisis entre reinicios y persistencia de métricas. `database/init.sql` también se ejecutó correctamente sobre otra base vacía hasta V011, antes de incorporar la carga persistente V012. Esta verificación usa una Spark simulada y no acredita conectividad con la máquina real.
