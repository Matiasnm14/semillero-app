# HU12 — Modelo e integridad de métricas

Implementación de **SB-050 / #66** (`PlayerMatchMetrics`) y **SB-052 / #68** (integridad de jugador y partido). Se integra con `AnalysisJob` de HU02/HU03 y los catálogos existentes de Semillero.

Esta entrega prepara persistencia y validación de referencias. La publicación exclusiva de análisis completados, el cambio de versión vigente y la recepción de resultados se incorporarán con SB-051/SB-053 y el contrato del analizador. HU12 todavía no está completa.

## Clases y responsabilidades

| Clase / archivo | Responsabilidad |
| --- | --- |
| `data/.../entity/PlayerMatchMetrics.java` | Entidad de métricas, intervalo, relaciones y campos de versionado |
| `data/.../repos/PlayerMatchMetricsRepository.java` | Persistencia; búsqueda por análisis y por análisis/jugador/intervalo |
| `core/.../service/PlayerMatchMetricsIntegrityService.java` | Validación de referencias y pertenencia; devuelve las entidades resueltas |
| `database/migrations/003_player_match_metrics.sql` | Tabla, restricciones, índices y FK compuesta hacia la participación |

El modelo mantiene la organización `semillero-api → core → data` y servicios concretos. La futura publicación usará esta validación desde su propia transacción; esta entrega no añade un endpoint de escritura que permita guardar métricas sin aplicar las reglas de publicación pendientes.

## Esquema de SB-050

| Columna SQL | Tipo / significado |
| --- | --- |
| `metric_id` | `bigint` identity; corresponde a `getId()` heredado de `BaseEntity` |
| `analysis_id` | FK obligatoria a `analysis_jobs.id` |
| `match_id` | FK obligatoria a `partidos.id`; relación Java `partido` |
| `player_id` | FK obligatoria a `jugadores.id`; relación Java `jugador` |
| `total_distance` | `numeric(19,6)` / `BigDecimal`, no negativo |
| `avg_speed` | `numeric(19,6)` / `BigDecimal`, no negativo |
| `max_speed` | `numeric(19,6)` / `BigDecimal`, mayor o igual que el promedio |
| `period_start` | `numeric(19,6)` / `BigDecimal`, no negativo |
| `period_end` | `numeric(19,6)` / `BigDecimal`, mayor que el inicio |
| `version` | Entero positivo; versión del resultado, independiente de las revisiones de `AnalysisJob` |
| `is_current` | Booleano obligatorio; preparado para selección de la versión vigente en SB-053 |
| `created_at` | Fecha de creación UTC con precisión de microsegundos, asignada al persistir |

Las relaciones, valores, intervalo, versión y fecha se inicializan sin setters y con columnas `updatable=false`. SB-053 incorporará la operación controlada para cambiar la selección de la versión vigente conservando los valores históricos. No hay incremento automático de versión ni publicación automática en esta entrega.

### Precisión y unidades

Se usa `BigDecimal` con hasta 13 dígitos enteros y 6 decimales. La validación JPA rechaza valores con más precisión antes de guardarlos; así evita un redondeo implícito al escribir desde Java. Las restricciones SQL también rechazan valores negativos, intervalos inválidos y valores especiales como `NaN`.

