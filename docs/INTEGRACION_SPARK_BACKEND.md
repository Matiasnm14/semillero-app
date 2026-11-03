# Integración de Spring Boot con Spark

Implementación del contrato recibido en `INFORME_INTEGRACION_SPARK.md`, del 2 de noviembre de 2026. Rama de trabajo: `video-data-analysis`.

## Flujo implementado

1. La carga validada queda guardada junto con su trabajo pendiente. El worker persiste un UUID canónico antes del envío.
2. Envía el video desde disco a `POST /analyses`, con `analysisId`, `readJerseys` y Bearer token. Solo un `409 GPU_BUSY` autoriza repetir esa subida, con espera y plazo máximo. Un `ANALYSIS_ID_EXISTS` recupera el trabajo existente.
3. Si se pierde la respuesta del POST, consulta el UUID. Un `404` temprano puede significar que la carga original aún sigue llegando: espera sin repetir el POST. Si el proceso se reinicia sin JSON guardado, recupera por GET y nunca vuelve a subir automáticamente un envío incierto.
4. Consulta el estado hasta finalizar. Al recibir el resultado, valida su identidad y estructura y **confirma el JSON en PostgreSQL antes de solicitar el mapa**. Si falla el commit, reintenta el guardado; no repite la descarga ni pide el segundo recurso mientras falta ese commit.
5. Solicita el PNG y lo valida por tipo, tamaño, dimensiones y decodificación. Lo escribe con sincronización a disco y movimiento atómico en el volumen persistente; después confirma su estado en SQL. Un `422 HEATMAP_UNAVAILABLE` queda registrado como ausencia de mapa, sin error del análisis.
6. Spark elimina el trabajo tras enviar ambas respuestas. Los trabajos nuevos no programan `DELETE`. Los resultados locales siguen disponibles aunque Spark responda `404`.

Se preservan los segmentos, dorsales candidatos y valores nulos exactamente como llegan. No se asignan automáticamente a jugadores ni se convierten píxeles a metros. `resultadosDisponibles` continúa en `false` hasta validar y publicar las métricas en HU12.

## Recuperación y contrato para el frontend

El DTO de estado conserva sus campos y añade `estadoHeatmap`:

| Estado | Significado |
| --- | --- |
| `PENDIENTE` | Aún no se confirmó el resultado de la descarga del mapa. |
| `DISPONIBLE` | PNG guardado y entrega confirmada en SQL. |
| `NO_DISPONIBLE` | Spark respondió `422 HEATMAP_UNAVAILABLE`. |
| `ERROR_DESCARGA` | No se pudo conservar la entrega; el JSON recibido se conserva. |
| `DESCONOCIDO` | Trabajo histórico anterior a los checkpoints. |

Un análisis con JSON válido y mapa fallido termina como `COMPLETADO`, con `respuestaRecibida=true`, `heatmapDisponible=false`, `estadoHeatmap=ERROR_DESCARGA` y un código/mensaje controlados. `COMPLETADO` indica que terminó el flujo de recepción; no confirma la publicación de métricas deportivas. Si faltan resultados JSON, una falla sigue terminando como `ERROR`.

Tras un reinicio, si hay JSON guardado se omiten las consultas remotas de estado y resultados. Un PNG local o un `NO_DISPONIBLE` ya confirmado permiten terminar sin contactar al trabajo borrado. Si falta el mapa, se solicita solo ese recurso; un `404 ANALYSIS_NOT_FOUND` registra `MAPA_CALOR_NO_RECUPERABLE` y conserva el JSON. Las descargas HTTP de Spring mantienen los permisos sobre el partido. Ver [rutas y configuración](HU02_HU03_ANALISIS.md).

**Límite del protocolo remoto:** hay una ventana entre el envío de la segunda respuesta y su persistencia local. Si Spring cae durante esa ventana, el mapa puede haberse borrado en Spark antes de quedar guardado. Los checkpoints protegen el JSON y permiten reconocer el problema; una garantía completa para ambos archivos requiere cambiar Spark para eliminar tras un ACK explícito de persistencia. Las marcas antiguas de limpieza manual se mantienen únicamente para trabajos heredados.

## Migración y activación en el VPS

La nueva migración es [V015](../database/migrations/V015__spark_result_checkpoints.sql): agrega `estado_heatmap` y adapta `chk_analysis_response` para permitir JSON en un trabajo remoto `PROCESANDO`. Conserva JSON, roles y documentos existentes. Las migraciones V000–V013 no se editan. V014 perteneció a una propuesta retirada de roles y no se reutiliza; si una base llegó a aplicarla, su historial requiere revisión con el administrador, sin editar checksums ni borrar registros.

