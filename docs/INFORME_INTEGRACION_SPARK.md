# Informe técnico y guía de integración: análisis de fútbol en DGX Spark

**Destinatarios:** responsable de integración Spring Boot y equipo del informe.

**Fecha de referencia:** 2 de noviembre de 2026.

**Repositorio:** https://github.com/s-villanueva/video-data-analysis, rama `dev`.

**Servicio:** `https://spark-0aa4.tail763771.ts.net` (sujeto al estado operativo de Funnel).

## Resumen para integración

Spring Boot envía un archivo de video mediante `multipart/form-data`, recibe un identificador, consulta el estado y, una vez completado, descarga el JSON y el mapa de calor. La Spark elimina automáticamente el trabajo después de enviar **ambas** respuestas. Si el mapa no se puede generar, el error `422 HEATMAP_UNAVAILABLE` cuenta como su respuesta. No hace falta llamar a `DELETE` en el flujo normal.

El microservicio acepta un solo trabajo a la vez, incluida la subida. Spring Boot debe gestionar la cola y los reintentos; debe descargar y guardar el JSON antes de solicitar el mapa o, si opta por el orden inverso, guardar el mapa antes de solicitar el JSON. La segunda respuesta puede disparar la eliminación inmediata de los archivos remotos. La entrega HTTP no garantiza que Spring los haya guardado, por lo que una caída entre la recepción y el guardado puede ocasionar pérdida de resultados.

El JSON puede contener segmentos duplicados de una misma persona y dorsales no verificados. El mapa es una imagen PNG y las métricas de distancia física dependen de una calibración estimada. La API no descarga videos desde YouTube: Spring Boot debe suministrar el archivo.

## Flujo recomendado para Spring Boot

1. Crear y persistir un UUID canónico `analysisId`; conservar el archivo o una referencia de origen durante el procesamiento.
2. Enviar el archivo en una petición `POST /analyses` con el token y los campos indicados abajo. La respuesta `202` llega después de finalizar la subida.
3. Mantener el trabajo en cola si la Spark responde `409 GPU_BUSY`, y reintentar con espera y límites configurables. Evitar subir varios videos simultáneamente.
4. Consultar `GET /analyses/{analysisId}` periódicamente hasta obtener `COMPLETED` o `FAILED`. Guardar los errores y aplicar una política de tiempo de espera apropiada al tamaño y duración del video.
5. Si terminó, descargar `GET /analyses/{analysisId}/results` y persistir el JSON de forma duradera **antes** del siguiente paso.
6. Descargar `GET /analyses/{analysisId}/heatmap` y guardar el PNG; si responde `422 HEATMAP_UNAVAILABLE`, guardar ese estado. Al terminar la segunda entrega, la Spark borra su copia del video y las salidas intermedias.
7. Marcar el proceso completo en la aplicación una vez persistidos sus propios resultados. No tratar el `404` posterior a la entrega como pérdida del trabajo si Spring ya guardó ambos resultados.

Usar subida en streaming y tiempos de espera configurables, evitando cargar videos completos en memoria. La subida no es reanudable ni completamente idempotente: si se pierde la conexión, consultar el identificador y coordinar los reintentos; un `404` durante la carga original no demuestra que haya finalizado. La Spark no ofrece callbacks. La política de reintentos y la persistencia de Spring Boot son responsabilidad de su implementación.

## Ejemplo de solicitudes HTTP

El siguiente ejemplo muestra el contrato para pruebas; el responsable debe obtener `SPARK_TOKEN` del gestor de secretos y establecer un UUID y un video propios. El token no se incluye en este documento.

