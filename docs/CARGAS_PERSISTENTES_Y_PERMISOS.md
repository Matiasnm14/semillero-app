# Permisos y cargas persistentes de video

La carga de un partido ahora responde **202 Accepted** después de recibir el archivo completo, validarlo con FFmpeg y confirmar en SQL el video y su análisis `PENDIENTE`. No espera a Spark ni a YouTube. El original queda guardado en disco y el worker recupera la cola desde la base, incluso después de reiniciar Spring.

## Uso desde el frontend

```bash
curl --fail-with-body -i \
  -H "Authorization: Bearer $JWT" \
  -F 'file=@partido.mp4;type=video/mp4' \
  http://localhost:8080/api/v1/partidos/1/videos
```

La respuesta conserva los metadatos del video y agrega:

```json
{
  "id": 17,
  "partidoId": 1,
  "analysisId": 23,
  "estadoAnalisis": "PENDIENTE",
  "estadoYoutube": "ESPERANDO_ANALISIS",
  "usuarioCargaId": 5
}
```

`Location` apunta a `/api/v1/analisis/23`. Consultar esa URL con el mismo JWT para obtener estado/progreso. También se puede consultar `/api/v1/partidos/1/videos/17`, que incluye el estado actual del análisis. El cliente debe aceptar `202`: ya no recibe `201` ni debe esperar resultados en la respuesta de carga.

No hacer otro POST de análisis después de cargar: el trabajo se crea en la misma transacción. Para recuperar su estado después de una desconexión, consultar el listado de videos/análisis del partido. Repetir el multipart es una nueva carga; aún no hay claves de idempotencia ni sesiones reanudables.

## Permisos

Todas las rutas `/api/v1/partidos/{partidoId}/videos/**` y `/api/v1/analisis/{id}/**` requieren autenticación. La comprobación de pertenencia se ejecuta en el filtro antes de procesar multipart y se repite en los servicios.

| Usuario | Lectura | Carga, análisis manual y reintento de YouTube |
| --- | --- | --- |
| ADMIN / ORGANIZADOR | Todos los partidos | Todos los partidos |
| ENTRENADOR | Partidos donde participa un club en el que trabaja | Esos mismos partidos |
| MESA_CONTROL | Partidos que tiene asignados | Esos mismos partidos |
| Jugador participante | Partidos donde aparece su participación | No |
| CONSULTA sin participación/vínculo autorizado | No | No |
| Sin JWT válido | No (`401`) | No (`401`) |

Los vínculos se consultan en la base; el `clubId` del JWT por sí solo no concede acceso. Un usuario autenticado ajeno al partido recibe `403`, también para JSON y PNG. La búsqueda por `analysisId` comprueba el partido del análisis, y una ruta con un video de otro partido no permite sustituir ese vínculo. Los rechazos se registran en auditoría.

Las escrituras de los CRUD compartidos `/partidos`, `/participaciones-partido` y `/fichados` requieren ADMIN u ORGANIZADOR. Estas relaciones determinan quién puede acceder: permitir modificarlas públicamente haría posible reasignar un partido o una participación y eludir los permisos. Las lecturas compartidas conservan su comportamiento. Las rutas propias de torneos mantienen sus reglas existentes.

## Persistencia y ejecución

1. Validar acceso, nombre y tamaño del archivo.
2. Escribir el original en disco, calcular SHA-256 y comprobar el MP4 con FFmpeg, sin transacción SQL abierta.
3. Confirmar el original y guardar video + análisis pendiente en una transacción corta. Registrar el ID de usuario de la carga; los videos históricos conservan `null`.
4. Responder `202` solo después de que termine el commit. Si falla la transacción, eliminar el archivo de esa carga y no aceptar registros parciales.
5. El worker toma el trabajo persistido y realiza la integración [Spark](HU02_HU03_ANALISIS.md). La concurrencia limita ejecuciones, no impide aceptar otro archivo mientras la GPU está ocupada.
6. Si la publicación en YouTube estaba habilitada al cargar, el video queda en `ESPERANDO_ANALISIS`. El commit del análisis completado lo pasa a `PENDIENTE` de YouTube. Un análisis fallido mantiene el original y no publica; el reintento de YouTube tampoco permite saltarse esta condición.

Un fallo remoto posterior al `202` se comunica mediante el estado del trabajo, sin deshacer una carga ya aceptada. Se mantienen el original y los registros para diagnóstico/reproceso explícito; no se reenvía silenciosamente una inferencia incierta. Los trabajos con identidad Spark se recuperan mediante GET tras reiniciar.

`ANALYSIS_ENABLED=false` detiene el despacho, pero permite aceptar archivos válidos en cola. Cuando se habilita, el worker puede procesarlos. El plazo `ANALYSIS_MAX_DURATION` sigue contando desde la solicitud: no es una cola de retención indefinida. La carga manual de un video antiguo sin análisis conserva sus controles de configuración y duplicados.

Montar `VIDEO_STORAGE_DIR` y `ANALYSIS_ARTIFACTS_DIR` en disco persistente y respaldarlos junto con PostgreSQL. Una caída antes de confirmar la carga puede dejar archivos sin fila, que requieren conciliación operativa; no se promete una transacción distribuida entre disco y SQL.

## Migración

Flyway aplica las migraciones pendientes al iniciar: agrega el usuario de carga y permite `ESPERANDO_ANALISIS`, conservando los datos existentes. `database/init.sql` incluye V000–V013 y V015–V018 para bases vacías. No editar las migraciones anteriores ni ejecutar el inicializador sobre una base con datos.

## Alcance de esta entrega

Se mantiene multipart de archivo completo y el límite predeterminado de **2 GiB**. La petición espera la recepción y validación del MP4; la inferencia y YouTube ocurren después. No se implementan todavía bloques de 64 MiB, carga reanudable de 12 GiB, cuotas, cancelación o limpieza de sesiones abandonadas.

Spark elimina automáticamente su copia temporal después de entregar JSON y PNG, o el 422 de mapa no disponible; Spring confirma el JSON antes de pedir la segunda salida. La eliminación automática del original del VPS tras resultados + confirmación de YouTube corresponde a la siguiente etapa; esta entrega conserva ese archivo. Al eliminar todos los originales, un reproceso requerirá una nueva carga u otra fuente acordada.

La imagen VPS de esta entrega incorpora FFmpeg/FFprobe y guarda temporales multipart/Java, originales y PNG en el volumen de disco. La imagen incluye Python y el uploader. Análisis y YouTube están deshabilitados por defecto y pueden activarse tras instalar los secretos y confirmar las migraciones centrales; ver [entrega automática](ENTREGA_SPARK_YOUTUBE_VPS.md). Ver [imagen y almacenamiento](../VPS-Implementacion/backend-compartido/README.md). No se ha desplegado esta entrega en el VPS.

## Verificación local

El 2026-11-02, `clean verify` completó **390 pruebas sin fallos, errores ni omisiones**, con las tres suites PostgreSQL opcionales habilitadas sobre una instancia local desechable de PostgreSQL 17. Incluye 11 casos de permisos con JWT firmados y vínculos reales, rollback de carga, fallos posteriores de Spark, espera de YouTube, migración V012 y recuperación tras reiniciar antes del primer envío. `database/init.sql` también se ejecutó correctamente hasta V012 sobre otra base vacía. La Spark usada en las pruebas HTTP es simulada.