El migrador central debe aplicar V013 y V015 antes de iniciar una imagen que solo habilite Spark; para la imagen integrada de esta rama debe aplicar también V016–V018. El runtime mantiene **Flyway desactivado y Hibernate `validate`**. `init.sql` incluye el esquema actual únicamente para bases vacías.

Compose y el perfil `vps` ya permiten activar Spark mediante el `.env` privado del despliegue:

```dotenv
ANALYSIS_ENABLED=true
ANALYSIS_WORKER_ENABLED=true
ANALYSIS_BASE_URL=https://spark-0aa4.tail763771.ts.net
ANALYSIS_TOKEN=<credencial entregada por el responsable de Spark>
ANALYSIS_READ_JERSEYS=true
ANALYSIS_REQUEST_TIMEOUT=2h
ANALYSIS_MAX_DURATION=6h
ANALYSIS_MAX_RESPONSE_BYTES=16777216
```

Configurar el token sin pegarlo en chats ni incluirlo en Git. Ambos interruptores permanecen en `false` por defecto. El límite es un trabajo por instancia; Spark también rechaza concurrencia durante la subida. Conservar el volumen `backend_data`, incluyendo los originales y `/var/lib/semillero/analysis`; respaldar PNG y PostgreSQL juntos. La carga pública actual sigue limitada a 2 GiB. El límite mayor de Spark no habilita automáticamente videos grandes en Spring, Cloudflare o el proxy.

La imagen preparada incluye el runtime Python y permite habilitar YouTube con su worker; OAuth debe instalarse en el volumen privado. Ver [entrega conjunta y activación](ENTREGA_SPARK_YOUTUBE_VPS.md). El original local se conserva.

## Validación

Las pruebas automatizadas usan una Spark HTTP controlada y PostgreSQL 17 desechable. Cubren limpieza automática sin DELETE, PNG válido e inválido, 422, recuperación de checkpoints, pérdida de respuesta de subida, UUID canónico, permisos y rechazo de respuestas incompatibles. La prueba PostgreSQL provoca un error real de COMMIT mediante un trigger diferido y comprueba que el JSON se confirma antes de solicitar el mapa, con una sola descarga del JSON.

Para repetir la suite completa, preparar las tres bases locales desechables según [BASE_DATOS_DOCKER.md](BASE_DATOS_DOCKER.md) y ejecutar:

```bash
MIGRATIONS_PG_TEST_URL=jdbc:postgresql://127.0.0.1:55437/migrations_test \
ANALYSIS_PG_TEST_URL=jdbc:postgresql://127.0.0.1:55437/analysis_hu0203_test \
METRICS_PG_TEST_URL=jdbc:postgresql://127.0.0.1:55437/metrics_hu12_test \
bash ./mvnw --batch-mode --no-transfer-progress clean verify
```

Se comprobó el servicio real el 2026-11-02: `/health` respondió `{"status":"ok","gpuBusy":false}`. Posteriormente se validaron la autenticación y el flujo completo con una animación y un video de fútbol de un minuto, descritos abajo. También se verificó por separado una subida real a YouTube con un clip corto. El flujo conjunto mediante los workers de Spring ya se verificó, como se detalla al final. La evaluación de exactitud deportiva y el despliegue al VPS quedan pendientes.

La evidencia local de esta adaptación está en `.local/vps/integracion-spark-20261102/`, fuera de Git.

Resultado final del 2026-11-02: **519 pruebas aprobadas, 0 fallos, 0 errores y 0 omitidas; BUILD SUCCESS**. Se ejecutaron las tres suites PostgreSQL, incluidos 18 casos de migraciones y una recuperación tras reiniciar Spring entre el commit del JSON y la descarga del mapa. Se verificaron las 15 migraciones empaquetadas en el JAR, `init.sql` sobre una base vacía y Compose con Spark activado/desactivado mediante valores sintéticos. El contenedor de pruebas se retiró al finalizar. No se actualizó el VPS ni se publicó un video real.

## Prueba autenticada con la Spark real

El 2026-11-02 a las 20:17 (Bolivia), la prueba explícita `SparkLiveIT` terminó con **1 prueba aprobada, sin fallos, errores ni omisiones**, usando PostgreSQL 17 local y el gateway HTTP real. Es una ejecución adicional a la suite de 519 pruebas anterior.