```bash
SPARK_URL='https://spark-0aa4.tail763771.ts.net'
ANALYSIS_ID='<uuid-canonico>'
VIDEO_PATH='<ruta-del-video>'

# SPARK_TOKEN debe estar definido de forma segura en el entorno.
curl --fail-with-body "$SPARK_URL/analyses" \
  -H "Authorization: Bearer $SPARK_TOKEN" \
  -F "analysisId=$ANALYSIS_ID" \
  -F 'readJerseys=true' \
  -F "video=@$VIDEO_PATH"

# Repetir hasta que status sea COMPLETED o FAILED.
curl --fail-with-body "$SPARK_URL/analyses/$ANALYSIS_ID" \
  -H "Authorization: Bearer $SPARK_TOKEN"

# Guardar el JSON de forma duradera antes de pedir el mapa.
curl --fail-with-body "$SPARK_URL/analyses/$ANALYSIS_ID/results" \
  -H "Authorization: Bearer $SPARK_TOKEN" \
  -o results.json

# HTTP 200 entrega PNG; HTTP 422 indica mapa no disponible.
# Esta segunda entrega desencadena la eliminación automática en la Spark.
curl --silent --show-error \
  -w 'HTTP %{http_code}\n' \
  "$SPARK_URL/analyses/$ANALYSIS_ID/heatmap" \
  -H "Authorization: Bearer $SPARK_TOKEN" \
  -o heatmap-response.bin
```

Comprobar el código HTTP y `Content-Type` antes de interpretar o renombrar `heatmap-response.bin`: con `200` es PNG; con `422` es un JSON de error. Los nombres anteriores son ejemplos locales. No se exponen rutas de estado de análisis mientras la carga aún no se ha recibido y validado completamente.

---

## Microservicio de análisis en la Spark

Estado revisado el 2 de noviembre de 2026. Esta documentación describe la implementación existente; algunos campos de duración aparecen actualmente en las respuestas aunque se hubieran excluido de un diseño anterior.

### Arquitectura y responsabilidades

`Spring Boot → HTTPS/Funnel → Spark 127.0.0.1:8080 → GPU y almacenamiento temporal`

Spring Boot administra usuarios, solicitudes, videos, persistencia y reintentos. La Spark recibe el archivo, procesa un análisis a la vez, mantiene su resultado temporalmente y permite descargar JSON y mapa PNG. Tras entregar ambos, la Spark elimina automáticamente el trabajo. El servicio no acepta URL de YouTube ni envía callbacks. El cliente consulta el estado hasta terminar y luego solicita los resultados.

En la Spark el contenedor se llama `football-spark-api` y la imagen activa es `football-analysis:spark-service-cleanup-20261102`. El volumen del trabajo está en `/home/sergio/football-spark-microservice/data`, montado como `/data`; el token está en el archivo de la Spark `/home/sergio/football-spark-microservice/secrets/api_token`, montado en solo lectura. No incluir el token en el código fuente ni en registros. El servicio requiere un único proceso Uvicorn: el bloqueo de concurrencia está en memoria y no coordina varios procesos o réplicas.

### Autenticación y dirección

Todos los endpoints excepto `GET /health` requieren:

```http
Authorization: Bearer <TOKEN>
```

La dirección local es `http://127.0.0.1:8080`. Funnel está activo en `https://spark-0aa4.tail763771.ts.net`. El servicio es accesible desde internet y el token protege los endpoints, pero `/health` queda público. Usar un token largo y aleatorio, entregado a Spring Boot por un gestor de secretos. El servicio no implementa cuotas ni límite de frecuencia de peticiones.

### Contrato de entrada

#### `POST /analyses`

`Content-Type: multipart/form-data`:

| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `analysisId` | texto UUID canónico | Sí | Identificador creado por Spring Boot, p. ej. `a8a0535c-30ed-4ac7-a862-0a486c234d10`. No debe estar en uso. |
| `video` | archivo | Sí | Video legible por FFmpeg. El servicio valida su duración y que tenga una pista de video. No hay lista cerrada de extensiones en el código. |
| `readJerseys` | texto `true` o `false` | No | Activa OCR de dorsales; predeterminado `true`. |

