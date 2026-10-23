# Plan de implementación de HU02 y HU03

**Documento histórico:** el protocolo de snapshots PUT/GET descrito aquí fue sustituido el 2026-10-23 por una evaluación HTTP con `CompletableFuture` y JSON final. El contrato vigente está en [HU02_HU03_ANALISIS.md](HU02_HU03_ANALISIS.md).

Fecha de revisión: 2026-10-21, zona America/La_Paz.

Este documento registra el diseño previo a la implementación. El backend desarrollado y el contrato para el futuro servicio Python están descritos en [HU02_HU03_ANALISIS.md](HU02_HU03_ANALISIS.md). La rama de trabajo es `Avance-HU02-y-HU03`, derivada de `video-data-analysis` en `536ef66e38929a0465f9ff5dc07efd42da9a3b0f` (`HU01 implementado`). La referencia remota de la rama de origen coincidía con ese commit al revisar el proyecto.

## 1. Fuentes y alcance real

El [tablero de producto](https://github.com/users/s-villanueva/projects/2) enlaza historias de `s-villanueva/video-data-analysis`. El código de este checkout pertenece a `Matiasnm14/semillero-app`: son repositorios diferentes. La API de GitHub informa que el primero no contiene archivos, por lo que sus issues son la fuente funcional, pero no proporcionan una implementación del microservicio.

Se leyeron los cuerpos, las subtareas y las dependencias de:

| Historia | Subtareas reales | Criterios que debe cubrir la entrega |
| --- | --- | --- |
| [HU-02 Iniciar el análisis y continuar trabajando, #6](https://github.com/s-villanueva/video-data-analysis/issues/6) | [SB-004, #20](https://github.com/s-villanueva/video-data-analysis/issues/20), [SB-005, #21](https://github.com/s-villanueva/video-data-analysis/issues/21), [SB-006, #22](https://github.com/s-villanueva/video-data-analysis/issues/22) | Aceptar un video válido de un partido y responder PENDIENTE; mostrar PROCESANDO cuando comience; continuar sin depender de la pantalla; impedir análisis activos duplicados. |
| [HU-03 Consultar el estado del análisis, #8](https://github.com/s-villanueva/video-data-analysis/issues/8) | [SB-007, #23](https://github.com/s-villanueva/video-data-analysis/issues/23), [SB-008, #24](https://github.com/s-villanueva/video-data-analysis/issues/24), [SB-009, #25](https://github.com/s-villanueva/video-data-analysis/issues/25) | Consultar PENDIENTE, PROCESANDO, COMPLETADO o ERROR; informar avance real cuando exista; informar finalización y disponibilidad de resultados; responder recurso no encontrado para un ID inexistente. |

Dependencias registradas: HU02 depende de [HU01, #4](https://github.com/s-villanueva/video-data-analysis/issues/4), que está cerrada; HU03 depende de HU02. Las seis subtareas están abiertas. HU02 y HU03 no tienen comentarios adicionales al momento de la consulta.

Se consultaron también [HU12, #7](https://github.com/s-villanueva/video-data-analysis/issues/7) y [HU16, #1](https://github.com/s-villanueva/video-data-analysis/issues/1) para respetar la futura conservación/versionado de resultados y el reproceso explícito.

## 2. Diagnóstico del proyecto

### Arquitectura y dominio

- Backend Maven con Java objetivo 21, Spring Boot 4.1.1, Lombok y PostgreSQL 17 en Docker Compose.
- Dependencias por capas: `semillero-api → core → data`. El JSON utiliza Jackson 3 mediante `tools.jackson`; hay que mantener ese patrón en las clases nuevas.
- `data`: entidades JPA, repositorios Spring Data, DTOs de entrada/salida y enums. `BaseEntity` genera IDs `Long` mediante identidad.
- `core`: servicios concretos por recurso, transacciones y reglas de negocio. `EntityRelations` resuelve las FK y produce errores 404 cuando corresponde.
- `semillero-api`: 17 recursos CRUD, más las operaciones de videos de partidos; validación Jakarta, paginación con `PageResponse` y errores `ProblemDetail` centralizados.
- Dominio existente: países/ciudades, clubes, usuarios y perfiles, jugadores y posiciones, fichajes, pruebas, clínicas, conversaciones, partidos y participaciones. Las participaciones vinculan jugador, partido y club con unicidad por jugador/partido.
- Las contraseñas se almacenan con PBKDF2. Todavía no hay autenticación ni autorización HTTP; las historias revisadas no especifican implementar ese subsistema.

### Flujo de videos que ya funciona como base

1. `VideoPartidoController` recibe multipart en `/api/v1/partidos/{partidoId}/videos`.
2. `VideoPartidoService` comprueba el partido y el nombre; `LocalVideoStorage` copia el archivo, calcula SHA-256 y controla el tamaño real.
3. `FfmpegVideoInspector` inspecciona MP4 con `ffprobe` y decodifica el contenido con FFmpeg.
4. Se conserva `original.mp4` bajo un UUID y se registra `VideoPartido` mediante una transacción corta. Los fallos normales de persistencia compensan el archivo.
5. `YouTubeUploadWorker` publica por separado usando una cola SQL, bloqueo de fila, reserva con vencimiento y token de propietario. Invoca el script Python fuera de la transacción.

`Video` representa enlaces de videos de jugadores; `VideoPartido` representa los originales de partidos. HU02 debe usar `VideoPartido`. `disponibleParaAnalisis` solo comprueba la existencia del original. `estadoYoutube` representa publicación, incluso cuando su valor es PROCESANDO; ninguno representa el trabajo de análisis.

### Infraestructura, pruebas y brechas

| Área | Estado observado | Consecuencia para HU02/HU03 |
| --- | --- | --- |
| Trabajos de análisis | No hay `AnalysisJob`, estado de análisis, endpoints ni cliente del analizador. | Crear el ciclo de vida completo de la solicitud. |
| Analizador | No se encontró código ni contrato HTTP implementado en las fuentes consultadas. | Definir el contrato y disponer de un servicio real antes de validar el flujo integrado. |
| Resultados | No hay integración Couchbase ni repositorio de resultados; el README menciona un microservicio futuro. | Separar la consulta de estado de HU12/HU13; no inventar métricas ni disponibilidad. |
| Interfaz | No hay frontend en este repositorio. | Entregar API y guía de consumo; la navegación visual se valida cuando exista el cliente. |
| Scheduler | Solo se configura `@EnableScheduling`; el uploader realiza una operación potencialmente larga. | Dar al análisis un scheduler propio para que la publicación no retrase su despacho o consulta. |
| Migraciones | Existe únicamente `001_videos_partido.sql`, de aplicación manual; `dev` usa `ddl-auto=update`. | Añadir SQL incremental y verificarlo en PostgreSQL; H2 no valida índices parciales de PostgreSQL. |
| Regresión | Tres clases Java cubren CRUD, HU01 y el adaptador YouTube; el script tiene 14 casos Python. | Mantener esas pruebas y añadir cobertura específica de estados, concurrencia y reinicios. |
| Documentación | README enlaza `dbfutbol.md` y `PLAN_VIDEO_DATA_ANALYSIS.md`, que no existen en el checkout. | Este plan usa el modelo y las historias verificadas, sin asumir el contenido de esos archivos. |
| Windows | El uploader importa `fcntl`, módulo POSIX; su guía indica Linux/macOS. | La publicación Python requiere un entorno compatible; el análisis debe funcionar con YouTube desactivado. |

## 3. Diseño propuesto

Mantener la orquestación de trabajos en el backend Java y persistir su estado en PostgreSQL. Un worker independiente despacha y consulta al futuro microservicio a través de `AnalysisGateway`. El procesamiento de visión y sus resultados detallados quedan en ese servicio y en sus historias correspondientes.

```text
Cliente → POST iniciar → AnalysisJob PENDIENTE en PostgreSQL → HTTP 202 + ID
                                 ↓
                     Worker con reserva persistente
                                 ↓
                 AnalysisGateway → servicio de análisis
                                 ↓
                   Estado y avance guardados en SQL
                                 ↑
Cliente → GET por ID / listado por video
```

La tabla de trabajos actúa como cola durable inicial. No hace falta incorporar un broker para estas dos historias. Cerrar la pantalla, cancelar el polling del cliente o reiniciar el backend no elimina el trabajo. Una tarea en memoria o `@Async` por sí solos no resuelven esa persistencia.

Decisiones propuestas para esta entrega:

- Analizar el original validado que ya está almacenado. No depender de publicación, credenciales ni disponibilidad de YouTube.
- Crear el trabajo únicamente después de validar la relación partido/video y la presencia del original.
- Usar el ID del análisis como clave idempotente al hablar con el analizador. Un reenvío técnico conserva ese ID.
- Implementar polling HTTP del cliente, inicialmente cada 3–5 segundos, y detenerlo al llegar a un estado terminal. No se necesita WebSocket para cumplir las historias.
- No exponer un flag de reproceso en HU02. Como política inicial propuesta, si ya existe un análisis terminal, exigir el flujo explícito que se desarrollará en HU16; no sobrescribir ni crear intentos implícitos. Este comportamiento después de un estado terminal debe confirmarse como contrato de producto: HU02 exige expresamente evitar duplicados activos, pero no define por sí sola toda la política de reprocesos.

## 4. Modelo de datos y transiciones

Crear `AnalysisJob` en `data/.../entity`, `EstadoAnalisis` en `data/.../enums` y `AnalysisJobRepository` en `data/.../repos`. Tabla propuesta: `analysis_jobs`.

| Grupo | Campos propuestos |
| --- | --- |
| Identidad | `id`, `video_partido_id` obligatorio con FK a `videos_partido`. `partidoId` se deriva del video al responder para evitar asociaciones contradictorias. |
| Ciclo de vida | `estado`, `fecha_solicitud`, `fecha_inicio`, `fecha_fin`, `fecha_actualizacion`, `revision` con `@Version`. Usar `Instant`. |
| Avance | `porcentaje`, `fotogramas_procesados`, `fotogramas_totales`, todos opcionales mientras no haya medición real. |
| Integración | `external_job_id` si el servicio lo requiere; `resultado_ref` y disponibilidad confirmada por el proveedor de resultados. |
| Diagnóstico | `codigo_error`, `mensaje_error` apto para el usuario. |
| Ejecución | `claim_token`, `lease_until`, `proxima_ejecucion`, contador de fallos técnicos y límite temporal del trabajo. |

Transiciones permitidas:

```text
PENDIENTE ──inicio confirmado──→ PROCESANDO ──fin confirmado──→ COMPLETADO
    │                              │
    └────────fallo definitivo───────┴────────────────────────→ ERROR
```

- Una reserva del worker no significa que el análisis haya comenzado: conservar PENDIENTE hasta que el analizador confirme PROCESANDO. Si una consulta remota salta directamente al fin, registrar los datos de inicio/fin que entregue el proveedor sin inventar un estado intermedio observado por el cliente.
- `fechaInicio` es opcional antes del inicio; `fechaFin` se registra en estados terminales. Los terminales no regresan a estados activos.
- Porcentaje finito entre 0 y 100; fotogramas no negativos y procesados no mayores que el total conocido. Sin un total fiable, no calcular un porcentaje artificial.
- Ignorar actualizaciones obsoletas; el token de reserva y el control de versión protegen las escrituras. Mantener el avance coherente dentro del mismo análisis.
- `COMPLETADO` requiere confirmación real del fin. `resultadosDisponibles` indica disponibilidad confirmada de los resultados de ese análisis; puede ser `false` si todavía no son consultables. El fin del proceso por sí solo no habilita un enlace inexistente ni implementa HU12.

Añadir `database/migrations/002_analysis_jobs.sql`, con FK, comprobaciones de estados/valores, índice para trabajos ejecutables y un índice único parcial por video para estados PENDIENTE/PROCESANDO. El servicio debe bloquear la fila existente de `VideoPartido` mientras consulta e inserta el trabajo. El índice refuerza la regla frente a accesos concurrentes y escrituras que omitan el servicio.

La migración se aplica sobre una base que ya tiene HU01. Como en el esquema actual, documentar su aplicación manual antes de `ddl-auto=validate`, y el procedimiento para bases creadas previamente con `dev`; no ejecutar DDL duplicado a ciegas. Probar el índice parcial en PostgreSQL real y no representarlo como una capacidad cubierta por H2.

## 5. Contrato público de API propuesto

| Método y ruta | Comportamiento |
| --- | --- |
| `POST /api/v1/partidos/{partidoId}/videos/{videoId}/analisis` | Sin recargar el archivo; registra una solicitud y responde `202 Accepted`, snapshot PENDIENTE y `Location` del análisis. |
| `GET /api/v1/analisis/{analysisId}` | `200` con el estado persistido, avance y datos de finalización. |
| `GET /api/v1/partidos/{partidoId}/videos/{videoId}/analisis` | Lista paginada con `PageResponse`, orden más reciente primero. Permite recuperar el ID al volver a la pantalla. |

La respuesta del POST se construye desde la creación confirmada como PENDIENTE; el worker no se invoca dentro de esa petición. Una consulta posterior puede mostrar un estado más avanzado.

Ejemplo de respuesta de consulta; nombres y tipos a fijar en el contrato de la primera entrega:

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

Reutilizar `ApiExceptionHandler` y el formato `ProblemDetail`:

- `400`: ID mal formado o no positivo y parámetros inválidos.
- `404`: partido, video o análisis inexistente; también video ajeno al partido indicado.
- `409`: ya hay análisis activo. Incluir `analysisId` y la ruta de consulta para recuperar el existente, sin crear otra fila. Aplicar la política acordada de reproceso a trabajos terminales.
- `503`: falta el original o la funcionalidad está deshabilitada/no configurada. No crear una solicitud que el despliegue sabe que no puede atender.

Una caída transitoria del analizador después de habilitar la integración no debe perder una solicitud aceptada: conservarla y reconciliar mediante el worker, dentro de los límites de recuperación definidos. La consulta pública lee SQL y sigue disponible durante esa caída; `fechaActualizacion` informa la antigüedad de los datos.

## 6. Ejecución durable e integración

### Worker Java

1. Buscar trabajos con `proximaEjecucion` vencida y sin reserva vigente.
2. Reclamar uno en una transacción corta, con bloqueo, token y vencimiento.
3. Resolver el original mediante `LocalVideoStorage` y verificar que sigue disponible.
4. Fuera de la transacción, enviar o consultar el mismo trabajo a través de `AnalysisGateway`.
5. Validar la respuesta y persistir estado/avance solo si el token sigue vigente. Liberar la reserva y programar la siguiente consulta.
6. Si el backend cae, una nueva instancia recupera la reserva vencida y consulta/reenvía el mismo ID. No crear otro trabajo ni reiniciar ciegamente el procesamiento remoto.

Configurar un `TaskScheduler` propio para este worker, independiente del uploader, con concurrencia limitada. Mantener las llamadas remotas acotadas y más cortas que la reserva. Proponer inicialmente polling de 5 segundos, timeout HTTP de 10 segundos y reserva de 30 segundos, todos configurables; el plazo total del análisis requiere acordarse con el equipo que lo procesa.

Distinguir fallos de transporte de un ERROR definitivo del analizador. Usar espera creciente y límite de tiempo/fallos consecutivos para transporte; registrar ERROR al agotar la política, sin dejar trabajos bloqueados indefinidamente. Esta recuperación del mismo trabajo no es el nuevo intento de usuario de HU16.

### Contrato con el analizador: dependencia pendiente

Propuesta de `AnalysisGateway` con operaciones `submit` y `getStatus`, implementada mediante un cliente HTTP con timeouts. Las rutas internas siguientes son una propuesta, no endpoints existentes:

- `PUT /internal/v1/analisis/{analysisId}`: alta idempotente con `videoId`, `partidoId`, `storageId` y checksum. Repetir el ID con el mismo contenido recupera el trabajo; contenido distinto produce conflicto.
- `GET /internal/v1/analisis/{analysisId}`: estado, avance opcional, fechas, error público y referencia/disponibilidad de resultados.

Como primera opción de despliegue, usar un volumen compartido de originales, de solo lectura para el analizador. Ambos servicios resuelven el UUID contra su propia raíz configurada; no enviar rutas absolutas de Windows esperando que existan en otro host. Si el despliegue no permite ese volumen, acordar transferencia privada por streaming u object storage antes de implementar el adaptador.

También debe acordarse cómo se autentican los servicios y quién confirma que los resultados son consultables. Las rutas públicas no exponen `storageId`, rutas físicas, tokens ni datos internos del proveedor.

El gateway simulado se usa solo en pruebas. Sin un analizador real disponible se puede entregar y probar la orquestación, pero no declarar validado el procesamiento de un partido ni cerrar la aceptación integral de HU02/HU03.

## 7. Cambios por capa

| Ubicación | Trabajo propuesto |
| --- | --- |
| `data/.../entity/AnalysisJob.java` | Entidad, relación con video, fechas, progreso, reserva y versión. |
| `data/.../enums/EstadoAnalisis.java` | Los cuatro estados requeridos. |
| `data/.../repos/AnalysisJobRepository.java` | Consulta por ID/video, detección de activo y selección/bloqueo de ejecutables. |
| `data/.../dto/response/AnalysisJobResponseDto.java` | Contrato público sin campos internos de reserva o almacenamiento. |
| `core/.../service/AnalysisJobService.java` | Alta transaccional, prevención de duplicados, consulta y listado. |
| `core/.../service/analysis/` | `AnalysisWorker`, `AnalysisGateway`, adaptador HTTP y validación de respuestas/transiciones. |
| `core/.../config/` | `AnalysisProperties` y configuración del scheduler propio. |
| `semillero-api/.../controller/` | Controladores de creación/listado por video y consulta por ID. |
| `semillero-api/.../controller/exception/ApiExceptionHandler.java` | Conflicto de análisis activo con referencia al existente y errores de integración aptos para el cliente. |
| `semillero-api/src/main/resources/application.properties`, `.env.example` | Activación, URL del servicio, acceso interno, polling, timeouts, reserva y límites de recuperación. |
| `database/migrations/002_analysis_jobs.sql` | Tabla, restricciones e índices de PostgreSQL. |
| `semillero-api/src/test/` | Integración HTTP, worker con gateway controlado, regresión y perfil de PostgreSQL. |
| `docs/`, `README.md` | Contrato, estados, operación, migración y ejemplos reproducibles de consumo. |

Reutilizar las excepciones y la resolución segura del original existentes. No es necesario alterar las reglas de jugadores/participaciones ni mezclar estados de YouTube con los de análisis.

## 8. Orden de implementación y entregables

| Paso | Subtarea / alcance | Entrega verificable |
| --- | --- | --- |
| 0 | Contrato de integración | Fijar identidad idempotente, acceso al original, semántica de resultados, política de reproceso y límites; registrar qué servicio implementará el contrato. |
| 1 | SB-004 + base de SB-007 | Modelo, migración, POST y GET. Persistir PENDIENTE y responder 202 con un ID consultable. |
| 2 | SB-006 | Bloqueo por video, índice de activo y conflicto que identifica el análisis existente; pruebas concurrentes. |
| 3 | SB-005 + transición de SB-004 | Gateway y worker durable con scheduler propio; despacho, transición real a PROCESANDO y recuperación tras reinicio. |
| 4 | SB-007 | Consulta de todos los estados, 404 y listado para recuperar análisis al volver. |
| 5 | SB-008 | Validar y persistir avance real; devolver null si no existe información. |
| 6 | SB-009 | Persistir fin, ERROR comprensible y disponibilidad de resultados confirmada. |
| 7 | Cierre conjunto | Regresión, pruebas PostgreSQL, prueba integrada con analizador real y documentación de consumo. |

Los pasos 1 y 2 pueden desarrollarse con independencia del servicio remoto. Los pasos 3–6 pueden probarse con un gateway controlado, pero su validación real depende del contrato y del analizador del paso 0.

## 9. Matriz de pruebas y criterios de cierre

| Caso | Evidencia esperada |
| --- | --- |
| Video válido asociado al partido | Un POST crea una fila y devuelve 202, PENDIENTE, ID y Location. |
| Partido/video inválido, relación cruzada u original ausente | 404/503 según el caso, sin filas de análisis nuevas. |
| Dos peticiones concurrentes del mismo video | Solo un trabajo activo; la otra respuesta es 409 con su ID. Verificar además la restricción directamente en PostgreSQL. |
| Dos videos distintos del mismo partido | Pueden tener sus propios trabajos activos. |
| Inicio real | Un proveedor que confirma inicio provoca PROCESANDO; reservar el trabajo no basta. |
| Cliente abandona la pantalla | Dejar de consultar no detiene el worker; consultar después devuelve el mismo ID. Los CRUD siguen respondiendo. |
| Reinicio con trabajo pendiente o reserva vencida | Nueva instancia recupera el trabajo persistido sin duplicar el procesamiento remoto. |
| Respuesta remota de alta perdida | Reenviar el mismo ID recupera un único trabajo remoto. |
| Dos workers o respuesta de propietario vencido | Solo la reserva vigente puede actualizar; los terminales no retroceden. |
| YouTube deshabilitado o publicándose | El análisis y su consulta continúan por su scheduler propio. |
| Analizador temporalmente caído | Consulta del estado sigue funcionando; recuperación limitada y ERROR final si corresponde. |
| Avance desconocido, parcial o inválido | null si falta; porcentaje válido o fotogramas coherentes si existe; rechazar respuestas fuera de rango/obsoletas. |
| Finalización | COMPLETADO con fechaFin y disponibilidad real, incluyendo el caso de resultados aún no consultables. |
| Error definitivo | ERROR con fechaFin y mensaje público; no exponer detalles internos. |
| ID inexistente o mal formado | 404 o 400 respectivamente, con ProblemDetail. |
| Reproceso posterior | Comportamiento explícito según la política fijada; conservar el registro y los resultados previos. |

Usar MockMvc/H2 para contrato y reglas, y PostgreSQL 17 desechable para DDL, índice parcial, bloqueos y concurrencia. Actualizar la limpieza de fixtures para eliminar trabajos antes de sus videos/partidos. Probar reinicio con almacenamiento SQL persistente, no solo con mocks o una base que se destruye entre contextos.

Para terminar las historias deben pasar la regresión del proyecto, las pruebas nuevas, el arranque con `ddl-auto=validate` tras aplicar la migración y el recorrido integrado con el proveedor real. La navegación de una pantalla queda como prueba pendiente del frontend cuando exista; la API debe permitirla desde esta entrega.

## 10. Límites de esta entrega

Estas historias incorporan la orquestación y consulta del análisis. La detección de jugadores/balón, tracking, calibración, métricas, mapas de calor, conservación/versionado detallado y consulta de resultados pertenecen a sus historias específicas. El reproceso confirmado con nuevos intentos pertenece a HU16.

No añadir porcentajes calculados por tiempo transcurrido, procesos simulados que terminen exitosamente en producción ni `resultadosDisponibles=true` sin evidencia del proveedor. La ausencia actual del analizador es una dependencia para la integración real, no una razón para fabricar esos comportamientos.

## 11. Verificación previa a la implementación

- La rama de origen estaba limpia y coincidía con la referencia remota. Solo se añadió documentación de planificación en la rama nueva.
- Se intentó `mvnw.cmd -o verify`; el wrapper intentó preparar Maven 3.10.0 fuera del espacio de escritura permitido. Para verificar la base se utilizó Maven 3.9.16 ya instalado, en modo offline, con JDK 25.0.2 y compilación objetivo Java 21. `verify` terminó con BUILD SUCCESS: 47 pruebas correctas, 0 fallos, 0 errores y 0 omitidas (23 CRUD, 7 del adaptador YouTube y 17 HU01). La ejecución necesitó salir de la restricción de procesos local para que Mockito pudiera adjuntarse a la JVM. No se modificaron las versiones configuradas del proyecto.
- `python -m unittest discover -s scripts/tests -v` no pudo importar el uploader en Windows: `ModuleNotFoundError: No module named 'fcntl'`. Sus 14 casos no llegaron a ejecutarse en este entorno; la comprobación documentada en HU01 corresponde a otra ejecución y no sustituye esta limitación local.
- En esa revisión todavía no se había implementado HU02/HU03 y no se ejecutaron migraciones ni operaciones de escritura en el tablero remoto. La verificación posterior del backend se documenta en HU02_HU03_ANALISIS.md.