Se usó un fragmento de 8 segundos de `cronoshift.mp4`: animación de 1920 × 1080, 2.729.758 bytes, SHA-256 `35423cec37f4ea89272dcd15e5bc987d6918f5e0be61ae399f75a5d5d487b2f2`. Sirve para validar el transporte y la recepción; no acredita detección de futbolistas ni calidad de métricas deportivas.

| Comprobación | Resultado |
| --- | --- |
| Autenticación Spark | Consulta con token aceptada; UUID inexistente devuelve `404 ANALYSIS_NOT_FOUND` |
| Carga en Spring | JWT real de una identidad local, respuesta `202` y trabajo persistido |
| Procesamiento Spark | `COMPLETED`, 40 fotogramas, 2,68 s reportados por el motor |
| Resultado local | `COMPLETADO`, JSON confirmado en PostgreSQL y servido por la API |
| Detecciones / segmentos | 0 / 0, coherente con un clip que no es de fútbol |
| Mapa | `422 HEATMAP_UNAVAILABLE` registrado como `NO_DISPONIBLE`, sin error del análisis |
| Limpieza automática | El UUID remoto pasó a responder `404` tras las entregas; no se programó DELETE |
| Consulta posterior | JSON local disponible después del borrado remoto; sin JWT devuelve `401` |

UUID remoto: `a4ac585d-74d1-4ffa-b24b-81779cfb7179`. JSON SHA-256: `927ea1848ff73970fc6973867d064c1117f42a46ff56fbda4610ac2803ffaa42`. La evidencia completa permanece en `.local/analysis/prueba-real-20261102/flujo-spring/`, excluida de Git. El token no forma parte del informe.

### Repetir con otro clip

La clase termina en `IT`, por lo que no se ejecuta en la suite normal. Además requiere `SPARK_LIVE_TEST=true`. El lanzador [test_spark_live.py](../scripts/test_spark_live.py) carga el token de `.local/analysis/.env` sin evaluarlo como shell ni pasarlo por argumentos; conserva logs y resultados en `.local/analysis/`. Solo admite el endpoint Spark acordado y una base PostgreSQL local llamada `spark_live_test`.

Preparar un PostgreSQL **desechable** en el puerto local 55438, con base y usuario `spark_live_test` y acceso local sin contraseña. No usar la base compartida del VPS. Flyway crea el esquema y aplica V015 en esa base de pruebas. Luego ejecutar:

```bash
python3 scripts/test_spark_live.py \
  --video /ruta/a/clip-de-futbol.mp4 \
  --db-url jdbc:postgresql://127.0.0.1:55438/spark_live_test
```

Usar un video autorizado de hasta 2 GiB, el límite multipart actual del backend. El harness lo envía por HTTP desde disco, sin cargarlo completo en memoria; también calcula su SHA-256 en streaming. La espera por petición en esta prueba es de 10 minutos para admitir subidas grandes por Funnel. El test envía un trabajo real a la GPU y espera hasta 20 minutos. Sin `--with-youtube` no publica en YouTube; con esa opción prueba ambos workers y una publicación real no listada. No invoca el VPS. Una nueva ejecución usa otra carpeta de evidencia para no sobrescribir el resultado anterior.

## Prueba completa con fútbol: IMG_0107

El 2026-11-02 entre las 20:24:02 y 20:28:55 (Bolivia) se procesó **el archivo completo `IMG_0107_1min_4k_sdr.mp4`**, sin recortarlo ni recomprimirlo. La prueba optativa terminó con **1 prueba aprobada, 0 fallos, 0 errores y 0 omitidas**.

| Comprobación | Resultado |
| --- | --- |
| Entrada | 60 s, 3840 × 2160, H.264 SDR, 15 fps, 401.535.506 bytes |
| Transporte | Archivo → HTTP multipart real de Spring → gateway HTTP → Spark mediante Funnel, desde disco |
| Tiempo total del flujo medido | Aproximadamente 293 s, incluida carga, procesamiento, descarga y comprobaciones |
| Procesamiento reportado por Spark | 48,83 s, 300 fotogramas muestreados |
| Salidas del motor | 5.209 detecciones acumuladas, 3.917 observaciones de seguimiento, 6 segmentos exportados |
| Estado en PostgreSQL | `COMPLETADO`, `DISPONIBLE`, JSON guardado, sin código de error ni limpieza manual pendiente |
| Mapa | PNG válido, 1280 × 800, 207.266 bytes, descargado desde la API de Spring |
| Limpieza remota | `404` después de entregar ambas salidas; JSON local disponible posteriormente |
| Permisos | Consulta de resultados sin JWT rechazada con `401` |