Se rechazan campos desconocidos o repetidos. Límite del archivo: 20 GiB por defecto (`SPARK_API_MAX_VIDEO_BYTES`); el cuerpo HTTP tiene un margen de 1 MiB para multipart. La duración admitida es mayor que 0 y hasta 86.400 s. La solicitud recibe `202` **una vez terminada la carga y validado el archivo**, no inmediatamente al comenzar la subida:

```json
{"analysisId":"a8a0535c-30ed-4ac7-a862-0a486c234d10","status":"QUEUED"}
```

El bloqueo de GPU se reserva antes de recibir el formulario y sigue ocupado durante la carga y el procesamiento. Una solicitud concurrente recibe `409 GPU_BUSY`; Spring debe mantener su propia cola y reintentar con espera. El servicio no ofrece reanudación de una carga interrumpida. El procesamiento del formulario puede utilizar archivos temporales además de la copia del video; prever espacio suficiente para varias copias y resultados.

### Estado y salidas

#### `GET /health`

Sin autenticación. `{"status":"ok","gpuBusy":false}` indica que el proceso HTTP responde y si el bloqueo está ocupado. No comprueba por sí solo CUDA, los pesos, espacio libre ni el procesamiento completo.

#### `GET /analyses/{analysisId}`

```json
{
  "analysisId":"a8a0535c-30ed-4ac7-a862-0a486c234d10",
  "status":"PROCESSING",
  "progress":{"processedSeconds":12.5,"totalSeconds":30.03,"percentage":41.63},
  "error":null
}
```

Estados: `QUEUED`, `PROCESSING`, `COMPLETED`, `FAILED`. `progress` es una estimación basada en los mensajes del proceso y puede actualizarse con saltos. En un error, `error` contiene `code` y `message`. Tras reiniciar el servicio, trabajos persistidos como pendientes o en proceso pasan a `FAILED` con `PROCESSING_INTERRUPTED`. Los trabajos ya finalizados permanecen hasta su eliminación.

#### `GET /analyses/{analysisId}/results`

Disponible con estado `COMPLETED`; devuelve `application/json`. Ejemplo ilustrativo, no resultado real:

```json
{
  "analysisId":"a8a0535c-30ed-4ac7-a862-0a486c234d10",
  "status":"COMPLETED",
  "summary":{
    "durationSeconds":30.03,"processingSeconds":50.31,
    "framesProcessed":150,"detections":2357,"tracks":1748,
    "inputWidth":3840,"inputHeight":2160,"jerseyRecognitionEnabled":true
  },
  "players":[{
    "playerId":"results:shot0:track12:segment0:players",
    "jerseyNumber":7,"jerseyStatus":"consensus_unverified","team":"Blanco",
    "identityScope":"track_segment_within_shot",
    "movement":{
      "distancePixels":492.8,"averageSpeedPixelsPerSecond":28.29,"measuredSeconds":17.42,
      "distanceMeters":0.57,"averageSpeedKmh":3.42,
      "metricObservedSeconds":0.6,"metricStatus":"estimated"
    }
  }]
}
```

| Campo | Significado |
|---|---|
| `summary.durationSeconds` | Duración del video detectada. |
| `summary.processingSeconds` | Tiempo de procesamiento reportado por el motor, no la duración de subida. |
| `summary.framesProcessed` | Cantidad de fotogramas muestreados procesados. |
| `summary.detections` | Detecciones acumuladas entre fotogramas, no cantidad de personas distintas. |
| `summary.tracks` | Observaciones de seguimiento acumuladas, no cantidad de jugadores únicos. |
| `summary.inputWidth`, `inputHeight` | Dimensiones de entrada en píxeles. |
| `summary.jerseyRecognitionEnabled` | Si se ejecutó el reconocimiento de dorsal. |
| `players` | Segmentos exportados; puede estar vacío y no representa necesariamente a todos los futbolistas presentes. |
| `playerId` | Identificador del segmento de seguimiento dentro del análisis. No es una identidad estable entre videos. |
| `jerseyNumber`, `jerseyStatus` | Número candidato y estado de la evidencia; pueden ser `null`. `consensus_unverified` no es confirmación de identidad. |
| `team` | Etiqueta de equipo estimada o `null`. |
| `identityScope` | Alcance de la identidad; normalmente un segmento de track de una toma. |
| `movement.distancePixels`, `averageSpeedPixelsPerSecond`, `measuredSeconds` | Distancia, velocidad y duración observadas en coordenadas de imagen. |
| `movement.distanceMeters`, `averageSpeedKmh`, `metricObservedSeconds` | Distancia, velocidad y tiempo con proyección métrica; pueden ser `null` y son estimaciones. |
| `movement.metricStatus` | Estado de la métrica, por ejemplo `estimated` o `unavailable`. |

