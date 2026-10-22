# Base de datos del equipo con Docker

Cada integrante puede levantar su propia copia de PostgreSQL 17 con el esquema completo del backend. No hace falta arrancar Java para crear las tablas ni configurar Google o el analizador para probar los CRUD.

## Primera instalación

Desde la raíz del repositorio:

```bash
# Solo si todavía no tienes un .env:
cp .env.example .env
# Editar DB_PASSWORD con una contraseña local.
docker compose up -d --wait db
```

En PowerShell, la copia inicial es `Copy-Item .env.example .env`; el comando de Docker es el mismo. Si ya tienes `.env`, conservarlo y revisar la sección de bases existentes antes de ejecutar SQL.

Docker crea la base y el usuario de `.env`, y ejecuta automáticamente [database/init.sql](../database/init.sql). Ese archivo carga, en orden:

| Archivo | Contenido |
| --- | --- |
| [000_base.sql](../database/migrations/000_base.sql) | 17 tablas de catálogos y CRUD: usuarios, clubes, jugadores, partidos, participaciones, etc. |
| [001_videos_partido.sql](../database/migrations/001_videos_partido.sql) | Video original del partido y metadatos de YouTube |
| [002_analysis_jobs.sql](../database/migrations/002_analysis_jobs.sql) | Trabajos de análisis, estados e índice único de trabajos activos |
| [003_player_match_metrics.sql](../database/migrations/003_player_match_metrics.sql) | Métricas, referencias y FK compuesta a participaciones |

