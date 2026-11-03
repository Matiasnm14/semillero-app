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

Docker por sí solo deja la base vacía. Al iniciar el backend, Flyway crea las tablas del esquema y `flyway_schema_history`. Los archivos SQL se incluyen dentro del JAR; no depende del directorio desde el que se ejecuta Java. Spring Boot integra [Flyway antes de JPA](https://docs.spring.io/spring-boot/how-to/data-initialization.html#howto.data-initialization.migration-tool.flyway).

## Bases que ya tienen datos

Conservar `.env` y el volumen `db_data`. Después de actualizar el código, arrancar el backend normalmente. La adopción anterior a Flyway ocurre una sola vez:

1. Comprueba las 17 tablas base conocidas y columnas características. Acepta también las tablas opcionales conocidas de videos, análisis, métricas, torneos (V007), roles compartidos (V008), documentos (V013), scouting (V016–V017) y reclutamiento (V018). Un esquema incompleto, ajeno o con tablas adicionales sin historial se detiene para revisión.
2. Registra una base histórica `-1`, que no omite ninguna migración del proyecto.
3. Ejecuta V000–V013 y V015–V018. El SQL conserva tablas y datos existentes y agrega las restricciones e índices ausentes. La 004 añade `respuesta_json` si falta; V007 incorpora torneos, V008 los roles compartidos, V009 los highlights y V010 el perfil de clubes, V011 el contrato HTTP Spark, V012 la procedencia de cargas y espera de YouTube, V013 los documentos del jugador, V015 los checkpoints de Spark, V016–V017 el módulo de scouting y V018 las pruebas, postulaciones, contacto y notificaciones.
4. Valida las entidades y permite arrancar la API.

Esto cubre bases creadas con los SQL anteriores, incluidas instalaciones que ya aplicaron 004, y bases creadas mediante `ddl-auto=update`. Los datos incompatibles con una restricción detienen la migración; no se borran ni se corrigen silenciosamente. La migración fallida se revierte y puede ejecutarse de nuevo tras corregir la causa. Las anteriores que ya terminaron permanecen registradas.

Las versiones V016–V018 consolidan las migraciones de scouting y reclutamiento de esta rama después de reservar V013 para documentos y V015 para checkpoints de Spark. Si una base persistente ya registró las antiguas migraciones de scouting como V013–V015, no desplegar esta secuencia directamente: su historial necesita reconciliación coordinada antes de actualizar el backend.

`baseline-on-migrate` permanece desactivado. La adopción está limitada por la comprobación del esquema; no se reconoce cualquier base como Semillero. Un bloqueo PostgreSQL coordina la adopción y migración si arrancan varias instancias simultáneamente. El historial conserva versiones y checksums: en reinicios posteriores se ejecutan únicamente las versiones pendientes. No hay borrado ni reparación automática del historial.

El usuario de conexión debe poder crear y modificar las tablas del esquema `public`. El usuario creado por Compose ya es propietario de su base. Si una base compartida usa otro propietario, su administrador debe proporcionar los permisos correspondientes. La configuración del proyecto usa `public`.

## Comprobar versiones

Después de arrancar el backend:

```bash
docker compose exec db psql -U semillero -d semillero -c '\dt'
docker compose exec db psql -U semillero -d semillero -c 'SELECT version, description, type, success FROM flyway_schema_history ORDER BY installed_rank;'
```

Las versiones SQL actuales son 000–013 y 015–018: dieciocho entradas SQL en el historial. V014 permanece sin uso. Las bases adoptadas tienen además la entrada `BASELINE` con versión `-1`. En una base nueva no se necesita esa entrada.

| Archivo | Contenido |
| --- | --- |
| [V000__base.sql](../database/migrations/V000__base.sql) | 17 tablas base, partidos y participaciones |
| [V001__videos_partido.sql](../database/migrations/V001__videos_partido.sql) | Originales y metadatos de YouTube |
| [V002__analysis_jobs.sql](../database/migrations/V002__analysis_jobs.sql) | Trabajos, restricciones e índice parcial de análisis activos |
| [V003__player_match_metrics.sql](../database/migrations/V003__player_match_metrics.sql) | Métricas y FK compuesta a participaciones |
| [V004__analysis_future_response.sql](../database/migrations/V004__analysis_future_response.sql) | JSON final y validación de su estructura |
| [V005__profile_and_club_images.sql](../database/migrations/V005__profile_and_club_images.sql) | Fotos de perfil y clubes como texto |
| [V006__normalize_image_text_storage.sql](../database/migrations/V006__normalize_image_text_storage.sql) | Convierte las fotos OID creadas por Hibernate a texto conservando su contenido |
| [V007__torneos.sql](../database/migrations/V007__torneos.sql) | Tablas de torneos y columnas compartidas de partidos, participaciones y jugadores |
| [V008__roles_y_usuarios_roles.sql](../database/migrations/V008__roles_y_usuarios_roles.sql) | Roles compartidos y asignaciones a usuarios |
| [V009__highlights_and_text_profile_images.sql](../database/migrations/V009__highlights_and_text_profile_images.sql) | Publicación de highlights y columnas de imágenes de texto |
| [V010__club_profile.sql](../database/migrations/V010__club_profile.sql) | Año de fundación y descripción del club |
| [V011__spark_analysis_contract.sql](../database/migrations/V011__spark_analysis_contract.sql) | Identidad remota Spark, progreso en segundos y limpieza posterior al commit |
| [V012__persistent_video_uploads.sql](../database/migrations/V012__persistent_video_uploads.sql) | Usuario de carga y publicación en YouTube después del análisis |
| [V013__documentos_jugador.sql](../database/migrations/V013__documentos_jugador.sql) | Documentos y apto médico del jugador |
| [V015__spark_result_checkpoints.sql](../database/migrations/V015__spark_result_checkpoints.sql) | JSON durante la descarga de artefactos y estado persistido del mapa |
| [V016__scouting_module.sql](../database/migrations/V016__scouting_module.sql) | Módulo de scouting |
| [V017__scouting_assignment_and_export_controls.sql](../database/migrations/V017__scouting_assignment_and_export_controls.sql) | Criterios de asignación y controles de exportación |
| [V018__club_recruitment_and_notifications.sql](../database/migrations/V018__club_recruitment_and_notifications.sql) | Pruebas, postulaciones, contacto y notificaciones |

Los nombres siguen `V<versión>__<descripción>.sql`. La fuente única es `database/migrations`; Maven la empaqueta en `db/migration`. No editar archivos ya registrados por Flyway: un checksum distinto detiene el arranque. Para un cambio nuevo añadir una versión nueva posterior a V018 y reconstruir el JAR. No se necesita ejecutar SQL manual. Si una instalación anterior sin historial ya creó tablas nuevas mediante Hibernate, revisar también la lista explícita de tablas reconocidas en `DatabaseMigrationConfiguration`; nunca aceptar nombres ajenos automáticamente.

Las entidades de fotos usan `String` sobre `TEXT`. La V006 también cubre instalaciones donde `@Lob` creó columnas OID: lee el contenido del objeto grande y conserva los valores nulos. No modifica la V005 ya distribuida ni elimina objetos grandes originales, que pueden tener otras referencias. Si un OID no puede leerse, la migración falla y se revierte; no sustituye fotos por el identificador numérico. Ver la [explicación de Hibernate sobre TEXT y LOB](https://docs.hibernate.org/orm/7.1/introduction/html_single/#lobs).

## Datos de ejemplo opcionales

Con el esquema ya migrado:

```bash
docker compose exec -T db psql -v ON_ERROR_STOP=1 -U semillero -d semillero -f /opt/semillero/database/seeds/demo.sql
```

[demo.sql](../database/seeds/demo.sql) crea un país, ciudad, dos clubes, jugadores sintéticos y un partido con sus participaciones. Imprime los IDs generados y rechaza repetir la carga. No crea resultados de análisis ficticios.

Los archivos se montan de solo lectura en `/opt/semillero/database`, fuera de la inicialización automática de PostgreSQL. [init.sql](../database/init.sql) queda como utilidad manual para construir una base vacía; el flujo normal usa exclusivamente Flyway desde Spring. Si ya se utilizó esa utilidad, el siguiente arranque adopta el esquema.

## Analizador y YouTube

Migrar la base no necesita acceso a Google ni al servicio de análisis. Los CRUD arrancan con las integraciones deshabilitadas. Las [cargas persistentes](CARGAS_PERSISTENTES_Y_PERMISOS.md) requieren JWT, acceso al partido, almacenamiento y FFmpeg; pueden quedar pendientes con Spark deshabilitada. El worker necesita configurar y habilitar Spark para procesarlas. YouTube sigue siendo opcional.

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

Para ejecutar también las pruebas PostgreSQL de análisis y métricas, preparar las bases
`analysis_hu0203_test` (propietario `analysis_test`) y `metrics_hu12_test` (propietario
`metrics_test`) en el mismo PostgreSQL desechable, con acceso local sin contraseña para esos
usuarios de prueba, y habilitar las tres variables:

```bash
MIGRATIONS_PG_TEST_URL=jdbc:postgresql://127.0.0.1:55437/migrations_test \
ANALYSIS_PG_TEST_URL=jdbc:postgresql://127.0.0.1:55437/analysis_hu0203_test \
METRICS_PG_TEST_URL=jdbc:postgresql://127.0.0.1:55437/metrics_hu12_test \
./mvnw clean verify
```

Sin sus variables respectivas, esos casos PostgreSQL se omiten; la suite normal con H2 no
verifica el SQL de las migraciones. Los 18 casos de migraciones PostgreSQL deben ejecutarse antes de subir
cambios en migraciones o en la integración con la base.

Estas pruebas recrean exclusivamente ese esquema de prueba. Cubren instalación vacía, reinicio, adopción de SQL previos e Hibernate con torneos y roles existentes, conservación de datos y JSON, rechazo de esquemas ajenos (también si contienen las tablas base), rollback, arranques concurrentes y validación de checksums, actualización desde V013 y preservación de checkpoints al adoptar un esquema ya inicializado. Las copias antiguas de SQL bajo `src/test/resources/db/legacy` son fixtures históricos; no se distribuyen como migraciones de producción.

## Verificación del 2026-10-25

`clean verify` completó **133 pruebas sin fallos, errores ni omisiones**, con las tres bases PostgreSQL desechables habilitadas mediante `MIGRATIONS_PG_TEST_URL`, `ANALYSIS_PG_TEST_URL` y `METRICS_PG_TEST_URL`. Incluye 10 casos de migraciones automáticas y las pruebas existentes de CRUD, videos, análisis y métricas. Se comprobó también la configuración de Compose y la inclusión de V000–V004 y Flyway en el JAR ejecutable. La base del proyecto no se modificó.

Después de integrar los cambios de `main` de fotos y autenticación, `clean verify` completó **135 pruebas sin fallos, errores ni omisiones**, con las tres bases PostgreSQL habilitadas. Incluye conservación y lectura JPA de fotos TEXT, conversión de fotos OID con Unicode y valores nulos mediante V006, escritura posterior y persistencia tras reiniciar. Las pruebas de análisis y métricas aplican las migraciones actuales al validar sus esquemas históricos.

## Verificación del 2026-10-28

Después de integrar torneos, `clean verify` completó **328 pruebas sin fallos, errores ni
omisiones**, con las tres bases PostgreSQL temporales habilitadas: 13 casos de migraciones,
uno de análisis y uno de métricas. La adopción de un esquema Hibernate conserva torneos,
usuarios y roles existentes; una tabla ajena sigue impidiendo crear el historial. Los SQL
V000–V008 y sus checksums no se modificaron.

El JAR se probó además mediante 27 comprobaciones HTTP contra otra base temporal: registro
público, rechazo de asignaciones de privilegios sin ADMIN y de cambios de contraseña ajenos,
cambio de contraseña propia, asignación legítima por ADMIN, preflight CORS y errores 401/403
con cabeceras para Flutter Web local. No se modificó la base del proyecto ni se contactó a
YouTube o al analizador externo.