El archivo local tiene SHA-256 `102f5e73130652d52b1fb360d213f7cbcf3a575c45ebee307467e94f8fcf8c41`. UUID remoto: `9c6f5171-a346-4eda-8d08-53889d8e8ed9`. El JSON guardado tiene SHA-256 `06503e548ef56bcb11c417f23da51a6faf4645e140118805d17f64dda6065854`; el PNG tiene SHA-256 `f9c5034b9e2dd0194aea5a50924b93048653ce2abd4ae6809b1a2e3e9a1abac8`.

Los seis segmentos incluyen candidatos de dorsal 7, 77 y 15, todos `consensus_unverified`; varios segmentos repiten dorsal. Sus métricas físicas tienen estado `estimated`. Esto valida el contrato de integración, **no seis identidades únicas ni la exactitud de los dorsales o velocidades**. No se publicaron métricas vinculadas automáticamente a jugadores de Spring.

Se verificó visualmente el PNG recibido. El mapa representa ocupación observada agregada y declara calibración estimada; no es un mapa por jugador identificado. La evidencia privada está en `.local/analysis/prueba-futbol-20261102/`: metadatos y hashes en `entrada.json`; JSON, PNG, informe y log en `flujo-completo/`. El video de entrada se agregó al `.gitignore` y no se incorpora a Git.

El contenedor PostgreSQL de esta prueba es desechable y se retira al finalizar; se conservan el JSON y el PNG locales para revisión. No se modificó el VPS, no se publicó en YouTube y no se probó un partido de varios GB.

## Clip corto compartido con la prueba de YouTube

El 2026-11-02 se ejecutó una nueva prueba aprobada con un recorte de los segundos 10–18 de IMG_0107: 8 s, 1920 × 1080, 3.947.758 bytes. La Spark reportó 5,93 s de procesamiento, 40 fotogramas, 789 detecciones acumuladas y 1 segmento exportado. Spring terminó en `COMPLETADO` con `DISPONIBLE`, conservó JSON y PNG y comprobó el `404` remoto tras las entregas. UUID: `9732347f-de11-464a-9cac-d9c59f7392ff`.

Se subió exactamente ese mismo archivo a YouTube usando la utilidad reanudable, con título `2026-11-02` y visibilidad `unlisted`; Google confirmó `AVAILABLE`. Ver [la verificación de YouTube](YOUTUBE_SETUP.md). Se hicieron pruebas separadas de ambos servicios, sin ejecutar el worker de YouTube en Spring ni desplegar al VPS. El clip, informe Spark y resumen cruzado están en `.local/analysis/prueba-youtube-spark-20261102/`, fuera de Git. El PostgreSQL temporal fue retirado al terminar.


## Flujo automático y runtime preparados

La ejecución con `--with-youtube` del 2026-11-02 completó el clip de 8 segundos usando únicamente los workers programados: `ESPERANDO_ANALISIS` al aceptar la carga, análisis `COMPLETADO`, mapa `DISPONIBLE` y publicación `DISPONIBLE`. La Spark reportó 6,02 s de procesamiento y el trabajo remoto terminó en 404 después de la entrega. El recibo confirmó fecha del partido `2026-11-02`, canal correcto y visibilidad `unlisted`. Evidencia privada: `.local/analysis/flujo-automatico-20261102/`.

La entrega pasó **521 pruebas Java y 15 pruebas Python**, sin fallos, errores u omisiones. La imagen se construyó y probó con Compose local, UID 10001, raíz de solo lectura, red host, límite de 1 GiB y volumen persistente. Recuperó la misma publicación desde el recibo sin crear otra, sirvió resultados protegidos por JWT y conservó JSON, PNG y OAuth después de reiniciar. Flyway permaneció desactivado y Hibernate en `validate` durante la prueba del runtime. El contexto de Docker no incluyó credenciales ni videos.

Ver [ENTREGA_SPARK_YOUTUBE_VPS.md](ENTREGA_SPARK_YOUTUBE_VPS.md) para construir la versión, instalar OAuth y activar la configuración. La evidencia de regresiones, imagen y recuperación está en `.local/vps/entrega-completa-20261102/`, fuera de Git. El despliegue al VPS y la confirmación allí de V013/V015 siguen pendientes.