El resultado son **20 tablas**, con claves foráneas, restricciones e índices. Los archivos se montan de solo lectura en `/docker-entrypoint-initdb.d`; las carpetas `migrations` y `seeds` no se ejecutan por separado. `init.sql` incluye las migraciones mediante `\ir`, resolviendo rutas relativas al propio archivo. [Referencia de psql](https://www.postgresql.org/docs/17/app-psql.html).

La imagen oficial ejecuta los scripts iniciales únicamente cuando el directorio de datos está vacío. Un volumen existente conserva sus datos y no vuelve a ejecutar `init.sql` después de un pull o reinicio. El healthcheck usa TCP para esperar al servidor definitivo, después de la inicialización. [Documentación de la imagen PostgreSQL](https://hub.docker.com/_/postgres).

## Conexión y comprobación

Con los valores iniciales de `.env.example`:

| Parámetro | Valor |
| --- | --- |
| Host para Java, DBeaver o pgAdmin en tu equipo | `localhost` |
| Puerto | `5432`, configurable con `DB_PORT_EXTERNAL` |
| Base | `semillero`, configurable con `DB_NAME` |
| Usuario | `semillero`, configurable con `DB_USER` |
| Contraseña | La que elegiste en `.env` |
| JDBC | `jdbc:postgresql://localhost:5432/semillero` |

Los siguientes comandos usan usuario y base `semillero`; sustituirlos si los cambiaste:

```bash
docker compose ps
docker compose exec db psql -U semillero -d semillero -c '\dt'
```

Si cambias el puerto o nombre de base, actualizar también `DB_URL` para Java. Dentro de otro contenedor del mismo Compose, PostgreSQL se alcanza como `db:5432`.

## Datos de ejemplo opcionales

El esquema inicial está vacío. Para crear un país, ciudad, dos clubes, dos jugadores y un partido con sus participaciones:

```bash
docker compose exec -T db psql -v ON_ERROR_STOP=1 -U semillero -d semillero -f /docker-entrypoint-initdb.d/seeds/demo.sql
```

[demo.sql](../database/seeds/demo.sql) imprime los IDs generados; no presupone que empiecen en 1. Si los clubes de ejemplo ya existen, rechaza repetir la carga sin añadir duplicados. Los jugadores son sintéticos, sin cuentas de acceso. No se crean videos ni resultados ficticios de análisis.

Para consultar el partido y las participaciones:

```bash
docker compose exec db psql -U semillero -d semillero -c 'SELECT id, fecha_inicio, estado FROM partidos;'
docker compose exec db psql -U semillero -d semillero -c 'SELECT partidos_id, jugadores_id, clubes_id FROM participaciones_partido;'
```

Con ese partido se puede probar la carga de HU01 usando el ID devuelto. YouTube y el analizador permanecen deshabilitados por defecto.

## Arrancar el backend contra el esquema SQL

La plantilla `.env.example` configura `DB_DDL_AUTO=validate`. Así Java comprueba que las tablas coincidan con sus entidades y no depende de `update` para crearlas. El perfil `dev` también respeta esa variable.

En Bash:

```bash
set -a
source .env
set +a
./mvnw clean verify
java -jar semillero-api/target/semillero-api-0.0.1-SNAPSHOT.jar
```

Docker Compose lee `.env` por su cuenta; Java necesita las variables exportadas o configuradas en el IDE. En PowerShell, ejecutar `./mvnw.cmd verify` y configurar las variables del backend en la terminal o en el IDE. Por ejemplo, con los valores locales elegidos:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/semillero'
$env:DB_USER = 'semillero'
$env:DB_PASSWORD = 'tu_contraseña_local'
$env:DB_DDL_AUTO = 'validate'
java -jar semillero-api/target/semillero-api-0.0.1-SNAPSHOT.jar
```

Las pruebas Java de HU01 requieren FFmpeg y ffprobe instalados en el host. La base por sí sola solo necesita Docker con Compose.

## Cuando ya existe una base con datos

**`init.sql` es para una base vacía.** Comprueba que no existan tablas antes de empezar y se detiene si encuentra alguna. No incluye borrados ni reconstruye la base principal.

Para actualizar una base existente, detener el backend, respaldar esa base y aplicar únicamente los archivos pendientes, en orden. Con las carpetas montadas por el Compose actual:

```bash
# Ejemplo: una base que ya tiene 000, 001 y 002 y necesita las métricas de 003.
docker compose exec -T db psql -v ON_ERROR_STOP=1 -U semillero -d semillero -f /docker-entrypoint-initdb.d/migrations/003_player_match_metrics.sql
```

Si el contenedor se creó antes de incorporar este montaje, ejecutar `docker compose up -d --wait db` para actualizar su configuración conservando el volumen. Eso no aplica automáticamente las migraciones sobre datos existentes.

La revisión inicial puede hacerse desde psql:

```sql
SELECT to_regclass('public.pais') AS base,
       to_regclass('public.videos_partido') AS hu01,
       to_regclass('public.analysis_jobs') AS hu02_hu03,
       to_regclass('public.player_match_metrics') AS metricas;

SELECT conname FROM pg_constraint
WHERE conname IN ('chk_analysis_queue', 'fk_pmm_participation');

SELECT indexname FROM pg_indexes
WHERE schemaname = 'public' AND indexname = 'uk_analysis_active_video';
```

- Aplicar 000 únicamente si todavía no existen las tablas base.
- Aplicar 001 si falta `videos_partido`; su SQL requiere que esa tabla no exista.
- Para 002, revisar su [guía](HU02_HU03_ANALISIS.md): una tabla creada por `dev` también necesita sus restricciones e índice parcial.
- Para 003, revisar la [guía de métricas](HU12_MODELO_INTEGRIDAD.md): una tabla creada por `dev` también necesita la FK `fk_pmm_participation`.

Cada migración usa una transacción y se aplica una sola vez. No hay todavía un ejecutor automático ni una tabla de historial; la existencia de una tabla por sí sola no acredita que sus restricciones estén completas. Conservar registro de las migraciones aplicadas al entorno compartido y actualizarlo con cada entrega del equipo.

## Persistencia y pruebas aisladas

`docker compose down` detiene y retira los contenedores, conservando el volumen `db_data`. Al arrancar otra vez se mantienen los datos.

Para probar desde cero sin usar la base que ya tienes, elegir otro nombre de proyecto Compose y un puerto libre. En Bash:

```bash
DB_PORT_EXTERNAL=55432 docker compose -p semillero-pruebas up -d --wait db
DB_PORT_EXTERNAL=55432 docker compose -p semillero-pruebas exec db psql -U semillero -d semillero -c '\dt'
DB_PORT_EXTERNAL=55432 docker compose -p semillero-pruebas down
```

Ese proyecto utiliza su propio volumen. Para conectar Java a él, usar `DB_URL=jdbc:postgresql://localhost:55432/semillero`. Mantener el mismo `-p` y puerto en los comandos de ese entorno.

## Próximas migraciones

Añadir cambios nuevos en otro archivo numerado dentro de `database/migrations`, conservar los archivos ya aplicados y agregar el nuevo `\ir` al final de `init.sql`. Así las instalaciones nuevas reciben todo el esquema y las existentes aplican solo el cambio nuevo.

## Verificación realizada el 2026-10-22

Se probó el Compose en un proyecto y volumen desechables con PostgreSQL 17: creación automática de las 20 tablas, restricciones de análisis/métricas, carga opcional de demo, rechazo de cargas repetidas y persistencia después de reiniciar. El JAR arrancó con perfil `dev` y `DB_DDL_AUTO=validate`, consultó los datos por API y aceptó una carga de video de HU01.

Las 108 pruebas Java pasaron, incluidas las bases PostgreSQL de HU02/HU03 y métricas, cuya prueba ahora verifica también el esquema base 000. Se retiraron los contenedores y el volumen de verificación; no se modificó la base del proyecto.
