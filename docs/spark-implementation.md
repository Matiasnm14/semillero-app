El contrato queda con el mapa de calor como imagen descargable, sin celdas ni valores numéricos en el JSON.

El video original permanecerá en YouTube. La Spark lo recibirá de Spring, lo procesará y conservará temporalmente solo lo necesario para entregar el resultado.

1. Recibir el video

Request

POST /analyses
Content-Type: multipart/form-data
Authorization: Bearer <token>

Campo	Tipo	Obligatorio	Descripción

video	Archivo	Sí	Video completo enviado por Spring Boot.
analysisId	Texto	Sí	Identificador generado por Spring Boot.
readJerseys	Booleano	No	Activa la lectura de dorsales. Por defecto: true.


Response

Después de recibir el archivo completo:

HTTP/1.1 202 Accepted
Content-Type: application/json

{
  "analysisId": "a8c78e12-6dd2-4a87-91f4-c38b0a2bb941",
  "status": "QUEUED"
}

Si la GPU está ocupada, la Spark responderá 409 GPU_BUSY y Spring decidirá cuándo reintentar.

2. Consultar estado

Request

GET /analyses/{analysisId}
Authorization: Bearer <token>

Response

{
  "analysisId": "a8c78e12-6dd2-4a87-91f4-c38b0a2bb941",
  "status": "PROCESSING",
  "progress": {
    "processedSeconds": 420.0,
    "totalSeconds": 1851.0,
    "percentage": 22.69
  },
  "error": null
}

Los estados serán QUEUED, PROCESSING, COMPLETED y FAILED. Los datos de progreso podrán ser null mientras no estén disponibles.

3. Recibir resultados

Request

GET /analyses/{analysisId}/results
Authorization: Bearer <token>

Response

{
  "analysisId": "a8c78e12-6dd2-4a87-91f4-c38b0a2bb941",
  "status": "COMPLETED",

  "summary": {
    "durationSeconds": 1850.54,
    "processingSeconds": 3925.7,
    "framesProcessed": 9243,
    "detections": 197350,
    "tracks": 149951,
    "inputWidth": 3840,
    "inputHeight": 2160,
    "jerseyRecognitionEnabled": true
  },

  "players": [
    {
      "playerId": "results:shot0:track10484:segment0:players",
      "jerseyNumber": 7,
      "jerseyStatus": "consensus_unverified",
      "team": null,
      "identityScope": "track_segment_within_shot",

      "movement": {
        "distancePixels": 655.11,
        "averageSpeedPixelsPerSecond": 51.94,
        "measuredSeconds": 12.61,
        "distanceMeters": null,
        "averageSpeedKmh": null,
        "metricStatus": "unavailable_missing_calibration"
      }
    }
  ]
}

El JSON no contiene un objeto heatmap ni rutas de artifacts.

Los jugadores representan segmentos de seguimiento. Un dorsal con estado consensus_unverified es un candidato; todavía no verifica la identidad del jugador. Las métricas en metros y km/h serán null si no hay calibración de cancha utilizable.

4. Recibir la imagen del mapa de calor

Request

GET /analyses/{analysisId}/heatmap
Authorization: Bearer <token>

Response

HTTP/1.1 200 OK
Content-Type: image/png

El cuerpo de la respuesta será la imagen PNG. Spring la descargará y decidirá dónde almacenarla y cómo entregarla al cliente.

El mapa superior requiere calibración de la cancha. Si no existen posiciones proyectadas suficientes, la Spark deberá responder con un error que indique que el mapa no está disponible; no devolverá posiciones de cámara como si fueran posiciones reales de cancha.

5. Tratamiento de archivos

Archivo generado	Tratamiento

summary.json	Se lee para construir la respuesta JSON.
metrics.json	Se lee para construir la lista de jugadores.
heatmap.png	Se devuelve como imagen a Spring.
tracks.csv y detections.csv	Se usan internamente y se desechan después de la entrega.
annotated.mp4	Se desecha; no forma parte de este contrato. Es distinto del video original que estará en YouTube.
Video recibido	Se elimina después de que Spring confirme la recepción de los resultados.


La Spark mantendrá temporalmente el JSON y la imagen hasta que Spring los haya recibido. Después, Spring solicitará la limpieza:

DELETE /analyses/{analysisId}
Authorization: Bearer <token>

Respuesta:

HTTP/1.1 204 No Content

6. Errores

Los errores seguirán un formato común:

{
  "analysisId": "a8c78e12-6dd2-4a87-91f4-c38b0a2bb941",
  "error": {
    "code": "INVALID_VIDEO",
    "message": "El archivo no es un video válido o no se puede leer"
  }
}

HTTP	Código	Motivo

400	INVALID_REQUEST	Falta un campo o tiene un formato incorrecto.
401	UNAUTHORIZED	Falta la credencial o no es válida.
404	ANALYSIS_NOT_FOUND	No existe el análisis solicitado.
409	GPU_BUSY	La Spark está procesando otro trabajo.
409	ANALYSIS_NOT_READY	Se solicitaron resultados antes de terminar.
409	ANALYSIS_ID_EXISTS	El identificador ya está en uso.
413	VIDEO_TOO_LARGE	El video supera el límite configurado.
422	INVALID_VIDEO	El archivo no se puede procesar como video.
422	HEATMAP_UNAVAILABLE	No hay calibración o posiciones suficientes para generar el mapa superior.
500	PROCESSING_ERROR	Falló el procesamiento.


Este es el contrato actualizado para implementar; los endpoints HTTP aún no existen en la Spark.