#### `GET /analyses/{analysisId}/heatmap`

Devuelve el mapa como `image/png` cuando existen posiciones proyectadas. Si no las hay, responde `422 HEATMAP_UNAVAILABLE`. No es un campo numérico en el JSON. Spring Boot debe descargar y guardar la imagen.

#### Limpieza automática después de la entrega

Spring Boot debe solicitar y guardar tanto `/results` como `/heatmap`. Cuando el servicio termina de enviar ambos, elimina automáticamente la carpeta del trabajo, incluidos video, resultados y archivos intermedios. Si el mapa no está disponible, la entrega del error `422 HEATMAP_UNAVAILABLE` cuenta como respuesta del mapa. El orden de las dos solicitudes no importa. Después, el identificador responde `404` y los resultados ya no pueden descargarse de nuevo.

La limpieza se activa al terminar de enviar las respuestas HTTP, no confirma que Spring Boot las haya persistido. Si una respuesta no se completa o el cliente nunca solicita uno de los dos endpoints, la carpeta se conserva; los trabajos fallidos tampoco se eliminan automáticamente. El estado de las entregas completadas se registra en disco. Un reinicio o fallo durante la limpieza puede requerir revisión y limpieza operativa posterior; no hay una tarea de vencimiento automático.

#### `DELETE /analyses/{analysisId}`

Permite eliminar manualmente la carpeta del trabajo, incluidos video, resultados y registros, con `204` sin cuerpo. Solo admite trabajos `COMPLETED` o `FAILED`; no cancela un trabajo activo. El flujo normal ya se limpia automáticamente tras entregar ambos resultados y Spring Boot no necesita llamar a este endpoint.

### Errores y reintentos

Formato habitual: `{"analysisId":null,"error":{"code":"UNAUTHORIZED","message":"Credencial no válida"}}`. `analysisId` puede ser `null`, incluso en errores durante la creación.

| HTTP | Código | Acción sugerida |
|---|---|---|
| 400 | `INVALID_REQUEST` | Corregir UUID, campos o valores. |
| 401 | `UNAUTHORIZED` | Revisar token; no reintentar indefinidamente. |
| 404 | `ANALYSIS_NOT_FOUND` | Comprobar identificador y que no se haya eliminado. |
| 409 | `GPU_BUSY` | Esperar y reintentar desde la cola de Spring. |
| 409 | `ANALYSIS_ID_EXISTS` | Consultar el trabajo existente antes de decidir si usar otro identificador. |
| 409 | `ANALYSIS_NOT_READY` | Esperar a finalización; también se devuelve al intentar borrar un trabajo activo. |
| 413 | `VIDEO_TOO_LARGE` | Reducir tamaño o cambiar el límite de forma coordinada. |
| 422 | `INVALID_VIDEO` | Revisar archivo y códec; no repetir el mismo archivo sin corregirlo. |
| 422 | `HEATMAP_UNAVAILABLE` | Registrar ausencia del mapa; el JSON puede estar disponible. |
| estado `FAILED` | `PROCESSING_ERROR` o `PROCESSING_INTERRUPTED` | Consultar diagnóstico operativo en la Spark y decidir un nuevo trabajo. |

