# Semillero App

Backend de clubes de fútbol con Java 21, Spring Boot 4.1.1, Maven y PostgreSQL.
El modelo relacional parte de [dbfutbol.md](dbfutbol.md).

El [plan de implementación de HU01 y HU12–HU16](PLAN_VIDEO_DATA_ANALYSIS.md) organiza las seis historias y sus 28 subissues reales por entregas, dependencias, cambios por capa, pruebas y criterios de cierre. El orden de trabajo es HU01 → HU12 → HU13 → HU15 → HU14 → HU16, según sus prioridades P0/P1.

El [plan de HU02 y HU03](docs/PLAN_HU02_HU03_ANALISIS.md) analiza la base actual y define la solicitud de análisis en segundo plano, la prevención de duplicados y la consulta de estado, avance y finalización, con trazabilidad a sus seis subtareas del tablero.

La [implementación backend de HU02 y HU03](docs/HU02_HU03_ANALISIS.md) incorpora trabajos persistentes, inicio con respuesta `202`, protección contra duplicados y consulta de estado/avance. Incluye el contrato HTTP para conectar el servicio Python, todavía en desarrollo; la integración viene deshabilitada por defecto.

Para HU12, [SB-050 y SB-052](docs/HU12_MODELO_INTEGRIDAD.md) incorporan `PlayerMatchMetrics`, su repositorio y validaciones de análisis, jugador y participación en el partido. La publicación y selección de la versión vigente se implementarán en las siguientes subtareas.

Para crear el canal y preparar la primera subida de prueba, seguir la [configuración inicial de YouTube](docs/YOUTUBE_SETUP.md).

La [implementación de HU01](docs/HU01_CARGA_VIDEOS.md) añade carga multipart de videos de partidos, validación con FFmpeg, almacenamiento del original, metadatos y publicación en YouTube en segundo plano. Para publicar, autorizar el canal y configurar `VIDEO_YOUTUBE_ENABLED=true`; la carga local también funciona con YouTube deshabilitado.

## Organización

```text
semillero-api → core → data
```

- `data/src/main/java/edu/upb/semillero/repository/`: entidades JPA en `entity`, repositorios en `repos`, DTOs en `dto/request` y `dto/response`, y enumeraciones en `enums`.
- `core/src/main/java/edu/upb/semillero/`: servicios transaccionales en `service`, excepciones de negocio en `service/exception` y configuración compartida en `config`.
- `semillero-api/src/main/java/edu/upb/semillero/`: aplicación, controladores en `controller` y manejo de errores HTTP en `controller/exception`.

Cada recurso tiene su entidad, repositorio, servicio, controlador y DTOs propios. Cada servicio y controlador declara directamente sus operaciones CRUD: listado, consulta por ID, creación, actualización y eliminación. Para modificar el comportamiento de un recurso, el equipo trabaja en sus clases concretas.

Por ejemplo, para cambiar el CRUD de jugadores:

- Editar `JugadorController` para modificar rutas, parámetros o respuestas HTTP.
- Editar `JugadorService` para modificar las consultas y las operaciones de creación, actualización y eliminación. Los métodos privados `apply` y `toResponse` convierten entre DTOs y entidades; los servicios que tienen reglas de negocio también incluyen un método `validate`.
- Ajustar `JugadorRequestDto`, `JugadorResponseDto`, `Jugador` o `JugadorRepository` cuando cambien los campos o las consultas necesarias.

## Arranque local

La [guía de PostgreSQL con Docker](docs/BASE_DATOS_DOCKER.md) explica la creación completa del esquema, los datos de ejemplo y la actualización de bases existentes. En un volumen nuevo, `docker compose up -d --wait db` ejecuta automáticamente [database/init.sql](database/init.sql) y crea las 20 tablas.

Requisitos: JDK 21, Docker con Compose, FFmpeg (`ffmpeg` y `ffprobe`) y acceso a Maven Central en la primera compilación. La publicación en YouTube requiere además Python 3 y las dependencias de `scripts/youtube-requirements.txt`. Se incluye Maven Wrapper, por lo que no hace falta instalar Maven.

Desde la raíz, en Bash:

```bash
cp .env.example .env
# Editar DB_PASSWORD en .env antes de continuar.
set -a
source .env
set +a

docker compose up -d --wait db
./mvnw clean verify
java -jar semillero-api/target/semillero-api-0.0.1-SNAPSHOT.jar
```

