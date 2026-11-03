# Semillero API

API REST para la gestión de clubes de fútbol, fichas de jugadores, scouting, torneos y análisis de video. El backend principal utiliza **Java 21, Spring Boot 4.1.1 y PostgreSQL 17**. El análisis se ejecuta en un servicio externo sobre DGX Spark; la publicación en YouTube es opcional.

Esta guía permite preparar una instalación local, obtener un token, consumir los recursos y habilitar las integraciones. Los contratos de entrada y salida están definidos por los DTO del repositorio y se exponen mediante OpenAPI.

## Contenido

- [Alcance y arquitectura](#alcance-y-arquitectura)
- [Requisitos](#requisitos)
- [Arranque local](#arranque-local)
- [Configuración](#configuración)
- [Base de datos y Flyway](#base-de-datos-y-flyway)
- [Autenticación y primer administrador](#autenticación-y-primer-administrador)
- [Permisos y CORS](#permisos-y-cors)
- [Convenciones HTTP](#convenciones-http)
- [Catálogo de recursos](#catálogo-de-recursos)
- [Primeras peticiones de negocio](#primeras-peticiones-de-negocio)
- [Análisis de video y YouTube](#análisis-de-video-y-youtube)
- [Pruebas](#pruebas)
- [Despliegue en el VPS](#despliegue-en-el-vps)
- [Solución de problemas](#solución-de-problemas)
- [Guía para modificar el código](#guía-para-modificar-el-código)
- [Documentación relacionada](#documentación-relacionada)

## Alcance y arquitectura

| Área | Funcionalidad |
| --- | --- |
| Catálogos y clubes | Países, ciudades, clubes, personal y posiciones. |
| Ficha técnica | Usuarios, jugadores, fichajes y documentos del jugador. |
| Scouting | Prospectos, asignaciones, evaluaciones, etapas, pruebas, postulaciones, contacto y notificaciones. |
| Partidos | Clubes participantes, jugadores, dorsales y estadísticas registradas. |
| Torneos | Categorías, reglamentos, inscripciones, lista de buena fe, fixture, planillas, eventos, resultados, sanciones, estadísticas y PDF. |
| Video de partidos | Recepción MP4, almacenamiento, cola persistente, análisis Spark, JSON, mapa de calor y publicación opcional en YouTube. |

El backend se compila como un proyecto Maven con tres módulos:

```text
semillero-api → core → data
```

| Directorio | Contenido |
| --- | --- |
| `semillero-api/` | Aplicación Spring Boot, controladores REST, seguridad HTTP, OpenAPI y perfiles. |
| `core/` | Servicios de negocio, validaciones, autorización de dominio, workers y adaptadores externos. |
| `data/` | Entidades JPA, repositorios, DTO de petición/respuesta y enumeraciones. |
| `database/migrations/` | Fuente de las migraciones SQL que Maven incluye dentro del JAR. |

La [API de scouting](docs/scouting/API.md) implementa prospectos, asignaciones, criterios, observaciones, etapas, consentimientos, comunicaciones, exportación y enlace con la ficha técnica. Las pruebas, postulaciones y notificaciones de clubes están descritas en la [API de reclutamiento](docs/reclutamiento/API.md).
| `database/seeds/` | Datos de demostración opcionales. |
| `scripts/` | Uploader de YouTube y herramientas de pruebas de integración. |
| `docs/` | Contratos, arquitectura, guías y evidencia documentada. |
| `VPS-Implementacion/` | Imagen, Compose y procedimientos del despliegue compartido. |

Los módulos Java se ejecutan dentro de **un mismo backend**. Spark es otro servicio: recibe archivos por HTTP y utiliza su propio almacenamiento temporal y GPU. No comparte el directorio de videos ni necesita conectarse a PostgreSQL. El contrato vigente no requiere Couchbase. El frontend Flutter se desarrolla por separado.

## Requisitos

| Requisito | Cuándo se necesita |
| --- | --- |
| JDK 21 y `JAVA_HOME` coherente | Compilar y ejecutar el backend localmente. |
| Git | Obtener y actualizar el repositorio. |
| Docker Engine/Desktop y Compose v2 | Levantar el PostgreSQL local con la configuración incluida. También se admite una instalación propia de PostgreSQL 17. |
| Acceso a Maven Central | Primera compilación y descarga de dependencias; se incluye Maven Wrapper. |
| FFmpeg y FFprobe en `PATH` | Cargar/validar videos y ejecutar las pruebas que procesan archivos reales. Los CRUD generales no ejecutan estas herramientas. |
| Directorios de datos escribibles y espacio en disco | Conservar originales, temporales y mapas de calor. |
| Acceso HTTPS al servicio Spark y su token | Ejecutar análisis; no es necesario para los CRUD ni para migrar la base. |
| Python 3.10 o posterior, `venv` y dependencias de YouTube | Publicar videos desde una instalación local. El uploader utiliza `fcntl`: usar Linux/macOS o WSL para esa integración. |
| Cliente OAuth, autorización del propietario y acceso al canal | Habilitar la publicación en YouTube. |

No hace falta instalar Maven globalmente ni disponer de GPU en la computadora que ejecuta Spring. Docker Compose en la raíz levanta **solo PostgreSQL**; el backend se inicia por separado.

Verificar las herramientas instaladas:

```bash
java -version
./mvnw -version
docker compose version
ffmpeg -version
ffprobe -version
```

En Windows utilizar `.\mvnw.cmd` en lugar de `./mvnw`. Si el wrapper no tiene permiso de ejecución en Linux/macOS, usar `bash ./mvnw`.

## Arranque local

Ejecutar desde la raíz del repositorio. Los comandos siguientes usan Bash; en PowerShell se puede seguir el mismo orden con `Copy-Item`, `.\mvnw.cmd` y `curl.exe`.

### 1. Preparar la configuración

Crear el archivo solo si todavía no existe:

```bash
cp .env.example .env
```

Editar al menos la contraseña de la base. Mantener estos valores coherentes:

```dotenv
DB_NAME=semillero
DB_USER=semillero
DB_PASSWORD=<contraseña-local>
DB_PORT_EXTERNAL=5432
DB_URL=jdbc:postgresql://localhost:5432/semillero
SPRING_PROFILES_ACTIVE=dev
DB_DDL_AUTO=validate
ANALYSIS_ENABLED=false
VIDEO_YOUTUBE_ENABLED=false
```

Reemplazar `<contraseña-local>` por un valor propio. Si se cambia el nombre de la base o el puerto externo, cambiar también `DB_URL`. Conservar el `.env` existente al actualizar el proyecto.

**La aplicación importa `.env` automáticamente al ejecutarse desde la raíz**, mediante `spring.config.import=optional:file:.env[.properties]`. Compose también lo lee. No hace falta ejecutar `source .env` para este arranque. Las variables del proceso tienen prioridad sobre el archivo; una variable antigua del IDE o terminal puede sobrescribirlo.

El archivo debe usar sintaxis `CLAVE=valor`, sin `export`. Si se inicia el JAR desde otro directorio, configurar las variables en el entorno/IDE o indicar explícitamente la ubicación del archivo. El perfil `vps` desactiva esta importación local.

### 2. Levantar PostgreSQL

```bash
docker compose up -d --wait db
docker compose ps
```

La imagen es `postgres:17`; el puerto se publica únicamente en `127.0.0.1`. El volumen `db_data` conserva los datos.

Con PostgreSQL instalado fuera de Docker, crear una base y un usuario con permisos para gestionar su esquema local y ajustar `DB_URL`, `DB_USER` y `DB_PASSWORD`. No hace falta ejecutar Compose en ese caso.

### 3. Compilar y verificar

```bash
./mvnw --batch-mode --no-transfer-progress clean verify
```

La suite normal usa una base H2 desechable; las pruebas PostgreSQL requieren activación adicional, explicada más abajo. FFmpeg/FFprobe deben estar disponibles para las pruebas de video.

### 4. Iniciar el backend

```bash
java -jar semillero-api/target/semillero-api-0.0.1-SNAPSHOT.jar
```

Esperar el mensaje `Started SemilleroApplication`. En el perfil local, Flyway aplica las migraciones antes de que Hibernate valide el esquema.

Para ejecutar desde el IDE: importar el proyecto Maven raíz, seleccionar JDK 21, usar `edu.upb.semillero.SemilleroApplication`, directorio de trabajo en la raíz y perfil `dev`.

Para cambiar el puerto en una ejecución:

```bash
java -jar semillero-api/target/semillero-api-0.0.1-SNAPSHOT.jar --server.port=8081
```

### 5. Comprobar la API y abrir su documentación

```bash
curl --fail-with-body 'http://localhost:8080/api/v1/paises?page=0&size=1'
```

| Entrada local | URL |
| --- | --- |
| Base REST | `http://localhost:8080/api/v1` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |

Swagger y OpenAPI están permitidos sin sesión. Para operaciones protegidas, usar un JWT del login. El indicador de seguridad generado se añade expresamente a las operaciones de torneos; la ausencia de un candado en otra ruta no implica que esa ruta sea pública. Los ejemplos `curl` permiten enviar el encabezado explícitamente.

El perfil `dev` crea catálogos mínimos cuando faltan: país, ciudades y posiciones. No crea automáticamente un administrador con contraseña conocida.

## Configuración

### Variables generales

| Variable | Predeterminado / función |
| --- | --- |
| `DB_PASSWORD` | Requerida para conectar a PostgreSQL. |
| `DB_USER` / `DB_NAME` | `semillero`; `DB_NAME` configura la base creada por Compose. |
| `DB_PORT_EXTERNAL` | `5432`; puerto local del contenedor PostgreSQL. |
| `DB_URL` | `jdbc:postgresql://localhost:5432/semillero`; no cambia automáticamente al cambiar las variables de Compose. |
| `SPRING_PROFILES_ACTIVE` | Perfil activo; si no se indica, Spring usa `dev` como predeterminado. |
| `DB_DDL_AUTO` | `validate` en local; Flyway gestiona los cambios de esquema. |
| `PORT` | `8080` en local; el perfil VPS fija `18083`. |
| `JWT_SECRET` | Clave de firma privada. Hay un valor exclusivo de desarrollo; `vps` exige uno explícito y estable. |
| `JWT_DURACION_MINUTOS` | `480` minutos para el JWT. |
| `PUBLIC_WEB_ORIGIN` / `CORS_ALLOWED_ORIGINS` | Origen web principal y adicionales en el perfil VPS. |

No confundir `JWT_SECRET`, el JWT de usuario obtenido en el login, el token de Spark y los tokens OAuth de Google: cumplen funciones diferentes. Las claves de servidor y los archivos OAuth no se entregan al frontend ni se incluyen en Git.

### Variables de video y Spark

| Variable | Predeterminado / función |
| --- | --- |
| `VIDEO_STORAGE_DIR` | `.local/videos`; originales y recibos de publicación. |
| `VIDEO_MAX_BYTES` | `2147483648` bytes, 2 GiB. |
| `VIDEO_MAX_FILE_SIZE` / `VIDEO_MAX_REQUEST_SIZE` | `2GB` / `2049MB`; límites multipart del archivo y de la petición completa. |
| `VIDEO_FFMPEG` / `VIDEO_FFPROBE` | `ffmpeg` / `ffprobe`; nombre o ruta del ejecutable. |
| `VIDEO_VALIDATION_TIMEOUT` | `1h`; límite de validación local. |
| `ANALYSIS_ENABLED` | `false`; permite despachar a Spark al habilitarse. |
| `ANALYSIS_WORKER_ENABLED` | `true` en local y `false` por defecto en VPS; controla el scheduler. |
| `ANALYSIS_BASE_URL` | URL accesible del servicio Spark; necesaria al habilitarlo. |
| `ANALYSIS_EVALUATION_PATH` | `/analyses`. |
| `ANALYSIS_TOKEN` | Bearer privado exigido por el servicio Spark. |
| `ANALYSIS_READ_JERSEYS` | `true`; solicita lectura de candidatos de dorsal. |
| `ANALYSIS_CONNECT_TIMEOUT` | `10s`; establecimiento de conexión. |
| `ANALYSIS_REQUEST_TIMEOUT` | `2h`; límite de cada petición HTTP, incluida la transferencia. |
| `ANALYSIS_MAX_DURATION` | `6h`; presupuesto total desde la creación del trabajo, incluida la espera en cola. |
| `ANALYSIS_POLL_INTERVAL` | `5s`; consultas y pausa para reintentar un `GPU_BUSY`. |
| `ANALYSIS_LEASE_DURATION` | `30s`; reserva renovable del trabajo; no es el tiempo máximo de inferencia. |
| `ANALYSIS_MAX_CONCURRENT_JOBS` | `1`; ejecuciones locales simultáneas. Spark también limita la concurrencia. |
| `ANALYSIS_MAX_RESPONSE_BYTES` | `1048576` en local; Compose VPS usa `16777216` por defecto. |
| `ANALYSIS_ARTIFACTS_DIR` | `.local/analysis`; PNG conservados por el backend. |
| `ANALYSIS_MAX_HEATMAP_BYTES` | `10485760` bytes, 10 MiB; también se validan dimensiones y decodificación. |

`ANALYSIS_ENABLED=false` permite guardar cargas válidas con su análisis pendiente. La ejecución sigue sujeta al plazo desde la solicitud; no es una cola con retención indefinida. Cambiar los límites de archivo requiere coordinar también multipart, proxy y capacidad de disco.

## Base de datos y Flyway

La fuente del esquema es [database/migrations](database/migrations). Maven empaqueta los SQL dentro del JAR en `db/migration`. En local, el arranque ejecuta las migraciones pendientes y después valida las entidades. Compose crea PostgreSQL y su base, pero no ejecuta por sí solo las migraciones de negocio.

Las versiones actuales son **V000–V013 y V015–V018**. V014 permanece sin uso.

| Migraciones | Contenido principal |
| --- | --- |
| V000 | Tablas base, partidos y participaciones. |
| V001–V004 | Videos de partidos, trabajos, métricas y respuesta JSON. |
| V005–V006 | Imágenes como texto y normalización de instalaciones anteriores con OID. |
| V007–V008 | Torneos y roles compartidos. |
| V009–V010 | Highlights y perfil de clubes. |
| V011–V012 | Identidad/progreso remoto y cargas persistentes con espera de YouTube. |
| V013 | Documentos del jugador. |
| V015 | Checkpoints del JSON y estado del mapa de calor. |
| V016–V017 | Módulo de scouting, asignaciones y controles de exportación. |
| V018 | Pruebas, postulaciones, contacto y notificaciones. |

`flyway_schema_history` registra versiones y checksums. Para una columna nueva, añadir otra migración y reconstruir el JAR; no editar una migración ya aplicada. Si falta una columna de V005 u otra versión, revisar el historial, la base a la que apunta `DB_URL` y la versión del artefacto ejecutado.

El proyecto incluye adopción controlada de esquemas anteriores conocidos. Un esquema ajeno o incompleto se detiene para revisión. No reparar esos casos borrando el historial o activando `ddl-auto=update`.

Comprobar las migraciones en la base local, adaptando usuario y nombre si se cambiaron:

```bash
docker compose exec db psql -U semillero -d semillero -c 'SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;'
```

Después del primer arranque se pueden cargar datos sintéticos opcionales:

```bash
docker compose exec -T db psql -v ON_ERROR_STOP=1 -U semillero -d semillero -f /opt/semillero/database/seeds/demo.sql
```

El seed imprime los IDs creados, rechaza repetir la carga y no inventa resultados del analizador. No sustituye la preparación del administrador.

`docker compose down` conserva los datos. No usar `down -v` como procedimiento de actualización: elimina el volumen de PostgreSQL. La guía [BASE_DATOS_DOCKER.md](docs/BASE_DATOS_DOCKER.md) explica adopción, permisos, migraciones y diagnóstico; para la importación de `.env` prevalece la configuración actual descrita en este README.

## Autenticación y primer administrador

### Registrar la cuenta

El registro es público. Ejemplo exclusivo para una base local de desarrollo:

```bash
curl --fail-with-body -i http://localhost:8080/api/v1/usuarios \
  -H 'Content-Type: application/json' \
  -d '{"name":"admin-local","nombres":"Admin","apellidos":"Local","email":"admin-local@example.com","password":"clave-local-123"}'
```

La contraseña es obligatoria al crear y admite de 8 a 128 caracteres. Se almacena como hash PBKDF2; las respuestas no devuelven contraseña ni hash. Registrar una cuenta no le asigna por sí solo un perfil o rol para iniciar sesión.

### Asignar el primer ADMIN

Los endpoints de asignación ya requieren ADMIN. La primera cuenta se habilita una sola vez mediante una conexión autorizada a la base. Con el ejemplo local anterior y los valores predeterminados de Compose:

```bash
docker compose exec db psql -v ON_ERROR_STOP=1 -U semillero -d semillero -c "INSERT INTO usuarios_roles (users_id, roles_id) SELECT u.id, r.id FROM users u CROSS JOIN roles r WHERE u.email = 'admin-local@example.com' AND r.codigo = 'ADMIN' ON CONFLICT DO NOTHING;"
```

La migración V008 incluye los roles. Comprobar que la cuenta y el rol existan si la inserción afecta cero filas. En un entorno compartido esta operación corresponde al administrador de esa base; no se usa una cuenta de demostración como credencial de producción.

### Iniciar sesión y usar el token

El campo se llama **`identificador`** y acepta email o nombre de usuario:

```bash
curl --fail-with-body http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"identificador":"admin-local@example.com","password":"clave-local-123"}'
```

La respuesta incluye datos del usuario, el perfil `rol`, la lista de permisos `roles` y `token`. Copiar el valor de `token` para los ejemplos siguientes:

```bash
JWT='<token-devuelto-por-login>'
curl --fail-with-body http://localhost:8080/api/v1/torneos \
  -H "Authorization: Bearer $JWT"
```

Los perfiles de jugador o de usuario de club también permiten login cuando sus relaciones están completas. Una cuenta sin perfil ni roles recibe un error de negocio. Las credenciales incorrectas también se notifican actualmente como error de negocio `400`; las rutas protegidas sin JWT válido devuelven `401`.

La autenticación es stateless. No existe un endpoint de refresh en este contrato: al vencer el token, volver a iniciar sesión. Conservar el secreto de firma entre reinicios para seguir aceptando tokens vigentes.

Un ADMIN puede consultar y reemplazar roles por API:

```http
GET /api/v1/roles
GET /api/v1/usuarios/{usuarioId}/roles
PUT /api/v1/usuarios/{usuarioId}/roles
Content-Type: application/json
Authorization: Bearer <JWT de ADMIN>

["ORGANIZADOR", "ENTRENADOR"]
```

El PUT reemplaza el conjunto de roles; no es una operación de añadir uno sin conservar los anteriores.

## Permisos y CORS

Los roles compartidos son `ADMIN`, `ORGANIZADOR`, `ENTRENADOR`, `MESA_CONTROL` y `CONSULTA`. Además del rol, algunos servicios verifican propiedad o pertenencia al club/partido.

| Operación | Acceso actual |
| --- | --- |
| Registro y login | Públicos. |
| Administración de administradores y roles | ADMIN. |
| Alta de `/usuarios-club` y `/usuarios-trabaja` | Pública en la integración actual; sus GET también son públicos. PUT/DELETE requieren ADMIN. |
| Modificar usuario o ficha de jugador | Titular, ADMIN o personal del club autorizado sobre un jugador fichado, según la operación. |
| Eliminar cuenta de usuario | Titular o ADMIN. |
| Escribir partidos, participaciones y fichajes compartidos | ADMIN u ORGANIZADOR. |
| Consultar torneos, comparador y estadísticas bajo `/torneos` | Usuario autenticado. |
| Gestionar torneo, fixture, reaperturas y sanciones | ORGANIZADOR o ADMIN, con las comprobaciones del servicio. |
| Inscribir equipos, lista de buena fe y apelar sanciones | También ENTRENADOR, con validaciones de propiedad. |
| Planillas, eventos y cierre del partido | También MESA_CONTROL, con validaciones de asignación. |
| Documentos del jugador | Sesión y permisos por jugador; el servicio controla lectura, gestión y aprobación. |
| Scouting | Roles de scouting; las identidades OIDC se administran con ADMIN. |
| Solicitudes de contacto, pruebas y notificaciones | Usuario autenticado; los servicios validan la propiedad de los recursos. |
| Gestionar highlights o consultar ocultos | JWT y autorización sobre el jugador. El listado visible es público. |

Para videos de partidos, JSON y PNG:

| Usuario | Lectura | Carga, análisis manual y reintento YouTube |
| --- | --- | --- |
| ADMIN / ORGANIZADOR | Todos los partidos. | Todos los partidos. |
| ENTRENADOR | Partidos de clubes donde trabaja. | Los mismos. |
| MESA_CONTROL | Partidos asignados. | Los mismos. |
| Jugador participante | Su partido. | No. |
| CONSULTA sin otro vínculo autorizado | No. | No. |

Los demás CRUD que no tienen reglas específicas conservan el acceso público actual. No asumir que toda la API exige JWT ni que obtener un token habilita cualquier recurso. La referencia de seguridad es [SeguridadConfiguration](semillero-api/src/main/java/edu/upb/semillero/config/SeguridadConfiguration.java) y las validaciones de cada servicio.

CORS permite en local `http://localhost:<puerto>` y `http://127.0.0.1:<puerto>`. El perfil `vps` configura el origen público mediante `PUBLIC_WEB_ORIGIN` y los adicionales mediante `CORS_ALLOWED_ORIGINS`. El preflight no necesita JWT; la petición real conserva sus permisos. CORS no concede autorización sobre recursos.

## Convenciones HTTP

- Prefijo de recursos: `/api/v1`.
- JSON con nombres `camelCase`; los campos y enumeraciones válidos están en los DTO y en OpenAPI. Los campos JSON desconocidos se rechazan.
- IDs generados por la base; en los CRUD de entidad no se envía `id` al crear. Las relaciones se expresan mediante `paisId`, `usuarioId`, `jugadorId`, `clubId`, `partidoId`, etc.
- No intercambiar IDs de usuario, jugador, participación, video y análisis: identifican entidades diferentes.
- Fechas locales: `yyyy-MM-dd`; horas: `HH:mm:ss`; `LocalDateTime`: `yyyy-MM-dd'T'HH:mm:ss`. Los campos `Instant`, como fechas del trabajo de análisis, se serializan con referencia UTC. Consultar el tipo del DTO antes de construir fechas.
- Un `null` de métricas significa dato no disponible; no sustituirlo por cero.

### CRUD y paginación

Los recursos CRUD convencionales ofrecen:

| Método | Ruta relativa | Respuesta habitual |
| --- | --- | --- |
| GET | `/{recurso}?page=0&size=20&sort=id,asc` | `200`, página. |
| GET | `/{recurso}/{id}` | `200`, objeto. |
| POST | `/{recurso}` | `201`, objeto y `Location`. |
| PUT | `/{recurso}/{id}` | `200`, objeto actualizado. |
| DELETE | `/{recurso}/{id}` | `204`, sin cuerpo. |

La paginación comienza en cero y admite hasta 100 elementos por página. Se pueden repetir parámetros `sort`. Los filtros son específicos de cada recurso; no asumir que todos aceptan un mismo campo de búsqueda.

```json
{
  "content": [{"id": 1, "nombrePais": "Bolivia"}],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

PUT no es PATCH: los servicios CRUD asignan los campos editables desde el DTO y muchos opcionales omitidos quedan en `null`. Hay excepciones explícitas: omitir la contraseña al actualizar un usuario conserva su hash. Las acciones de torneo, highlights y video tienen sus propios contratos y no siguen necesariamente la tabla CRUD.

### Errores

La API utiliza `application/problem+json` y `ProblemDetail`. Ejemplo ilustrativo:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Revisa los campos de la solicitud",
  "instance": "/api/v1/partidos",
  "errors": {"clubLocalId": "no debe ser nulo"}
}
```

`errors` aparece en validaciones de campos, no en todas las respuestas.

| Código | Significado habitual |
| --- | --- |
| `400` | JSON, parámetros o reglas de negocio inválidos. |
| `401` | Falta JWT válido en una ruta protegida. |
| `403` | Falta permiso o pertenencia al recurso. |
| `404` | Registro, relación o mapa no disponible. |
| `409` | Duplicado, dependencia que impide borrar, análisis ya existente o resultado aún pendiente. |
| `413` | Archivo excede el máximo permitido. |
| `503` | Almacenamiento, herramientas o servicio requerido no disponibles. |

Una falla de Spark **posterior al 202 de carga** se consulta en el trabajo persistido: no se convierte retroactivamente en un error de aquella petición HTTP.

## Catálogo de recursos

Las rutas de las tablas siguientes se añaden a `/api/v1`. OpenAPI describe los esquemas completos; la tabla de permisos anterior sigue aplicando.

### Recursos compartidos

| Recurso | Ruta |
| --- | --- |
| Países y ciudades | `/paises`, `/ciudades` |
| Clubes | `/clubes` |
| Usuarios y administradores | `/usuarios`, `/administradores` |
| Cuenta y trabajo en club | `/usuarios-club`, `/usuarios-trabaja` |
| Jugadores y posiciones | `/jugadores`, `/posiciones`, `/posiciones-jugador` |
| Fichajes | `/fichados` |
| Pruebas y clínicas | `/pruebas`, `/clinicas-pago` |
| Videos de jugador y conversaciones | `/videos`, `/conversaciones` |
| Partidos y participaciones | `/partidos`, `/participaciones-partido` |
| Documentos del jugador | `/documentos-jugador` |

El CRUD `/videos` administra videos de jugador. La subida de archivos para análisis utiliza `/partidos/{partidoId}/videos`; son contratos distintos.

### Torneos

| Área | Rutas principales |
| --- | --- |
| Torneos y categorías | `/torneos`, `/torneos/{torneoId}/categorias` |
| Apertura de inscripciones | `POST /torneos/{id}/abrir-inscripciones` |
| Reglamento y desempates | `/torneos/{torneoId}/reglamento`, `/torneos/criterios-desempate` |
| Sedes y canchas | `/torneos/sedes`, `/torneos/canchas` |
| Inscripciones y estados | `/torneos/inscripciones`, `/torneos/inscripciones/{id}/estado` |
| Lista de buena fe | `/torneos/inscripciones/{inscripcionEquipoId}/lista-buena-fe`; confirmación y PDF. |
| Fixture por categoría | `/torneos/{torneoId}/categorias/{categoriaId}/fixture`; generación de liga/eliminación, avance, partidos manuales y PDF. |
| Publicación del fixture | `POST /torneos/{torneoId}/fixture/publicar` |
| Operación del partido | `/torneos/partidos/{partidoId}/planilla`, `/eventos`, `/resultado`, `/cerrar`, `/reabrir`, `/historial`, `/reprogramaciones`, bajo ese mismo partido. |
| Posiciones y estadísticas | `/torneos/{torneoId}/categorias/{categoriaId}/tabla` y `/estadisticas`, bajo esa misma categoría. |
| Sanciones y apelaciones | `/torneos/{torneoId}/sanciones`, `/torneos/sanciones/{id}/apelaciones`, `/torneos/apelaciones/{id}/resolucion` |
| Consultas propias | `/torneos/mi-cuenta`, `/torneos/notificaciones` |
| Partidos cerrados | `/torneos/partidos/finalizados?desde=...` |

Para inscribir jugadores en lista de buena fe se validan documentos y apto médico vigente. El aviso opcional `PartidoFinalizado` se configura con `TORNEOS_PARTIDO_FINALIZADO_URL` y `TORNEOS_PARTIDO_FINALIZADO_TOKEN`; vacío no envía aviso. El análisis actual se inicia al cargar el video y no depende de ese aviso.

El [contexto de torneos](docs/torneos/CONTEXTO_TORNEOS.md), sus DTO y la colección [Postman](docs/torneos/postman/semillero-torneos-sprint1.postman_collection.json) contienen el detalle de acciones. Algunos procedimientos antiguos de esa carpeta corresponden a versiones previas; para `.env`, permisos, migraciones y conservación de datos usar este README y las guías vigentes.

### Comparador, métricas y highlights

| Método | Ruta | Función |
| --- | --- | --- |
| GET | `/torneos/jugadores/{jugadorId}/estadisticas?temporada=2026` | Estadísticas deportivas del jugador; admite también `torneoId`. |
| GET | `/torneos/jugadores/comparacion?jugadorAId=1&jugadorBId=2&temporada=2026` | Comparación desde partidos y eventos registrados. |
| GET | `/participaciones-partido?jugadorId=1&partidoId=1&clubId=1` | Filtros opcionales combinados, con respuesta paginada. |
| GET | `/metricas?jugadorId=1` | Métricas físicas persistidas marcadas como vigentes. |
| GET | `/metricas?partidoId=1&clubId=1` | Métricas físicas vigentes del partido y club. |
| GET | `/highlights?jugadorId=1` | Highlights visibles; `incluirOcultos=true` requiere autorización. |
| POST | `/highlights` | Crear highlight del jugador autorizado. |
| PUT / PATCH / DELETE | `/highlights/{id}` | Actualizar URL, modificar visibilidad o eliminar, respectivamente. |

El comparador usa participaciones y eventos; no depende del modelo GPU. `/metricas` consulta registros existentes: la integración Spark todavía no publica automáticamente esos registros ni resuelve identidades reales. Ver [contrato del comparador](docs/COMPARADOR_JUGADORES.md).

## Primeras peticiones de negocio

Preparar el ADMIN y la variable `JWT` como se indicó arriba. El orden habitual es países → ciudades → clubes; usuarios → jugadores/personal; después fichajes, partidos y participaciones.

Consultar los IDs reales: el seed de desarrollo u otras cargas pueden haber ocupado los primeros valores.

```bash
curl --fail-with-body 'http://localhost:8080/api/v1/ciudades?page=0&size=20'
```

Crear dos clubes, sustituyendo `ciudadId` por un ID existente y guardando cada `id` devuelto:

```bash
curl --fail-with-body -i http://localhost:8080/api/v1/clubes \
  -H 'Content-Type: application/json' \
  -d '{"nombreClub":"Semillero Local","ciudadId":1}'

curl --fail-with-body -i http://localhost:8080/api/v1/clubes \
  -H 'Content-Type: application/json' \
  -d '{"nombreClub":"Semillero Visitante","ciudadId":1}'
```

Crear el partido con los IDs obtenidos y JWT de ADMIN u ORGANIZADOR:

```bash
curl --fail-with-body -i http://localhost:8080/api/v1/partidos \
  -H "Authorization: Bearer $JWT" \
  -H 'Content-Type: application/json' \
  -d '{"fechaInicio":"2026-11-10T10:00:00","clubLocalId":1,"clubVisitanteId":2,"estado":"PROGRAMADO"}'
```

Los IDs `1` y `2` son ilustrativos. Los clubes deben ser distintos. Los estados del CRUD compartido son `PROGRAMADO`, `EN_CURSO`, `FINALIZADO` y `CANCELADO`. Las fechas de fin informadas deben ser posteriores al inicio.

Para crear participaciones se necesitan un `Jugador` y su partido. El jugador se relaciona con `usuarioId`; la participación usa `jugadorId`, `partidoId` y uno de los clubes del encuentro. Hay una participación por jugador y partido; el dorsal no es el identificador del jugador. Los minutos y estadísticas informados deben ser no negativos.

Las claves foráneas y sus dependencias se validan. Eliminar un registro relacionado puede devolver `409`; no asumir borrado en cascada.

## Análisis de video y YouTube

### Recorrido de una carga

1. El frontend envía un MP4 con JWT y permiso de escritura sobre el partido.
2. Spring valida el archivo y conserva el original. Guarda `VideoPartido` y `AnalysisJob=PENDIENTE`, y responde **202**, `analysisId` y `Location`.
3. `AnalysisWorker` toma la cola SQL y persiste un UUID remoto antes del envío.
4. `HttpAnalysisGateway` envía `video`, `analysisId` y `readJerseys` a Spark; consulta su progreso hasta finalizar.
5. Descarga y confirma **primero el JSON en PostgreSQL**. Solo después solicita el mapa.
6. Guarda el PNG validado en el volumen persistente o registra `NO_DISPONIBLE` si Spark devuelve `422 HEATMAP_UNAVAILABLE`.
7. Spark elimina automáticamente su trabajo al terminar de entregar JSON y mapa, contando el `422` como entrega. **El flujo normal no necesita DELETE**.
8. Spring completa el trabajo y sirve sus copias locales. Si YouTube estaba habilitado, pasa la publicación de `ESPERANDO_ANALISIS` a `PENDIENTE`.
9. `YouTubeUploadWorker` publica el original mediante el uploader Python y OAuth. Verifica canal, título con fecha y visibilidad No listado; guarda ID, enlace y recibo reanudable.

El original del backend se conserva. Un fallo de YouTube no elimina los resultados. Un fallo de mapa después de confirmar JSON deja el trabajo `COMPLETADO` con diagnóstico y `estadoHeatmap=ERROR_DESCARGA`; puede habilitar YouTube. Una ejecución sin JSON que termina en `ERROR` no habilita la publicación.

`COMPLETADO`, `respuestaRecibida`, `heatmapDisponible`, `estadoHeatmap` y `resultadosDisponibles` son indicadores distintos. Los dorsales candidatos y segmentos de Spark no se convierten automáticamente en métricas publicadas del `Jugador`; `resultadosDisponibles` sigue `false` en este flujo.

### Endpoints del backend

| Método | Ruta bajo `/api/v1` | Resultado |
| --- | --- | --- |
| POST | `/partidos/{partidoId}/videos` | Multipart `file`; `202` después de confirmar carga y trabajo. |
| GET | `/partidos/{partidoId}/videos` | Listado paginado. |
| GET | `/partidos/{partidoId}/videos/{videoId}` | Metadatos, análisis y estado YouTube. |
| GET | `/partidos/{partidoId}/videos/{videoId}/analisis` | Trabajos del video. |
| POST | `/partidos/{partidoId}/videos/{videoId}/analisis` | Inicio manual para un video anterior sin análisis; `202`. No repetir tras una carga nueva. |
| GET | `/analisis/{analysisId}` | Estado y progreso persistidos. |
| GET | `/analisis/{analysisId}/resultados` | JSON conservado; `409` si aún no está completado/disponible. |
| GET | `/analisis/{analysisId}/heatmap` | PNG; `409` si está pendiente, `404` si completó sin imagen disponible. |
| POST | `/partidos/{partidoId}/videos/{videoId}/youtube/reintentar` | Reintento compatible con análisis completado y publicación habilitada; `202`. |

Ejemplo, adaptando el partido, el archivo y el análisis devuelto:

```bash
curl --fail-with-body -i http://localhost:8080/api/v1/partidos/1/videos \
  -H "Authorization: Bearer $JWT" \
  -F 'file=@partido.mp4;type=video/mp4'

curl --fail-with-body http://localhost:8080/api/v1/analisis/23 \
  -H "Authorization: Bearer $JWT"

curl --fail-with-body http://localhost:8080/api/v1/analisis/23/resultados \
  -H "Authorization: Bearer $JWT"

curl --fail-with-body http://localhost:8080/api/v1/analisis/23/heatmap \
  -H "Authorization: Bearer $JWT" -o heatmap.png
```

### Habilitar Spark

Obtener del responsable del servicio la URL y el token. El informe registra una publicación mediante Tailscale Funnel; su disponibilidad depende del equipo remoto. Ejemplo de variables locales:

```dotenv
ANALYSIS_ENABLED=true
ANALYSIS_WORKER_ENABLED=true
ANALYSIS_BASE_URL=https://spark-0aa4.tail763771.ts.net
ANALYSIS_TOKEN=<credencial-privada>
ANALYSIS_MAX_RESPONSE_BYTES=16777216
```

Spark exige un UUID canónico, acepta un trabajo activo a la vez y responde `202` después de recibir y validar el archivo completo. Su token de servicio es distinto del JWT de usuario.

El presupuesto predeterminado es **6 horas desde que se crea el trabajo**, incluyendo cola, `GPU_BUSY`, transferencia, procesamiento y recepción. Ante `409 GPU_BUSY`, Spring espera 5 segundos y reintenta: **no hay un timeout separado de espera de GPU**. El límite por petición HTTP es 2 horas, y el de conexión 10 segundos.

Un timeout incierto de subida no autoriza reenviar a ciegas. Al reiniciar se usa el UUID y los checkpoints: JSON guardado evita descargarlo otra vez; PNG local o ausencia confirmada permiten completar aunque Spark ya responda `404`.

Existe una ventana entre la segunda entrega remota y su guardado local: Spark no espera un ACK de persistencia. Una caída ahí puede perder el mapa; el JSON ya confirmado se conserva. Vencer el plazo local no cancela automáticamente la GPU.

### Habilitar YouTube en local

Primero preparar el proyecto de Google, la API, el cliente OAuth de escritorio y el canal siguiendo [YOUTUBE_SETUP.md](docs/YOUTUBE_SETUP.md). El archivo privado debe quedar en `.local/youtube/client_secret.json`.

En Linux/macOS o WSL, desde la raíz:

```bash
python3 -m venv .local/youtube/venv
.local/youtube/venv/bin/python -m pip install -r scripts/youtube-requirements.lock
.local/youtube/venv/bin/python scripts/youtube_upload.py authorize
```

Completar el consentimiento en el navegador y verificar el canal autorizado. Después configurar:

```dotenv
VIDEO_YOUTUBE_ENABLED=true
VIDEO_YOUTUBE_WORKER_ENABLED=true
VIDEO_YOUTUBE_PYTHON=.local/youtube/venv/bin/python
VIDEO_YOUTUBE_SCRIPT=scripts/youtube_upload.py
VIDEO_YOUTUBE_CLIENT=.local/youtube/client_secret.json
VIDEO_YOUTUBE_TOKENS=.local/youtube/tokens.json
VIDEO_YOUTUBE_CHANNEL=Semillero-Video-Analysis
```

El canal puede adaptarse a uno autorizado propio, manteniendo coherencia con la autorización del uploader. El backend debe poder renovar el archivo de tokens. No incluir OAuth ni recibos privados en Git; no ejecutar consentimiento interactivo dentro del worker del servidor.

Para publicar automáticamente una carga nueva, habilitar YouTube antes de cargarla. Las cargas creadas con la integración deshabilitada no se vuelven publicables solo por cambiar una variable: usar el reintento autorizado cuando cumplan sus condiciones.

Los originales quedan en `.local/videos`, los PNG en `.local/analysis` y el JSON en PostgreSQL, salvo que se configuren otras rutas. La ampliación a subidas por partes de 12 GiB es una propuesta, no una capacidad actual.

## Pruebas

### Suite normal

```bash
./mvnw --batch-mode --no-transfer-progress clean verify
```

Usa H2 desechable y servicios HTTP controlados para las pruebas de integración habituales. No necesita credenciales de Google ni realiza análisis reales en Spark. Las pruebas PostgreSQL se omiten sin sus variables; una compilación verde de H2 no valida los SQL de producción.

### Migraciones y persistencia en PostgreSQL real

Preparar las tres bases **locales desechables** y sus usuarios conforme a [BASE_DATOS_DOCKER.md](docs/BASE_DATOS_DOCKER.md#persistencia-y-pruebas), en el ejemplo sobre el puerto 55437:

```bash
MIGRATIONS_PG_TEST_URL=jdbc:postgresql://127.0.0.1:55437/migrations_test \
ANALYSIS_PG_TEST_URL=jdbc:postgresql://127.0.0.1:55437/analysis_hu0203_test \
METRICS_PG_TEST_URL=jdbc:postgresql://127.0.0.1:55437/metrics_hu12_test \
./mvnw --batch-mode --no-transfer-progress clean verify
```

Estas pruebas recrean sus esquemas de prueba; no usar la base local de trabajo ni la compartida del VPS. Verifican instalación, adopción, checksums, restricciones, checkpoints y recuperación. Ejecutarlas al cambiar migraciones o persistencia.

Pruebas del uploader, después de instalar sus dependencias:

```bash
.local/youtube/venv/bin/python -m unittest discover -s scripts/tests -v
```

Las pruebas reales son optativas. `scripts/test_spark_live.py` con `SparkLiveIT` permite validar contra Spark; `--with-youtube` activa ambos workers y **publica un video real no listado**. Requiere base local desechable, tokens y archivo autorizado según [la guía de entrega](docs/ENTREGA_SPARK_YOUTUBE_VPS.md#validación-repetible-del-flujo-real). No se ejecuta como parte de la suite normal.

La [colección de Postman de torneos](docs/torneos/postman/semillero-torneos-sprint1.postman_collection.json) y su [entorno](docs/torneos/postman/semillero-local.postman_environment.json) permiten pruebas manuales; ajustar URL y administrador local, y ejecutarla en orden porque crea datos relacionados.

## Despliegue en el VPS

El despliegue usa una imagen Java 21 con FFmpeg, FFprobe y el runtime Python del publicador. Se construye fuera del VPS y se transfiere según [backend-compartido/README.md](VPS-Implementacion/backend-compartido/README.md).

| Aspecto | Perfil `vps` |
| --- | --- |
| Ejecución | Docker rootless, UID/GID 10001, raíz de la imagen de solo lectura. |
| Red | `network_mode: host`; Spring escucha en `127.0.0.1:18083`. El proxy publica HTTPS. |
| Base | Credenciales explícitas y PostgreSQL compartido en `127.0.0.1:5432`. |
| Esquema | Migrador central; Flyway del runtime desactivado y Hibernate `validate`. |
| Configuración | Entorno privado del despliegue; no importa el `.env` local automáticamente. |
| Persistencia | Volumen `backend_data` en `/var/lib/semillero`, para originales, PNG, OAuth, recibos y temporales. |
| Integraciones | Spark y YouTube optativas, desactivadas por defecto y activables con variables. |
| Seguridad | `JWT_SECRET` obligatorio; CORS con origen público configurado. |

Antes de iniciar la imagen, el migrador central debe confirmar el esquema vigente, incluidas V013, V015 y V016–V018. No ejecutar `init.sql` sobre la base compartida, activar Flyway local contra ella ni cambiar Hibernate a `update` para resolver columnas faltantes.

Conservar volumen, secretos y recibos entre versiones. Respaldar PostgreSQL, originales y PNG. El límite multipart de Spring no elimina límites del proxy o del proveedor de red.

La [entrega conjunta Spark–YouTube](docs/ENTREGA_SPARK_YOUTUBE_VPS.md) detalla construcción, instalación privada de OAuth, activación y prueba. Contiene evidencia local del encadenamiento automático y persistencia tras reiniciar; esa evidencia no acredita por sí sola que la versión ya esté desplegada en el VPS.

## Solución de problemas

| Síntoma | Comprobación y acción |
| --- | --- |
| Falta `DB_PASSWORD` o usa otro puerto | Ejecutar desde la raíz, revisar `.env`, perfil activo y variables que lo sobrescriben en terminal/IDE. |
| `JWT_SECRET` sin resolver | El perfil VPS exige clave explícita; comprobar perfil y entorno según [application-vps.properties](semillero-api/src/main/resources/application-vps.properties). |
| PostgreSQL rechaza contraseña tras editar `.env` | Las variables de inicialización no cambian usuarios de un volumen existente; coordinar el cambio del rol o restaurar credenciales correctas, conservando datos. |
| Puerto 5432 ocupado | Cambiar `DB_PORT_EXTERNAL` y `DB_URL`, o usar el PostgreSQL ya instalado. |
| Puerto 8080 ocupado | Usar `PORT` en local o `--server.port=8081` y adaptar las URLs. |
| Flyway informa checksum distinto | Restaurar el SQL aplicado y añadir una nueva migración; no editar el historial para ocultar la diferencia. |
| Esquema no reconocido o columna faltante | Revisar `DB_URL`, historial y JAR. En VPS, pedir al migrador central las versiones pendientes; no borrar la base. |
| Maven informa `Unable to find a suitable main class` | Compilar desde la raíz y ejecutar el JAR; no aplicar `spring-boot:run` al POM agregador con `-am`. |
| Video devuelve `503` o fallan pruebas de video | Verificar FFmpeg/FFprobe, sus rutas y los permisos/espacio del almacenamiento. |
| Video devuelve `413` | Revisar tamaño, límites multipart, `VIDEO_MAX_BYTES` y proxy. El máximo actual es 2 GiB. |
| Análisis permanece `PENDIENTE` | Revisar ambos interruptores de análisis, configuración efectiva y cola. El plazo de 6h sigue contando desde la solicitud. |
| Spark responde `GPU_BUSY` | Solo admite un trabajo activo; se reintenta con pausa de 5s dentro del presupuesto total. |
| Spark devuelve `404` tras completar | Es normal tras entregar ambos recursos; verificar copias locales y `estadoHeatmap`. |
| JSON disponible, PNG ausente | Distinguir `NO_DISPONIBLE` de `ERROR_DESCARGA`; no borrar ni ocultar el JSON conservado. |
| YouTube queda esperando o deshabilitado | Revisar estado del análisis, configuración al crear la carga y ambos interruptores de YouTube. |
| OAuth no renueva o canal incorrecto | Reautorizar desde un equipo con navegador y verificar canal, permisos y escritura del archivo de tokens. |
| API funciona con curl pero falla en navegador | Revisar CORS para el origen exacto, URL base y encabezado Bearer; no confundir CORS con permisos del recurso. |
| `401` al consultar una ruta protegida | Iniciar sesión y enviar JWT válido. |
| `403` con JWT válido | Revisar rol, pertenencia al club/partido o asignación; volver a iniciar sesión después de cambiar roles. |

## Guía para modificar el código

Los controladores trabajan con DTO y delegan en servicios concretos. Los servicios implementan reglas y transacciones; los repositorios resuelven persistencia. Evitar devolver entidades JPA directamente.

Para cambiar jugadores, por ejemplo:

1. Ajustar `JugadorRequestDto` y `JugadorResponseDto` en `data/.../dto`.
2. Cambiar validaciones y conversiones en `JugadorService`.
3. Modificar rutas o parámetros en `JugadorController` si cambia el contrato HTTP.
4. Si cambia el esquema, actualizar entidad/repositorio y añadir una migración nueva.
5. Verificar permisos y ejecutar pruebas adecuadas; incluir PostgreSQL al cambiar SQL o persistencia.

Los acuerdos compartidos y el contexto de torneos están en [docs/torneos/PENDIENTES.md](docs/torneos/PENDIENTES.md). Sus secciones históricas deben interpretarse según la fecha y el registro de acuerdos.

## Documentación relacionada

| Documento | Para qué sirve |
| --- | --- |
| [Migraciones del modelo relacional](database/migrations) | Esquema versionado y evolución efectiva de la base. |
| [PostgreSQL y Flyway](docs/BASE_DATOS_DOCKER.md) | Adopción, versiones, diagnóstico y pruebas PostgreSQL. |
| [Cargas persistentes y permisos](docs/CARGAS_PERSISTENTES_Y_PERMISOS.md) | Contrato del 202 y autorización por partido. |
| [Informe del servicio Spark](docs/INFORME_INTEGRACION_SPARK.md) | Contrato remoto vigente y limpieza automática. |
| [Integración Spring–Spark](docs/INTEGRACION_SPARK_BACKEND.md) | Checkpoints, recuperación, configuración y evidencia real. |
| [HU02/HU03](docs/HU02_HU03_ANALISIS.md) | Estado/progreso y endpoints de análisis. |
| [Modelo HU12](docs/HU12_MODELO_INTEGRIDAD.md) | Integridad de métricas y trabajo pendiente de publicación. |
| [Entrega Spark y YouTube](docs/ENTREGA_SPARK_YOUTUBE_VPS.md) | Imagen preparada, OAuth, workers y activación conjunta. |
| [Configuración de YouTube](docs/YOUTUBE_SETUP.md) | Preparación del canal y consentimiento inicial. |
| [API de scouting](docs/scouting/API.md) | Prospectos, asignaciones, evaluaciones, etapas, exportación y seguridad. |
| [API de reclutamiento](docs/reclutamiento/API.md) | Pruebas, postulaciones, solicitudes de contacto y notificaciones. |
| [Comparador de jugadores](docs/COMPARADOR_JUGADORES.md) | Goles, asistencias, minutos y comparación desde eventos deportivos. |
| [Contexto de torneos](docs/torneos/CONTEXTO_TORNEOS.md) | Organización e integración del módulo de torneos. |
| [Plan HU02/HU03](docs/PLAN_HU02_HU03_ANALISIS.md) | Planificación y trazabilidad; no es el contrato vigente de ejecución. |

`docs/HU01_CARGA_VIDEOS.md`, `docs/INTEGRACION_AUTOMATICA_VIDEO.md` y el contrato anterior `docs/spark-implementation.md` contienen decisiones históricas. Para cargas actuales usar el flujo `202` con cola persistente y el informe vigente de Spark; para limpieza, JSON primero y eliminación remota automática tras ambas entregas.