Si la conexión se interrumpe durante o después de subir, el cliente no puede asumir que se creó el trabajo. Consultar el `analysisId` antes de reenviar: un `404` puede producirse mientras la subida original sigue en curso, pues el estado se crea tras recibir y validar el archivo. La API no ofrece una operación idempotente de carga ni reanudación; coordinar reintentos para evitar envíos simultáneos.

### Preparación de Funnel

Revisión actual: Tailscale 1.104.2 instalado; API enlazada a `127.0.0.1:8080` y publicada mediante Funnel. Existe una configuración TCP en el puerto 3390 de Tailscale para otro servicio: no usar `tailscale funnel reset` ni `tailscale serve reset`, porque podrían alterar configuraciones ajenas. Los siguientes pasos sirven para comprobar o volver a activar Funnel si se deshabilita.

1. En la Spark, ejecutar `bash /home/sergio/football-spark-microservice/spark_funnel_preflight.sh`. Comprueba contenedor, respuesta local, estado de Tailscale y espacio sin publicar ni modificar nada. Si falta el archivo, se encuentra también en `deploy/scripts/spark_funnel_preflight.sh` del proyecto.
2. Comprobar en `tailscale serve status --json` y `tailscale funnel status` que HTTPS 443 no esté ocupado por otra publicación. Comprobar MagicDNS, certificados HTTPS y autorización de Funnel para este dispositivo en la administración de la tailnet. La activación puede requerir un administrador y un flujo de aprobación de Tailscale.
3. Cuando se decida publicar, en la Spark ejecutar:

   ```bash
   sudo tailscale funnel --bg --https=443 http://127.0.0.1:8080
   tailscale funnel status
   ```

4. Desde el cliente autorizado comprobar `GET https://spark-0aa4.tail763771.ts.net/health` y después una solicitud autenticada. Configurar la URL real mostrada por Tailscale en Spring Boot. La propagación DNS puede tardar; probar después un video representativo y finalmente uno completo. Funnel tiene límites de ancho de banda no configurables, de modo que la subida de archivos grandes debe medirse.
5. Para retirar únicamente esta publicación HTTPS del puerto 443, comprobar primero el estado y ejecutar `sudo tailscale funnel --https=443 off`; volver a verificar el estado. No retirar las otras publicaciones.