La API escucha en `http://localhost:8080/api/v1`. En Windows usar `mvnw.cmd` y configurar las variables de `.env` en el entorno o en la ejecución del IDE. Docker Compose lee `.env` automáticamente; Java requiere que esas variables se exporten como en el ejemplo anterior.

Variables:

| Variable | Uso / valor por defecto |
| --- | --- |
| `DB_PASSWORD` | Contraseña PostgreSQL, requerida. |
| `DB_USER` | Usuario, `semillero`. |
| `DB_NAME` | Nombre de la base de Compose, `semillero`. |
| `DB_PORT_EXTERNAL` | Puerto local de Compose, `5432`. |
| `DB_URL` | URL JDBC, `jdbc:postgresql://localhost:5432/semillero`. Adaptar si cambia el nombre o puerto de la base. |
| `SPRING_PROFILES_ACTIVE` | Usar `dev` en desarrollo; también respeta `DB_DDL_AUTO`. |
| `DB_DDL_AUTO` | La plantilla usa `validate` para comprobar el esquema creado por SQL. |
| `PORT` | Puerto de la API, `8080`. |

El esquema inicial usa [000_base.sql](database/migrations/000_base.sql), seguido de las migraciones 001–003. Docker solo lo aplica automáticamente al inicializar un volumen vacío; en una base existente se aplican manualmente las migraciones pendientes. Ejecutar 002 y 003 también cuando `dev` ya creó sus tablas, según sus guías, para completar el índice único parcial y la FK compuesta. La configuración recomendada es `DB_DDL_AUTO=validate`; `dev` conserva `update` como alternativa si no se define esa variable. `docker compose down` detiene la base y conserva los datos.

## Rutas CRUD

Todas las rutas usan `/api/v1` y JSON con nombres en `camelCase`.

| Recurso | Ruta | Tabla del DBML |
| --- | --- | --- |
| País | `/paises` | `Pais` |
| Ciudad | `/ciudades` | `Ciudad` |
| Club | `/clubes` | `Clubes` |
| Usuario | `/usuarios` | `users` |
| Administrador | `/administradores` | `administrador` |
| Usuario de club | `/usuarios-club` | `Usuarios_club` |
| Trabajo en club | `/usuarios-trabaja` | `Usuario_trabaja` |
| Jugador | `/jugadores` | `Jugadores` |
| Posición | `/posiciones` | `Posiciones` |
| Posición de jugador | `/posiciones-jugador` | `posiciones_jugador` |
| Fichaje | `/fichados` | `Fichado` |
| Prueba | `/pruebas` | `Pruebas` |
| Clínica de pago | `/clinicas-pago` | `Clinica_de_pago` |
| Video de jugador | `/videos` | `Videos` |
| Conversación | `/conversaciones` | `conversa` |
| Partido | `/partidos` | `Partidos` |
| Participación | `/participaciones-partido` | `Participaciones_partido` |

Para cada ruta:

| Método | URL | Respuesta |
| --- | --- | --- |
| `GET` | `/{recurso}?page=0&size=20&sort=id,asc` | `200`, página de registros. Tamaño máximo: 100. |
| `GET` | `/{recurso}/{id}` | `200`, registro por ID. |
| `POST` | `/{recurso}` | `201`, registro creado y cabecera `Location`. |
| `PUT` | `/{recurso}/{id}` | `200`, registro actualizado. Reemplaza los campos editables; los opcionales omitidos quedan en `null`. |
| `DELETE` | `/{recurso}/{id}` | `204`, sin cuerpo. |

Los IDs son `bigint` autogenerados y no se envían en POST/PUT. Las claves foráneas se reciben y devuelven como IDs, sin anidar entidades. Ejemplo de respuesta paginada:

```json
{"content":[{"id":1,"nombrePais":"Bolivia"}],"page":0,"size":20,"totalElements":1,"totalPages":1}
```

Ejemplos de creación (reemplazar IDs por los devueltos por la API):

