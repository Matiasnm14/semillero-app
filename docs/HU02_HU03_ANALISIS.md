# HU02 y HU03 — Integración HTTP con la Spark

Contrato vigente: [INFORME_INTEGRACION_SPARK.md](INFORME_INTEGRACION_SPARK.md), actualizado el 2026-11-02. El servicio está publicado; las pruebas automatizadas usan un servidor HTTP controlado. Ver [adaptación del backend y validación real pendiente](INTEGRACION_SPARK_BACKEND.md). Sustituye la limpieza manual descrita en el contrato anterior.

## Flujo entre Spring y la Spark

1. Spring genera y persiste un UUID canónico como identidad remota y lo envía con `POST /analyses`, multipart con `video`, `analysisId` y `readJerseys` (por defecto `true`). El archivo se transmite desde disco. Los IDs de partido, torneo y jugador permanecen en Spring.
2. La Spark contesta `202` con el mismo `analysisId` y `status=QUEUED`. Eso confirma recepción del archivo, todavía no resultados.
3. Spring consulta `GET /analyses/{analysisId}`. `QUEUED` y `PROCESSING` mantienen el Future pendiente. `FAILED` termina con un error controlado; los detalles internos de la Spark no llegan al cliente.
4. Con `COMPLETED`, Spring obtiene `GET /analyses/{analysisId}/results`. Comprueba identidad, estado, `summary` y `players`, y conserva el texto JSON original en SQL.
5. Solo después de confirmar esa transacción SQL, descarga `GET /analyses/{analysisId}/heatmap` como PNG a almacenamiento local. No acepta URLs de artifacts ni celdas de heatmap dentro del JSON. Valida formato, tamaño y dimensiones antes de guardar el archivo mediante escritura sincronizada y un movimiento atómico; después persiste `estadoHeatmap=DISPONIBLE`.
6. Un `422 / HEATMAP_UNAVAILABLE` queda registrado como `NO_DISPONIBLE`. Una descarga inválida, interrumpida o perdida queda como `ERROR_DESCARGA`, con código y mensaje controlados: el JSON ya guardado se conserva y el análisis se completa con ese aviso.
7. Spark elimina su trabajo tras enviar JSON y PNG, o el error 422 del mapa. Los trabajos nuevos no programan `DELETE`; solo las marcas de limpieza heredadas conservan ese mecanismo para las instalaciones anteriores.

Todas las llamadas llevan `Authorization: Bearer ${ANALYSIS_TOKEN}` cuando se configura la credencial. No se siguen redirecciones. El JSON y la imagen tienen límites separados y no se carga el video completo en memoria.

La implementación usa `CompletableFuture`, peticiones HTTP asíncronas y hilos virtuales. La espera y las consultas remotas ocurren fuera de una transacción SQL. Cancelar el Future detiene la espera local; no cancela la inferencia remota.

## Entrada desde el frontend

`POST /api/v1/partidos/{partidoId}/videos` recibe y valida el MP4, guarda el original y confirma video + análisis `PENDIENTE` en SQL. Devuelve `202 Accepted` con `analysisId` y `Location` de consulta; el worker realiza después el ciclo Spark. Un fallo remoto no elimina una carga aceptada. YouTube espera a que termine el análisis. Ver [permisos y cargas persistentes](CARGAS_PERSISTENTES_Y_PERMISOS.md).
Los videos anteriores sin análisis usan `POST /api/v1/partidos/{partidoId}/videos/{videoId}/analisis`: Spring responde `202` inmediatamente con un trabajo persistido y el worker lo procesa en segundo plano. No llamar este POST después de una carga nueva, que ya tiene análisis.

El worker procesa ambos flujos desde la cola persistida con el mismo límite de concurrencia. Un `409 / GPU_BUSY` permite volver a enviar el mismo identificador después del intervalo de consulta; nunca se repite un POST por un timeout o un error incierto. Un `409 / ANALYSIS_ID_EXISTS` recupera el trabajo existente mediante GET. Los resultados que aún no estén listos (`409 / ANALYSIS_NOT_READY`) vuelven al ciclo de consulta.

## Estados y progreso

Spring conserva `PENDIENTE`, `PROCESANDO`, `COMPLETADO` y `ERROR`. `PROCESANDO` incluye la recepción/cola de la Spark y la espera de resultados; no acredita que la GPU ya haya empezado. El worker persiste `segundosProcesados`, `segundosTotales` y `porcentaje` cuando la Spark los informa; pueden ser `null`. No calcula fotogramas desde segundos ni inventa porcentajes.

Al confirmar el JSON, `respuestaRecibida=true`, incluso si aún se está descargando el mapa. Los endpoints de descarga se habilitan al terminar el flujo (`COMPLETADO`). `heatmapDisponible` indica si hay PNG guardado. El nuevo campo `estadoHeatmap` distingue `PENDIENTE`, `DISPONIBLE`, `NO_DISPONIBLE`, `ERROR_DESCARGA` y `DESCONOCIDO` para históricos. `resultadosDisponibles` sigue en `false` hasta que HU12 valide y publique métricas asociadas a jugadores reales.

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

