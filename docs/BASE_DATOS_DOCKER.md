# PostgreSQL y migraciones automáticas

Docker levanta PostgreSQL 17 y crea la base y su usuario. **Spring ejecuta Flyway al arrancar**, antes de validar las entidades con Hibernate. Esto funciona con una base vacía y con instalaciones anteriores del proyecto; no hay que aplicar la migración 004 a mano.

## Arranque del equipo

Desde la raíz, en Bash:

```bash
# Solo si todavía no tienes .env:
cp .env.example .env
# Editar DB_PASSWORD con una contraseña local.
set -a
source .env
set +a

docker compose up -d --wait db
./mvnw clean verify
java -jar semillero-api/target/semillero-api-0.0.1-SNAPSHOT.jar
```

Compose lee `.env` automáticamente; Java necesita las variables exportadas o configuradas en el IDE. En Windows usar `mvnw.cmd` y configurar `DB_URL`, `DB_USER` y `DB_PASSWORD` en el entorno. Mantener `DB_DDL_AUTO=validate`, también con el perfil `dev`.

Con los valores iniciales, la conexión es `jdbc:postgresql://localhost:5432/semillero`, usuario `semillero`. Si cambias `DB_NAME` o `DB_PORT_EXTERNAL`, ajustar también `DB_URL`. Dentro de otro contenedor del mismo Compose, el host es `db:5432`.