```bash
curl -i -X POST http://localhost:8080/api/v1/paises \
  -H 'Content-Type: application/json' \
  -d '{"nombrePais":"Bolivia"}'

curl -i -X POST http://localhost:8080/api/v1/ciudades \
  -H 'Content-Type: application/json' \
  -d '{"nombreCiudad":"La Paz","paisId":1}'

curl -i -X POST http://localhost:8080/api/v1/clubes \
  -H 'Content-Type: application/json' \
  -d '{"nombreClub":"Semillero","ciudadId":1}'

curl -i -X POST http://localhost:8080/api/v1/usuarios \
  -H 'Content-Type: application/json' \
  -d '{"name":"ana","nombres":"Ana","apellidos":"Perez","email":"ana@example.com","password":"clave-local-123"}'

curl -i -X POST http://localhost:8080/api/v1/jugadores \
  -H 'Content-Type: application/json' \
  -d '{"usuarioId":1,"piernaHabil":"D","fechaNacimiento":"2004-03-15","altura":1.75,"peso":70.0}'

curl -i -X POST http://localhost:8080/api/v1/partidos \
  -H 'Content-Type: application/json' \
  -d '{"fechaInicio":"2026-10-20T10:00:00","clubLocalId":1,"clubVisitanteId":2,"estado":"PROGRAMADO"}'

curl -i -X POST http://localhost:8080/api/v1/participaciones-partido \
  -H 'Content-Type: application/json' \
  -d '{"partidoId":1,"jugadorId":1,"clubId":1,"esTitular":true,"dorsal":10}'
```

Crear también el club visitante antes del partido. El orden habitual de carga es países → ciudades → clubes; usuarios → usuarios de club / jugadores; después fichajes, pruebas, clínicas, videos, partidos y participaciones.

## Campos y relaciones

Los DTOs en `data/.../dto/request` son el contrato completo de entrada. Los campos del DBML conservan su significado: por ejemplo, `nombre_pais` se expone como `nombrePais` y `fecha_inicio_clinica` como `fechaInicioClinica`.

| Columna FK del DBML | Campo JSON |
| --- | --- |
| `pais_id` | `paisId` |
| `ciudad_id` | `ciudadId` |
| `users_id` | `usuarioId` |
| `usuarios_club_id` | `usuarioClubId` |
| `clubes_id` | `clubId` |
| `jugadores_id` | `jugadorId` |
| `posiciones_id` | `posicionId` |
| `users_envia_id` / `users_recibe_id` | `usuarioEnviaId` / `usuarioRecibeId` |
| `club_local_id` / `club_visitante_id` | `clubLocalId` / `clubVisitanteId` |
| `partidos_id` | `partidoId` |

Fechas: `yyyy-MM-dd`; horas: `HH:mm:ss`; timestamps: `yyyy-MM-dd'T'HH:mm:ss`, sin zona horaria como en el DBML.

Se respetan las relaciones muchos-a-uno y la nulabilidad del DBML: las FK del esquema original son opcionales; las de partidos y participaciones son obligatorias. Los identificadores se generan al insertar, como decisión de esta base. No hay borrado en cascada.

Reglas incluidas:

- Longitudes de texto y tipos del esquema; email válido, IDs positivos y medidas físicas positivas cuando se informan.
- Contraseña de 8 a 128 caracteres en POST/PUT de usuarios. Se recibe `password` y se almacena un hash PBKDF2 en `password_hash`; ninguna respuesta devuelve contraseña ni hash.
- Clubes distintos en un partido y fechas finales no anteriores a las iniciales en partidos, fichajes y clínicas.
- Estados de partido: `PROGRAMADO`, `EN_CURSO`, `FINALIZADO`, `CANCELADO`.
- Una participación por jugador y partido, con restricción única en la base. El club debe ser local o visitante. Cambiar los clubes de un partido tampoco puede invalidar participaciones existentes.
- `minutoEntrada` vale `0` al omitirse o enviarse `null`; los minutos y estadísticas son no negativos, y la salida no puede ser anterior a la entrada.
- Los IDs de partido y jugador siguen siendo los identificadores compartidos con el futuro microservicio de análisis. Los videos de partidos y los datos de Couchbase no se guardan en estas tablas.

Errores HTTP en formato `ProblemDetail`: `400` para datos/reglas inválidos, `404` para registros o relaciones inexistentes y `409` para duplicados o restricciones de integridad, incluido borrar un registro que tiene dependencias.

La autenticación y autorización de roles quedan como trabajo posterior: esta base expone los CRUD para desarrollo del equipo.

## Pruebas

```bash
./mvnw clean verify
```

Las pruebas arrancan la aplicación completa con H2 en modo PostgreSQL y ejercitan los 17 CRUD a través de MockMvc, los errores de validación, las FK y restricciones, las reglas de partidos/participaciones y el hash de contraseñas. No requieren una base externa y no usan las credenciales de desarrollo. La base H2 de pruebas no reemplaza la validación con PostgreSQL al preparar el despliegue.