Requisitos y comportamiento según [documentación de Tailscale Funnel](https://tailscale.com/docs/features/tailscale-funnel) y [referencia del comando](https://tailscale.com/docs/reference/tailscale-cli/funnel). Se comprobó el tráfico HTTPS a través de Funnel con un fragmento de 30 segundos; todavía no se ha validado por Funnel una subida del partido completo.

### Operación y solución de problemas

```bash
docker ps --filter name=football-spark-api
docker logs --tail 100 football-spark-api
curl --fail http://127.0.0.1:8080/health
df -h /home/sergio/football-spark-microservice/data
tailscale funnel status
```

Los registros de un análisis están bajo `/home/sergio/football-spark-microservice/data/jobs/<analysisId>/processing.log`. Evitar borrar trabajos activos directamente. Antes de reiniciar el contenedor, comprobar si `gpuBusy` es `true`: un reinicio interrumpe el trabajo y no reanuda su procesamiento. Ver el informe del modelo para la validación disponible y el fallo de subida del partido completo.

---

## Informe del modelo y del nodo Spark

Fecha de revisión: 2 de noviembre de 2026. Este informe describe el servicio que está ejecutándose en `spark-0aa4`. Los archivos `spark_service.py` y `football_official.json` del repositorio coinciden mediante SHA-256 con los del contenedor.

### Función

Spring Boot envía el video al nodo Spark. El nodo analiza el contenido mediante GPU, calcula resultados por segmentos de seguimiento y, cuando la calibración lo permite, entrega un mapa de calor como imagen. Después de entregar el JSON y el mapa o su respuesta de indisponibilidad, la Spark elimina el trabajo automáticamente. El video original puede estar en YouTube, pero la API actual recibe el archivo: no descarga enlaces de YouTube.

El contenedor activo es `football-spark-api`, imagen `football-analysis:spark-service-cleanup-20261102`, con reinicio `unless-stopped`. El servicio está en `127.0.0.1:8080` de la Spark. Utiliza `football_official.json` y los pesos `upstream_football_yolov5.pt`; su SHA-256 dentro del contenedor es `53fcd08a5e5283d7bba705f07fc04022a6ab73a2ba2a847d3bc8143c9ecad2eb`. Este identificador permite reconocer el archivo; no es una puntuación de calidad.

### Parámetros efectivos del detector

Los parámetros siguientes son ajustes de ejecución del perfil, no los millones de valores internos aprendidos de la red. No se exponen como opciones de la API.

| Parámetro | Valor | Descripción |
|---|---:|---|
| `backend` | `pytorch` | Motor que ejecuta el modelo. El perfil no configura TensorRT. |
| `weights` | `models/upstream_football_yolov5.pt` | Archivo de pesos del detector especializado en fútbol. |
| `device` | `cuda:0` | Primera GPU CUDA visible en el contenedor. |
| `batch_size` | 4 | Hasta cuatro imágenes del video se agrupan para inferencia; el seguimiento conserva el orden. Si falta memoria, el detector divide el lote y reintenta. |
| `imgsz` | 1280 | Tamaño configurado para la entrada del detector; el archivo de origen se mantiene a su resolución nativa para otras etapas. |
| `precision` | `fp32` | Formato numérico de 32 bits; no es precisión estadística del modelo. |
| `threshold_det` | 0,70 | Confianza mínima inclusiva para conservar una detección. Un umbral alto puede descartar detecciones correctas de jugadores pequeños. |
| `certain_conf` | 0,70 | Límite a partir del cual una detección retenida se marca como aceptada. Aceptada no significa verificada contra una etiqueta real. |
| `refine_imgsz` | 0 | Segunda vista de refinamiento desactivada. |
| `tile_size` | 0 | División del fotograma en mosaicos desactivada. |
| región de inferencia | fotograma completo | El servicio sustituye el recorte y polígono específicos de `IMG_0107` para aceptar otros partidos; no hay recorte automático de público. |

El servicio solicita procesar 5 fotogramas por segundo de video, no necesariamente cada fotograma del archivo; el muestreo real depende del FPS original y del paso entero. Analiza la duración detectada del archivo completo. Esta frecuencia es interna y no la envía Spring Boot.

### Seguimiento, dorsales y cancha

| Parámetro | Valor efectivo | Descripción |
|---|---:|---|
| `match_thresh` | 0,9 | Coste máximo de asociación primaria entre detecciones y seguimientos; más bajo exige mayor similitud. |
| `second_match_thresh` | 0,7 | Coste máximo de la segunda asociación. La política de confianza del detector condiciona qué propuestas llegan a ella. |
| `track_high` | 0,70 efectivo | Umbral superior para seguimiento: el valor predeterminado 0,35 se eleva al mínimo de detección 0,70. El umbral de nuevos tracks también resulta 0,70. |
| retención | aproximadamente 2 s | Periodo predeterminado de conservación de un seguimiento sin observación, convertido a fotogramas procesados. |
| protección de identidad | activada | Usa comprobaciones de apariencia y conflictos para reducir asociaciones dudosas; no garantiza identidad permanente. |
| `readJerseys` | `true` por defecto | Activa el lector de dorsales; `false` lo desactiva. Es el único ajuste de análisis que recibe la API. |
| OCR | `corroborated`, política `integrated` | Combina evidencia visual y temporal. Los números publicados son candidatos no verificados, no identidad confirmada. |
| `ocr_interval_seconds` | 0,2 s | Intervalo de lectura configurado en el perfil corroborado, sujeto a disponibilidad y reglas del planificador. |
| `min_confidence` / `min_frames` | 0,2 / 2 | Umbral y cantidad mínima usados por la regla de evidencia del lector corroborado. |
| `anchor_min_confidence` | 0,8 | Confianza mínima para una lectura usada como ancla de corroboración. |
| `min_gap_seconds` | 0,4 s | Separación temporal mínima para determinada evidencia corroborada. |
| `tau_invisible` / `tau_uncertainty` | 0,02 / 0,02 | Umbrales internos de la regla de visibilidad e incertidumbre del dorsal. |
| `prior_strength` / `window_samples` | 1,0 / 40 | Fuerza de la distribución previa y cantidad de muestras de la ventana de evidencia. |
| `ink.confidence` / `min_votes` / `consensus` | 0,95 / 2 / 0,8 | Reglas del camino auxiliar de tinta para admitir evidencia; también exige separación y geometría. |
| calibración de cancha | automática | Usa `models/pitch_keypoints/best.pt` para estimar la proyección al esquema de cancha. Puede fallar o cubrir solo una parte del video. |
| dimensiones de plantilla | 105 × 68 m | Dimensiones asumidas, no medidas de la cancha grabada. Las distancias y velocidades métricas son estimaciones. |
| `no-backtrack` | activo | El servicio conserva etiquetas calculadas en vivo, sin la pasada retrospectiva de ese flujo. |
| modo de cámara | fijo | Valor interno predeterminado; no es un campo del contrato HTTP. |

El perfil OCR incluye además reglas de tamaño, geometría y selección de evidencias; ver `deploy/models/jersey_corroborated.json`. Algunos campos heredados en `IMG_0107.json` no deben interpretarse como parámetros efectivos de detección sin comprobar su uso; en particular, la regla OCR corroborada sustituye los umbrales originales de ese archivo.

### Interpretación y validación

- Una entrada de `players` representa un segmento de seguimiento, no necesariamente una persona única durante todo el partido; una misma persona puede tener varios segmentos.
- Un dorsal con `consensus_unverified` es una hipótesis temporal no verificada. `null` indica que no se publica un valor.
- Las métricas en metros dependen de la proyección y se marcan como estimadas o no disponibles. Las métricas en píxeles dependen del encuadre.
- Un mapa de calor solo se entrega cuando hay posiciones proyectadas suficientes; no se debe inferir que la calibración sea perfecta porque exista la imagen.
- Una revisión exploratoria de 60 detecciones en 20 momentos encontró 57 cajas con un jugador aparente y 3 dudosas. No es una medida formal de precisión ni de recall: faltan cajas de referencia, comparación IoU y revisión independiente. El 96,5 % de una muestra anterior tampoco demuestra la precisión del partido completo.

### Pruebas existentes

El informe `testing/spark_api_validation_20261102/report_v3.json` registra pruebas de autenticación, video inválido, concurrencia, estados y eliminación con clips pequeños. `clip_4k_long_report.json` registra 30,03 s en 3840 × 2160: 150 fotogramas procesados, 50,31 s de procesamiento y respuesta PNG del mapa; no es una prueba de exactitud del análisis.

La subida del video completo de 12.305.680.167 bytes por túnel SSH falló por reinicio de conexión después de enviar 11.299.454.976 bytes. Por tanto **no existe una prueba completa satisfactoria de ese archivo ni una prueba de subida grande mediante Funnel**. Se requiere repetirla en condiciones de red adecuadas antes de afirmar que el flujo completo está validado.

En una prueba posterior se subió por Funnel un fragmento 4K de 30,03 s y 202.947.225 bytes: la carga tomó 167,2 s y el procesamiento reportó 57,65 s. Se descargaron JSON y mapa; al actualizar el servicio entre el procesamiento y la descarga se verificó la limpieza automática. Una segunda prueba de 4,07 s recorrió subida, procesamiento, entrega y limpieza completos con la versión actualizada, sin llamar a `DELETE`. En ambos casos se confirmó que la carpeta del trabajo ya no existía en la Spark.
