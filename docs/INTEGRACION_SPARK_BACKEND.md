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

El migrador central debe aplicar V013 y V015 antes de iniciar la imagen nueva. El runtime mantiene **Flyway desactivado y Hibernate `validate`**. `init.sql` incluye el esquema actual únicamente para bases vacías.

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

YouTube permanece deshabilitado en la imagen del VPS: requiere su runtime Python y OAuth. Esta adaptación no borra el original local ni publica videos.

## Validación

Las pruebas automatizadas usan una Spark HTTP controlada y PostgreSQL 17 desechable. Cubren limpieza automática sin DELETE, PNG válido e inválido, 422, recuperación de checkpoints, pérdida de respuesta de subida, UUID canónico, permisos y rechazo de respuestas incompatibles. La prueba PostgreSQL provoca un error real de COMMIT mediante un trigger diferido y comprueba que el JSON se confirma antes de solicitar el mapa, con una sola descarga del JSON.

Para repetir la suite completa, preparar las tres bases locales desechables según [BASE_DATOS_DOCKER.md](BASE_DATOS_DOCKER.md) y ejecutar:

```bash
MIGRATIONS_PG_TEST_URL=jdbc:postgresql://127.0.0.1:55437/migrations_test \
ANALYSIS_PG_TEST_URL=jdbc:postgresql://127.0.0.1:55437/analysis_hu0203_test \
METRICS_PG_TEST_URL=jdbc:postgresql://127.0.0.1:55437/metrics_hu12_test \
bash ./mvnw --batch-mode --no-transfer-progress clean verify
```

Se comprobó el servicio real el 2026-11-02: `/health` respondió `{"status":"ok","gpuBusy":false}`. Eso confirma acceso HTTPS al proceso, no inferencia, calibración ni autenticación. **La prueba de procesamiento real está pendiente de configurar el token y proporcionar un video de fútbol autorizado.** La prueba de YouTube y el despliegue al VPS también quedan pendientes.

La evidencia local de esta adaptación está en `.local/vps/integracion-spark-20261102/`, fuera de Git.

Resultado final del 2026-11-02: **519 pruebas aprobadas, 0 fallos, 0 errores y 0 omitidas; BUILD SUCCESS**. Se ejecutaron las tres suites PostgreSQL, incluidos 18 casos de migraciones y una recuperación tras reiniciar Spring entre el commit del JSON y la descarga del mapa. Se verificaron las 15 migraciones empaquetadas en el JAR, `init.sql` sobre una base vacía y Compose con Spark activado/desactivado mediante valores sintéticos. El contenedor de pruebas se retiró al finalizar. No se actualizó el VPS ni se publicó un video real.