Docker por sí solo deja la base vacía. Al iniciar el backend, Flyway crea **20 tablas de negocio y `flyway_schema_history`**. Los archivos SQL se incluyen dentro del JAR; no depende del directorio desde el que se ejecuta Java. Spring Boot integra [Flyway antes de JPA](https://docs.spring.io/spring-boot/how-to/data-initialization.html#howto.data-initialization.migration-tool.flyway).

## Bases que ya tienen datos

Conservar `.env` y el volumen `db_data`. Después de actualizar el código, arrancar el backend normalmente. La adopción anterior a Flyway ocurre una sola vez:

1. Comprueba las 17 tablas base conocidas y columnas características. Acepta también las tablas opcionales de videos, análisis y métricas. Un esquema incompleto, ajeno o con tablas adicionales sin historial se detiene para revisión.
2. Registra una base histórica `-1`, que no omite ninguna migración del proyecto.
3. Ejecuta V000–V004. El SQL conserva tablas y datos existentes y agrega las restricciones e índices ausentes. La 004 añade `respuesta_json` si falta.
4. Valida las entidades y permite arrancar la API.

Esto cubre bases creadas con los SQL anteriores, incluidas instalaciones que ya aplicaron 004, y bases creadas mediante `ddl-auto=update`. Los datos incompatibles con una restricción detienen la migración; no se borran ni se corrigen silenciosamente. La migración fallida se revierte y puede ejecutarse de nuevo tras corregir la causa. Las anteriores que ya terminaron permanecen registradas.

`baseline-on-migrate` permanece desactivado. La adopción está limitada por la comprobación del esquema; no se reconoce cualquier base como Semillero. Un bloqueo PostgreSQL coordina la adopción y migración si arrancan varias instancias simultáneamente. El historial conserva versiones y checksums: en reinicios posteriores se ejecutan únicamente las versiones pendientes. No hay borrado ni reparación automática del historial.

El usuario de conexión debe poder crear y modificar las tablas del esquema `public`. El usuario creado por Compose ya es propietario de su base. Si una base compartida usa otro propietario, su administrador debe proporcionar los permisos correspondientes. La configuración del proyecto usa `public`.

## Comprobar versiones

Después de arrancar el backend:

```bash
docker compose exec db psql -U semillero -d semillero -c '\dt'
docker compose exec db psql -U semillero -d semillero -c 'SELECT version, description, type, success FROM flyway_schema_history ORDER BY installed_rank;'
```

Las versiones SQL son 000–004. Las bases adoptadas tienen además la entrada `BASELINE` con versión `-1`. En una base nueva no se necesita esa entrada.

| Archivo | Contenido |
| --- | --- |
| [V000__base.sql](../database/migrations/V000__base.sql) | 17 tablas base, partidos y participaciones |
| [V001__videos_partido.sql](../database/migrations/V001__videos_partido.sql) | Originales y metadatos de YouTube |
| [V002__analysis_jobs.sql](../database/migrations/V002__analysis_jobs.sql) | Trabajos, restricciones e índice parcial de análisis activos |
| [V003__player_match_metrics.sql](../database/migrations/V003__player_match_metrics.sql) | Métricas y FK compuesta a participaciones |
| [V004__analysis_future_response.sql](../database/migrations/V004__analysis_future_response.sql) | JSON final y validación de su estructura |

Los nombres siguen `V<versión>__<descripción>.sql`. La fuente única es `database/migrations`; Maven la empaqueta en `db/migration`. No editar archivos ya registrados por Flyway: un checksum distinto detiene el arranque. Para un cambio nuevo añadir, por ejemplo, `V005__descripcion.sql` y reconstruir el JAR. No se necesita modificar código Java ni ejecutar SQL manual.

## Datos de ejemplo opcionales

Con el esquema ya migrado:

```bash
docker compose exec -T db psql -v ON_ERROR_STOP=1 -U semillero -d semillero -f /opt/semillero/database/seeds/demo.sql
```

[demo.sql](../database/seeds/demo.sql) crea un país, ciudad, dos clubes, jugadores sintéticos y un partido con sus participaciones. Imprime los IDs generados y rechaza repetir la carga. No crea resultados de análisis ficticios.

Los archivos se montan de solo lectura en `/opt/semillero/database`, fuera de la inicialización automática de PostgreSQL. [init.sql](../database/init.sql) queda como utilidad manual para construir una base vacía; el flujo normal usa exclusivamente Flyway desde Spring. Si ya se utilizó esa utilidad, el siguiente arranque adopta el esquema.

## Analizador y YouTube

Migrar la base no necesita acceso a Google ni al servicio de análisis. Los CRUD arrancan con las integraciones deshabilitadas. Cargar videos requiere configurar y habilitar Python según la [integración automática](INTEGRACION_AUTOMATICA_VIDEO.md); sin él, la carga responde `503`. YouTube sigue siendo opcional.

## Persistencia y pruebas

`docker compose down` conserva el volumen y los datos. Para una copia aislada, usar otro nombre de proyecto y puerto:

```bash
DB_PORT_EXTERNAL=55432 docker compose -p semillero-pruebas up -d --wait db
# Ejecutar el backend con DB_URL=jdbc:postgresql://localhost:55432/semillero.
DB_PORT_EXTERNAL=55432 docker compose -p semillero-pruebas down
```

Las pruebas normales usan H2 con Flyway deshabilitado y esquema JPA desechable. Las migraciones de producción se prueban contra PostgreSQL real, mediante una URL local restringida a la base desechable `migrations_test`, usuario `migrations_test`, sin contraseña en ese entorno aislado:

```bash
MIGRATIONS_PG_TEST_URL=jdbc:postgresql://127.0.0.1:55437/migrations_test ./mvnw clean verify
```

Estas pruebas recrean exclusivamente ese esquema de prueba. Cubren instalación vacía, reinicio, adopción de SQL previos e Hibernate, actualización hasta 004, conservación del JSON, rechazo de esquemas ajenos, rollback, arranques concurrentes y validación de checksums. Las copias antiguas de SQL bajo `src/test/resources/db/legacy` son fixtures históricos; no se distribuyen como migraciones de producción.

## Verificación del 2026-10-25

`clean verify` completó **133 pruebas sin fallos, errores ni omisiones**, con las tres bases PostgreSQL desechables habilitadas mediante `MIGRATIONS_PG_TEST_URL`, `ANALYSIS_PG_TEST_URL` y `METRICS_PG_TEST_URL`. Incluye 10 casos de migraciones automáticas y las pruebas existentes de CRUD, videos, análisis y métricas. Se comprobó también la configuración de Compose y la inclusión de V000–V004 y Flyway en el JAR ejecutable. La base del proyecto no se modificó.