El worker guarda `remote_analysis_id` antes del envío. Tras reiniciar, recupera un trabajo con esa identidad mediante GET, sin volver a subir el video. Puede hacerlo aunque el original local ya no exista. Si la caída ocurrió antes de que la Spark recibiera el POST, un GET puede responder `404`: no se crea silenciosamente otra inferencia. Cuando se pierde la respuesta del POST, el gateway consulta el UUID y espera si recibe un 404 temprano, hasta el límite configurado; nunca usa ese 404 como autorización para repetir la subida.

Si ya existe JSON guardado, la recuperación omite las consultas de estado y resultados: usa el PNG local o el estado `NO_DISPONIBLE` confirmado; de lo contrario intenta descargar solamente el mapa. Un `404` en esa descarga conserva el JSON y registra `MAPA_CALOR_NO_RECUPERABLE`. La eliminación automática remota deja una ventana inevitable entre la segunda respuesta HTTP y el guardado local: resolverla completamente requiere un ACK explícito en la Spark.

Los trabajos históricos sin identidad remota no se reenvían: una espera interrumpida conserva la regla anterior de terminar con `EVALUACION_INTERRUMPIDA`. Los JSON históricos no se reescriben ni se obliga a que tengan el formato nuevo.

Las cargas nuevas también tienen fila SQL y trabajo pendiente antes de responder `202`. Su cola se recupera tras reiniciar igual que la manual. La copia y validación del original ocurren antes de ese commit; una caída anterior puede dejar archivos sin fila que requieren conciliación. Si la carga falla en el commit, no se inicia ninguna llamada a Spark.
Montar `ANALYSIS_ARTIFACTS_DIR` en almacenamiento persistente y respaldarlo junto con PostgreSQL: el PNG no se almacena en SQL. El video original y la publicación opcional en YouTube conservan su flujo actual (no listado, título con fecha del partido). La limpieza remota no elimina la copia local ni el video de YouTube.

## Configuración

La integración sigue deshabilitada por defecto. El perfil VPS y Compose permiten activar Spark con `ANALYSIS_ENABLED=true` y `ANALYSIS_WORKER_ENABLED=true`, después de aplicar V015 y configurar URL/token. Ambos están desactivados por defecto; YouTube conserva su preparación independiente.

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
| `ANALYSIS_WORKER_ENABLED` | `true` | Despacho de la cola, reservas y limpieza de marcas heredadas |

Para un `.env` existente, reemplazar `/internal/v1/evaluaciones` por `/analyses`, configurar URL/token y agregar las variables nuevas. Si se deshabilita el worker, tampoco se ejecuta la limpieza periódica; permanece marcada para cuando vuelva a habilitarse.

## Base de datos y pruebas

[V011__spark_analysis_contract.sql](../database/migrations/V011__spark_analysis_contract.sql) agrega identidad remota, progreso en segundos y marca de limpieza, con sus restricciones e índices. Flyway la aplica al iniciar el backend. V012 incorpora la procedencia de la carga y la espera de YouTube; V013 agrega documentos. V015 agrega `estado_heatmap` y permite un checkpoint JSON en `PROCESANDO`. `database/init.sql` incluye 000–013 y 015 para bases vacías. V014 no se reutiliza: perteneció a una propuesta retirada de roles. En el VPS las migraciones las ejecuta el migrador central, con Flyway desactivado en el runtime. No modificar migraciones aplicadas ni ejecutar el inicializador sobre una base existente.

```bash
./mvnw clean verify
```

Las pruebas cubren multipart real, `202`, cola/progreso, GPU ocupada, identidad existente, recuperación sin reenvío, errores, límites, PNG, conservación de `null`, JWT en las nuevas descargas y limpieza automática tras ambas entregas. Las pruebas opcionales de PostgreSQL verifican migraciones y recuperación entre reinicios sobre bases locales desechables; consultar [BASE_DATOS_DOCKER.md](BASE_DATOS_DOCKER.md).

La conectividad pública a `/health` se comprobó el 2026-11-02 (`status=ok`, `gpuBusy=false`); el procesamiento autenticado real requiere configurar el token y aportar un video de fútbol autorizado. Tampoco se implementa aquí la asociación definitiva de segmentos a jugadores ni la publicación de HU12–HU16.

Verificación de la adaptación al contrato de limpieza automática del 2026-11-02: `clean verify` completó **519 pruebas, sin fallos, errores ni omisiones**, con las tres suites opcionales habilitadas sobre PostgreSQL 17 desechable. Incluye 18 casos de migraciones, recuperación entre el commit del JSON y la entrega del mapa, un fallo de COMMIT provocado y persistencia de métricas. `database/init.sql` se ejecutó correctamente sobre otra base vacía con 000–013 y 015. Las pruebas funcionales usan una Spark simulada; `/health` real se comprobó por separado, y no acredita procesamiento autenticado.