Es una decisión inicial de almacenamiento, independiente del transporte. **Las unidades de distancia/velocidad y la unidad del intervalo siguen pendientes del acuerdo con el analizador.** No se convierten valores, no se comparan aún con la duración del video ni se interpreta el intervalo como minutos de participación. Un escritor SQL externo debe respetar la precisión: PostgreSQL puede redondear valores con más decimales que la escala declarada. [Referencia de tipos numéricos](https://www.postgresql.org/docs/17/datatype-numeric.html).

### Duplicados e índices

La combinación `(analysis_id, player_id, period_start, period_end)` es única. Repetir el mismo resultado para el mismo análisis/jugador/intervalo produce un conflicto, aunque se envíe otro número de versión. Distintos intervalos pueden coexistir en el mismo análisis.

La restricción no define todavía si la versión vigente abarcará un partido, un video o un intervalo; ese acuerdo se requiere para SB-053. Un reproceso deberá tener su propia identidad de ejecución, conforme al futuro flujo de HU16.

Se añaden índices por `(match_id, player_id)` y `player_id`; el índice único también permite buscar por análisis. La consulta y optimización del historial se desarrollarán con HU13/HU14.

## Validaciones de SB-052

El método `PlayerMatchMetricsIntegrityService.validate(analysisId, partidoId, jugadorId)` comprueba:

1. Los tres IDs son positivos y no nulos.
2. El análisis, partido y jugador existen.
3. El video del análisis pertenece al partido indicado.
4. Existe `ParticipacionPartido` para ese jugador y partido.
5. El club registrado en esa participación es local o visitante del partido.

La pertenencia se basa en la participación histórica del partido, sin exigir que el jugador mantenga hoy el mismo club. Antes de importar métricas debe estar registrada la plantilla mediante el CRUD de participaciones; un ID de tracking debe resolverse al `Jugador.id` real.

El método devuelve `References(analysis, partido, jugador)`, con las entidades verificadas. Un ID inexistente produce `NotFoundException`; IDs inválidos o asociaciones inconsistentes producen `BusinessException`, con un motivo específico. La validación es de lectura y no modifica estados ni crea métricas.

**Integración posterior:** el servicio de publicación debe invocar esta validación dentro de su transacción de escritura y aplicar además el control `COMPLETADO` de SB-051. Una validación previa desde otra transacción no constituye una reserva de las asociaciones para una escritura futura.

## Integridad en PostgreSQL

Además de las tres FK de catálogo, se añade:

```sql
FOREIGN KEY (match_id, player_id)
REFERENCES participaciones_partido(partidos_id, jugadores_id)
```

Esto impide insertar métricas de un jugador ajeno al partido incluso si los IDs individuales existen. También impide eliminar o mover silenciosamente una participación ya referenciada. Se reutiliza la clave única existente de `ParticipacionPartido`. [Referencia de restricciones y claves foráneas](https://www.postgresql.org/docs/17/ddl-constraints.html).

La correspondencia entre el partido y el video del análisis, y la pertenencia del club al partido, se validan en el servicio. Los escritores deben usar ese servicio: las FK escalares por sí solas no comprueban esas relaciones transitivas.

## Aplicación de la migración

En instalaciones nuevas, Docker Compose aplica 000–004 automáticamente mediante [init.sql](../database/init.sql). El procedimiento manual siguiente es para bases existentes que aún no tengan 003; consultar la [guía del equipo](BASE_DATOS_DOCKER.md).

Con los catálogos base y las migraciones 001/002 aplicadas, ejecutar **una vez** la [migración 003](../database/migrations/003_player_match_metrics.sql), antes de arrancar con `ddl-auto=validate`.

Desde la raíz, con `.env` exportado:

```bash
docker compose exec -T db psql -v ON_ERROR_STOP=1 \
  -U "$DB_USER" -d "$DB_NAME" < database/migrations/003_player_match_metrics.sql
```

También hay que ejecutarla si el perfil `dev` ya creó la tabla: Hibernate crea las FK individuales y comprobaciones del modelo, pero la FK compuesta hacia la participación se incorpora mediante el SQL. La migración conserva una tabla creada por `dev` y añade esa FK; las restricciones hacen fallar la transacción si existen registros incompatibles. No elimina datos para conciliarlos.

No repetir la migración una vez aplicada: la adición de la FK compuesta es una operación única. El proyecto mantiene la aplicación manual de migraciones.

## Verificación

```bash
./mvnw verify
```

La suite normal incluye 20 casos nuevos de modelo/integridad con H2: recuperación exacta de valores, referencias y fecha; duplicados; intervalos distintos; precisión; inconsistencias numéricas; IDs inválidos; registros inexistentes; análisis de otro partido; jugador ajeno; participación con club ajeno y validación sin publicación.

La prueba PostgreSQL se habilita exclusivamente con una URL local a una **base desechable** llamada `metrics_hu12_test`, cuyo usuario sea `metrics_test`. Recrea las tablas de esa base. Con ese entorno preparado:

```bash
METRICS_PG_TEST_URL=jdbc:postgresql://127.0.0.1:55438/metrics_hu12_test ./mvnw verify
```

Comprueba la migración sobre tabla creada por `dev`, la cadena SQL 001–003 desde tablas de catálogo existentes, arranque con `validate`, FK compuesta, restricciones mediante SQL directo y recuperación de métricas después de reiniciar el contexto.

**Verificación local del 2026-10-21:** 108 pruebas Java correctas, sin errores ni omitidas, habilitando las bases PostgreSQL temporales de HU02/HU03 y de métricas. Incluye las 21 pruebas nuevas de esta entrega (20 de modelo/integridad y un escenario PostgreSQL). La base del proyecto no se modificó.

## Pendientes para el equipo

- Confirmar unidades, precisión e interpretación de `period_start`/`period_end` en el contrato de resultados.
- El gateway adaptado recibe el JSON final mediante Future y lo conserva en `AnalysisJob.respuestaJson`; las cargas nuevas ya lo guardan junto con el video y un análisis completado mediante la [integración automática](INTEGRACION_AUTOMATICA_VIDEO.md). Acordar su estructura antes de convertirlo en métricas definitivas.
- Resolver dorsales repetidos entre equipos y asociar cada resultado con la participación real. El JSON recibido aún no equivale a métricas publicadas.
- Definir ámbito y asignación de la versión vigente; implementar publicación transaccional e idempotente con SB-051/SB-053.
- Completar SB-054 cuando exista ese flujo para probar la publicación completa y el cambio atómico de versión.
