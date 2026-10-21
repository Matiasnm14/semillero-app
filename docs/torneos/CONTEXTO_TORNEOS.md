# Contexto del módulo de Torneos - Proyecto SEMILLERO

Este archivo es el contexto para cualquier IA (o persona) que trabaje en el módulo de Torneos. Hay que leerlo completo antes de escribir código. Si algo de este archivo no coincide con el código del repositorio, manda el repositorio y hay que avisar al grupo.

Actualizado: 21/10/2026.

---

## 1. El proyecto

- Materia: Proyecto de Ingeniería de Software, UPB Santa Cruz. Docente: Ing. José Alejandro Rodríguez Zegada.
- Sistema: **SEMILLERO**, sistema de gestión para escuelas de fútbol. Lo hace toda la clase en 4 módulos: torneos, ficha técnica, análisis de rendimiento por video y scouting/reclutamiento/contacto. Los 4 funcionan como un solo sistema.
- Nuestro grupo: **Grupo 4, módulo de Torneos**.
- Entrega final: **04/11/2026**. Ese día el módulo tiene que estar terminado y funcionando.

### Equipo

| Integrante | Nombre corto en las tareas |
| --- | --- |
| Miguel Ángel López Arispe | Miguel |
| Ignacio Rodrigo Daza Reyes | Ignacio |
| Sergio Mauricio Valdivia Vergara | Mauricio |
| Alan Marcelo Villavicencio Ponce | Alan |

### Qué hace el módulo de Torneos

Crear torneos internos y externos con sus categorías por edad, configurar el reglamento, registrar sedes y canchas, inscribir equipos y su lista de buena fe, aprobar inscripciones, generar el fixture, reprogramar partidos, llenar la planilla digital del partido, registrar eventos (goles, tarjetas, cambios), calcular minutos, tabla de posiciones y estadísticas, manejar sanciones por tarjetas, y dar estos datos a los otros módulos y a la app móvil.

---

## 2. Repositorio

- GitHub: https://github.com/Matiasnm14/semillero-app
- Es **un solo repositorio para todos los grupos**. No es nuestro: también trabajan ficha técnica, scouting y el grupo que armó la base.
- Stack: Spring Boot 4.1.1, Java 21, Maven Wrapper (`mvnw`), Lombok, PostgreSQL 17 en Docker.
- Paquete raíz: `edu.upb.semillero`.

### Estructura (proyecto Maven de varios módulos)

```text
semillero-api  →  core  →  data
```

| Módulo Maven | Qué tiene | Carpeta |
| --- | --- | --- |
| `data` | Entidades JPA, repositorios, DTO (records), enums | `data/src/main/java/edu/upb/semillero/repository/` (`entity`, `repos`, `dto/request`, `dto/response`, `enums`) |
| `core` | Servicios y excepciones de negocio | `core/src/main/java/edu/upb/semillero/` (`service`, `service/exception`, `config`) |
| `semillero-api` | Aplicación, controladores, manejo de errores HTTP | `semillero-api/src/main/java/edu/upb/semillero/` (`controller`, `controller/exception`) |

### Dónde va el código de torneos

Todo lo de torneos va en un subpaquete `torneos` dentro de cada capa:

```text
data/.../repository/entity/torneos/        entidades de torneos
data/.../repository/repos/torneos/         repositorios
data/.../repository/dto/request/torneos/   DTO de entrada
data/.../repository/dto/response/torneos/  DTO de salida
data/.../repository/enums/torneos/         enums
core/.../service/torneos/                  servicios
semillero-api/.../controller/torneos/      controladores
```

(Propuesta del grupo; si los otros grupos acuerdan otra cosa, se cambia aquí.)

### Cómo levantar el proyecto en Windows

Requisitos: JDK 21, Git, Docker Desktop.

```bash
git clone https://github.com/Matiasnm14/semillero-app.git
cd semillero-app
copy .env.example .env        # en Git Bash: cp .env.example .env
# editar .env y poner una contraseña en DB_PASSWORD
docker compose up -d --wait db
mvnw.cmd clean verify
```

Para correr la API desde IntelliJ: perfil `dev` y las variables del `.env` en la configuración de ejecución (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `SPRING_PROFILES_ACTIVE=dev`). La API queda en `http://localhost:8080/api/v1`.

- Conexión a la BD (DBeaver o pgAdmin): host `localhost`, puerto `5432`, base `semillero`, usuario `semillero`, contraseña la del `.env`.
- Con el perfil `dev` (`ddl-auto=update`) **las tablas las crea Hibernate a partir de las entidades**. No se ejecuta ningún script SQL a mano.
- `docker compose down` apaga la base y conserva los datos.
- Las pruebas usan H2 en modo PostgreSQL, no necesitan Docker.

---

## 3. Base de datos

### 3.1 BD acordada entre grupos (no se toca)

Ya está en el repositorio como entidades del módulo `data`. **No se cambia ni se borra ninguna tabla ni columna existente.** Torneos solo agrega columnas opcionales y tablas nuevas.

Tablas: `pais`, `ciudad`, `clubes`, `users`, `administrador`, `usuarios_club`, `usuario_trabaja`, `jugadores`, `posiciones`, `posiciones_jugador`, `fichado`, `pruebas`, `clinica_de_pago`, `videos`, `conversa`, `partidos`, `participaciones_partido`.

Las dos que más usa torneos:

- `partidos` (entidad `Partido`): `id`, `fecha_inicio`, `fecha_fin`, `club_local_id`, `club_visitante_id`, `estado` (`PROGRAMADO`, `EN_CURSO`, `FINALIZADO`, `CANCELADO`).
- `participaciones_partido` (entidad `ParticipacionPartido`): `id`, `partidos_id`, `jugadores_id`, `clubes_id`, `es_titular`, `dorsal`, `minuto_entrada`, `minuto_salida`, `velocidad_prom`, `distancia_recorrida`. Único por (partido, jugador).

### 3.2 Lo que torneos agrega a tablas acordadas

Todas opcionales (aceptan NULL o tienen valor por defecto) para no romper el CRUD que ya existe.

| Tabla | Columnas nuevas |
| --- | --- |
| `partidos` | `torneo_categorias_id`, `grupos_id`, `fase`, `jornada`, `canchas_id`, `arbitro`, `mesa_control_id` (→ users), `goles_local`, `goles_visitante`, `penales_local`, `penales_visitante`, `ganador_walkover_id` (→ clubes), `partido_siguiente_id` (→ partidos), `fecha_cierre`, `cerrado_por` (→ users) |
| `participaciones_partido` | `capitan` (boolean, false por defecto), `minutos_jugados` |
| `jugadores` | `estado` (`ACTIVO`, `LESIONADO`, `INACTIVO`, `BAJA`), la mantiene ficha técnica |

Estas columnas se agregan **una sola vez y por una sola persona** (tarea S1-06), para no tener conflictos en `Partido.java` y `ParticipacionPartido.java`.

### 3.3 Tablas propias de torneos

`sedes`, `canchas`, `torneos`, `reglamentos`, `criterios_desempate`, `reglamento_desempates`, `torneo_categorias`, `grupos`, `inscripciones_equipo`, `lista_buena_fe`, `reprogramaciones`, `planillas`, `planilla_cuerpo_tecnico`, `eventos_partido`, `sanciones`, `apelaciones`.

### 3.4 Tablas compartidas nuevas que pide torneos

`roles`, `usuarios_roles`, `documentos_jugador`, `auditoria`, `notificaciones`. Falta acordar qué grupo crea cada una. Mientras tanto, si torneos las necesita, se crean en `entity/torneos` y se avisa.

### 3.5 Detalle de columnas

El diseño completo con tipos, restricciones y comentarios está en **`bd_torneos_propuesta.sql`** (versión 3.0), que va junto a este archivo. Ese script es la referencia para escribir las entidades: cada `CHECK` y `UNIQUE` del script se pasa a `@CheckConstraint` y `@UniqueConstraint` en la entidad.

### 3.6 Decisiones que hay que respetar

- **No hay tabla `equipos`.** El equipo es el club inscrito en una categoría del torneo: `inscripciones_equipo.clubes_id`.
- **No hay tabla común `categorias`.** Cada torneo define sus categorías en `torneo_categorias` con `edad_minima` y `edad_maxima`.
- **Campeonato externo** (`torneos.ambito = 'EXTERNO'`, con `organizador_externo`): no se genera fixture ni tabla. Los partidos de nuestro equipo se cargan a mano y el rival se registra en `clubes` con datos mínimos (nombre y ciudad), porque `club_visitante_id` es obligatorio.
- **Estados del partido**: se usan los acordados. Partido cerrado = `FINALIZADO`. Ganado por W.O. = `FINALIZADO` con `ganador_walkover_id`. Partido anulado = `CANCELADO`. No inventar estados nuevos.
- **La planilla de jugadores es `participaciones_partido`**: es_titular, dorsal, capitan. Suplente que no entró: `minuto_entrada = 0` y `minuto_salida = 0`. Expulsado: `minuto_salida` = minuto de la roja.
- `velocidad_prom` y `distancia_recorrida` las llena el microservicio de video. Torneos no las toca.
- Se dice "lista de buena fe", no "nómina".

---

## 4. Arquitectura

- **Monolito distribuido.** Torneos, ficha técnica, scouting y el núcleo común son una sola aplicación (`semillero-api.jar`, puerto 8080).
- Entre módulos de la aplicación **no se usa REST**: cada módulo publica una interfaz de servicio en Java y los demás solo usan esa interfaz.
  - Torneos usa `FichaTecnicaService` (datos del jugador y habilitación) y `ComunService` (usuarios, roles, clubes).
  - Torneos ofrece `TorneosConsultaService` (fixture, resultados, tablas, estadísticas, sanciones) a ficha técnica y scouting.
  - Si el otro grupo todavía no tiene su interfaz, se crea la interfaz con un mock y se sigue.
- Torneos **nunca escribe** en tablas de otro módulo ni usa sus repositorios para escribir.
- El **microservicio de análisis de rendimiento por video** es el único aparte (puerto 8090, otro grupo, guarda los videos en Couchbase). Se comunica por REST. Al cerrar un partido, torneos le envía `PartidoFinalizado` con los datos por jugador. Si no responde, el microservicio lee los partidos `FINALIZADO` por `fecha_cierre`.
- La web y la app móvil usan la API REST de torneos: prefijo **`/api/v1/torneos/...`**.
- Producción: VPS de DigitalOcean con Nginx, PostgreSQL 17 en Docker y los .jar con systemd.

---

## 5. Convenciones de código (seguir el estilo del repo)

Antes de escribir una clase nueva, abrir una parecida que ya exista en el repo (por ejemplo `Partido`, `PartidoService`, `PartidoController`, `PartidoRequestDto`) y copiar su forma.

- **Entidades**: `@Getter @Setter @NoArgsConstructor @Entity @Table(name = "...")`, extienden `BaseEntity` (id `Long` con `IDENTITY`). Relaciones `@ManyToOne(fetch = FetchType.LAZY)` con `@JoinColumn(name = "<tabla>_id")`. Enums con `@Enumerated(EnumType.STRING)`. Restricciones con `@CheckConstraint`, `@UniqueConstraint` e `@Index` dentro de `@Table`.
- **Nombres de tablas y columnas**: minúsculas, plural, FK `<tabla>_id`, igual que en `bd_torneos_propuesta.sql`.
- **DTO**: records en `dto/request` y `dto/response`. Las FK van como IDs (`torneoId`, `clubId`, `jugadorId`), sin anidar entidades. JSON en camelCase. Validación con Jakarta Validation (`@NotNull`, `@Size`, etc.).
- **Servicios**: `@Service`, `@Transactional(readOnly = true)` en la clase y `@Transactional` en los métodos que escriben. Métodos privados `require`, `apply`, `validate`, `toResponse` como en los servicios existentes. Errores con `NotFoundException` (404), `BusinessException` (400) y `ConflictException` (409).
- **Controladores**: `@RestController`, ruta `/api/v1/torneos/...`, devuelven `PageResponse` en listados, `201` con `Location` al crear, `204` al borrar.
- **Fechas**: `LocalDate` y `LocalDateTime`, sin zona horaria.
- **Respuestas** con el ID del jugador, nunca datos personales.
- **Seguridad**: el repo todavía no tiene. La hace Alan en la HU 16 (S2-01 a S2-04). Mientras no exista, no bloquear el resto de tareas por eso.
- Nada de contraseñas ni `.env` en Git.

---

## 6. Reglas de negocio (no se pueden romper)

1. **Edad**: se calcula a la **fecha de corte del reglamento** (por defecto 31/12 de la temporada), nunca a la fecha del partido. Tiene que estar entre `edad_minima` y `edad_maxima` de la categoría.
2. **Lista de buena fe**: un jugador en un solo equipo por torneo; dorsal único dentro del equipo; cantidad entre `min_jugadores` y `max_jugadores` del reglamento; documentos y apto médico vigentes; jugador `ACTIVO`; sin sanción vigente; fichado en el club. Si algo falla, devolver el motivo exacto.
3. **Inscripción**: solo con inscripciones abiertas y antes de la fecha límite; un club una vez por categoría; respetar el cupo. Observar o rechazar exige motivo.
4. **Fixture de liga**: método de rotación, n − 1 fechas con n equipos; si n es impar, un equipo libre por fecha. Solo equipos aprobados. Sin choques: una cancha sin dos partidos que se crucen entre `fecha_inicio` y `fecha_fin`, un equipo sin partidos simultáneos y con el descanso mínimo del reglamento.
5. **Tabla de posiciones**: no se guarda, se calcula desde los partidos `FINALIZADO` de torneos internos. Desempates en el orden de `reglamento_desempates`.
6. **Minutos jugados**: se calculan al cerrar el partido con titularidad, cambios y expulsiones, y se guardan en `participaciones_partido.minutos_jugados`.
7. **Cierre del partido**: una sola transacción que pone `FINALIZADO`, guarda goles, minutos, estadísticas y sanciones, y llena `fecha_cierre` y `cerrado_por`. Un partido cerrado solo lo reabre el organizador y queda en `auditoria`.
8. **Sanciones**: al llegar al umbral de amarillas o con roja se crea la suspensión automática; se descuenta con las fechas jugadas del mismo torneo; el jugador suspendido aparece bloqueado en la planilla.
9. **Auditoría**: cambios de reglamento, de estado de inscripción, reapertura de partido y accesos rechazados se guardan en `auditoria`.

---

## 7. Historias de usuario

| HU | Nombre | Responsable | Pts | Sprint |
| --- | --- | --- | --- | --- |
| 1 | Crear torneo | Miguel | 3 | 1 |
| 2 | Configurar el reglamento | Ignacio | 5 | 1 |
| 3 | Registrar canchas | Mauricio | 3 | 1 |
| 4 | Inscribir un equipo | Alan | 2 | 1 |
| 5 | Registrar la lista de buena fe | Miguel | 8 | 2 |
| 6 | Aprobar o rechazar inscripciones | Ignacio | 3 | 2 |
| 7 | Generar el fixture | Mauricio | 8 | 2 |
| 16 | Control de acceso por roles | Alan | 5 | 2 |
| 8 | Reprogramar un partido | Alan | 3 | 3 |
| 9 | Llenar la planilla del partido | Miguel | 5 | 3 |
| 10 | Registrar los eventos del partido | Ignacio | 8 | 3 |
| 11 | Tabla de posiciones | Mauricio | 5 | 3 |
| 12 | Goleadores y estadísticas | Alan | 3 | 4 |
| 13 | Suspensión automática por tarjetas | Miguel | 5 | 4 |
| 14 | Consulta desde la app móvil | Ignacio | 5 | 4 |
| 15 | Exponer datos por API | Mauricio | 5 | 4 |

Roles del sistema: administrador, organizador, entrenador, mesa de control y consulta.

### Criterios de aceptación

**HU 1 - Crear torneo (organizador).** Pide nombre, temporada, tipo, ámbito (interno o externo), formato (liga, eliminación directa o grupos con fase final), fecha de inicio y fin; si es externo, el organizador. Una o varias categorías con nombre y rango de edad, cada una con su fixture y su tabla. No se guarda si la fecha de fin es anterior a la de inicio. Se crea en estado Borrador y no es visible para los equipos hasta abrir inscripciones.

**HU 2 - Configurar el reglamento (organizador).** Puntos por victoria, empate y derrota. Lista ordenada de desempates: diferencia de goles, goles a favor, enfrentamiento directo, fair play y sorteo. Umbral de amarillas y fechas de suspensión por roja. Fecha de corte (31/12 de la temporada por defecto). Si se cambia en un torneo en curso, la tabla se recalcula y queda registrado quién lo cambió.

**HU 3 - Registrar canchas (organizador).** Nombre, sede, dirección y tipo de superficie (césped natural, césped sintético, tierra, cemento o piso techado). Descripción con dimensiones, techada, iluminación y vestuarios. Superficie y descripción se muestran en el fixture y en el partido. No se elimina una cancha con partidos programados.

**HU 4 - Inscribir un equipo (entrenador).** Solo con inscripciones abiertas y antes de la fecha límite. Queda Pendiente hasta que el organizador la revise. El mismo equipo no se inscribe dos veces en la misma categoría. Si la categoría llegó al cupo, avisa y no deja continuar.

**HU 5 - Lista de buena fe (entrenador).** Jugadores seleccionados desde ficha técnica, sin cargar datos personales. Dorsal y posición; dorsal no repetido en el equipo. Valida edad a la fecha de corte, documentos y apto médico vigentes, sin sanción y no inscrito en otro equipo del torneo. Muestra el motivo exacto del rechazo. No se confirma con menos del mínimo ni más del máximo. La lista confirmada se exporta en PDF.

**HU 6 - Aprobar inscripciones (organizador).** Lista con estado pendiente, observada, aprobada o rechazada. Observar o rechazar pide motivo y el entrenador lo ve. Solo las aprobadas entran al fixture. Cada cambio de estado queda con usuario y fecha.

**HU 7 - Generar el fixture (organizador).** Liga por rotación (n − 1 fechas, un libre si n es impar). Eliminación directa con siembra o sorteo. Cancha y horario sin choques y con descanso mínimo. Ajuste manual con nueva validación. Exportar a PDF y publicar. En campeonato externo no se genera: se cargan a mano los partidos con el rival registrado como club.

**HU 8 - Reprogramar un partido (organizador).** Motivo obligatorio. Valida conflictos de fecha, hora y cancha. Notifica a los dos equipos. Guarda historial. No se reprograma un partido cerrado.

**HU 9 - Llenar la planilla (mesa de control).** Carga sola torneo, categoría, fecha, hora, cancha con superficie, equipos y árbitro. Titulares y suplentes desde la lista de buena fe con dorsal, y capitán; se guardan en `participaciones_partido`. Suspendidos y no habilitados aparecen bloqueados. Cuerpo técnico presente y observaciones del árbitro. Se guarda incompleta y se retoma. Se exporta a PDF.

**HU 10 - Eventos del partido (mesa de control).** Goles con autor, minuto, tipo (jugada, penal, tiro libre, autogol) y asistencia opcional. Tarjetas con motivo; cambios con minuto, sale y entra. Solo jugadores de la planilla. Calcula minutos jugados y los guarda en `participaciones_partido`. Al cerrar se actualizan tabla, estadísticas y sanciones. Solo el organizador reabre un partido cerrado y queda registrado.

**HU 11 - Tabla de posiciones (entrenador, jugador, familia).** PJ, PG, PE, PP, GF, GC, DG y puntos. Una por categoría y una por grupo si hay grupos; en externos no hay tabla. Aplica los desempates del reglamento. Se calcula desde partidos cerrados. Anulados y W.O. según el reglamento.

**HU 12 - Goleadores y estadísticas.** Tablas de goleadores, asistencias, vallas menos vencidas y fair play. Resumen por jugador: partidos, minutos, goles, asistencias y tarjetas. No cuentan partidos anulados. Se actualizan al cerrar cada partido.

**HU 13 - Suspensión automática (organizador).** Acumula tarjetas por jugador y torneo. Al llegar al umbral genera la suspensión con su número de fechas. Se descuenta con las fechas jugadas del mismo torneo. Lista de sancionados por fecha. Sanción manual con motivo y apelación con resolución.

**HU 14 - App móvil (jugador o familia).** Próximos partidos con fecha, hora, cancha y superficie. Tabla y goleadores de su categoría. Aviso de suspensión vigente. Notificación al reprogramar un partido o publicar un resultado. Misma API REST y misma cuenta que la web.

**HU 15 - Exponer datos por API.** Torneos, fixture, tabla, partidos, estadísticas de un jugador y sanciones: por REST para video, web y app; por interfaces Java para ficha técnica y scouting. Contratos REST en OpenAPI. `PartidoFinalizado` al cerrar un partido. Solo IDs, sin datos personales. Endpoints con autenticación y error claro si el token no es válido.

**HU 16 - Control de acceso por roles (administrador).** Roles administrador, organizador, entrenador, mesa de control y consulta. Solo el organizador configura torneos, aprueba inscripciones y reprograma. El entrenador solo maneja la lista de su equipo. La mesa de control solo sus partidos asignados. Consulta solo lee. Acciones no permitidas se rechazan y se registran.

---

## 8. Tareas por sprint

El documento oficial es el **Sprint Backlog** (Google Doc "Sprint Backlog - Módulo de Torneos", versión de entrega 04/11). Esta es la lista resumida para saber qué hacer y en qué orden.

**Estado real al 21/10/2026:** el repositorio todavía no tiene código de torneos. Las tareas de los sprints 1 y 2 están atrasadas y hay que hacerlas primero, porque las del sprint 3 dependen de ellas. Respetar la columna "Depende de".

### Sprint 1 (01/10 al 09/10) - base, torneo, reglamento, canchas, inscripción

| ID | Tarea | Resp. | Depende de |
| --- | --- | --- | --- |
| S1-01 | Crear los subpaquetes `torneos` en cada capa del repo | Mauricio | - |
| S1-02 | Levantar PostgreSQL en Docker, revisar perfiles dev/test, habilitar Swagger (springdoc) | Alan | S1-01 |
| S1-03 | Modelo ER del módulo (ya está: `bd_torneos_propuesta.sql`) | Todos | - |
| S1-04 | Acordar interfaces Java con ficha técnica y contratos REST con video; presentar las columnas que torneos agrega | Ignacio | S1-03 |
| S1-05 | Pasar restricciones del script a las entidades (CHECK, UNIQUE) | Miguel | S1-03 |
| S1-06 | Entidades JPA y repositorios del sprint, y columnas nuevas en `Partido` y `ParticipacionPartido` | Alan | S1-05 |
| S1-07 | Datos de prueba (torneos internos y externos, sedes, canchas, usuarios) | Alan | S1-06 |
| S1-08 | Endpoint crear torneo (estado Borrador, validaciones) | Miguel | S1-06 |
| S1-09 | Categorías del torneo con rango de edad | Miguel | S1-08 |
| S1-10 | Estados Borrador e Inscripciones abiertas | Miguel | S1-08 |
| S1-11 | Pantalla de creación de torneo | Miguel | S1-08 |
| S1-12 | Endpoint de reglamento | Ignacio | S1-06 |
| S1-13 | Orden de criterios de desempate | Ignacio | S1-12 |
| S1-14 | Historial de cambios del reglamento | Ignacio | S1-12 |
| S1-15 | Pantalla de reglamento | Ignacio | S1-12 |
| S1-16 | CRUD de sedes y canchas | Mauricio | S1-06 |
| S1-17 | Descripción de la cancha | Mauricio | S1-16 |
| S1-18 | No eliminar canchas con partidos programados | Mauricio | S1-16 |
| S1-19 | Pantalla de sedes y canchas | Mauricio | S1-16 |
| S1-20 | Endpoint de inscripción de equipo | Alan | S1-10 |
| S1-21 | Validar duplicado y cupo | Alan | S1-20 |
| S1-22 | Pantalla de inscripción | Alan | S1-20 |
| S1-23 | Pruebas unitarias del sprint 1 | Todos | S1-08 a S1-22 |

### Sprint 2 (10/10 al 18/10) - roles, lista de buena fe, aprobación, fixture

| ID | Tarea | Resp. | Depende de |
| --- | --- | --- | --- |
| S2-01 | Spring Security con JWT (coordinar con el login común) | Alan | S1-01 |
| S2-02 | Roles y permisos por endpoint | Alan | S2-01 |
| S2-03 | Restricciones por propiedad (entrenador y mesa de control) | Alan | S2-02 |
| S2-04 | Registro de accesos rechazados | Alan | S2-02 |
| S2-05 | Consulta a ficha técnica por su interfaz Java (con mock) | Miguel | S1-04 |
| S2-06 | Registrar jugadores en la lista de buena fe | Miguel | S2-05 |
| S2-07 | Validaciones de elegibilidad | Miguel | S2-06, S1-12 |
| S2-08 | Mínimo y máximo de jugadores, PDF de la lista | Miguel | S2-06 |
| S2-09 | Pantalla de lista de buena fe | Miguel | S2-07 |
| S2-10 | Listado de inscripciones | Ignacio | S1-20 |
| S2-11 | Cambio de estado con motivo | Ignacio | S2-10 |
| S2-12 | Historial de estados | Ignacio | S2-11 |
| S2-13 | Pantalla de revisión | Ignacio | S2-11 |
| S2-14 | Fixture de liga por rotación | Mauricio | S2-11 |
| S2-15 | Llaves de eliminación directa | Mauricio | S2-11 |
| S2-16 | Asignar cancha y horario sin choques | Mauricio | S2-14, S1-16 |
| S2-17 | Ajuste manual del fixture (y partidos de campeonatos externos) | Mauricio | S2-16 |
| S2-18 | Exportar y publicar el fixture | Ignacio | S2-16 |
| S2-19 | Pantalla del fixture | Ignacio | S2-16 |
| S2-20 | Pruebas del sprint 2 | Todos | S2-01 a S2-19 |

### Sprint 3 (19/10 al 27/10) - planilla, eventos, tabla, reprogramación (sprint actual)

| ID | Tarea | Resp. | Depende de |
| --- | --- | --- | --- |
| S3-01 | Planilla precargada | Miguel | S2-16 |
| S3-02 | Titulares, suplentes y capitán en `participaciones_partido`; bloquear suspendidos | Miguel | S2-07 |
| S3-03 | Cuerpo técnico, observaciones y borrador | Miguel | S3-02 |
| S3-04 | PDF de la planilla | Miguel | S3-03 |
| S3-05 | Pantalla de planilla para celular | Miguel | S3-02 |
| S3-06 | Registro de goles | Ignacio | S3-02 |
| S3-07 | Registro de tarjetas y cambios | Ignacio | S3-02 |
| S3-08 | Cálculo de minutos jugados | Ignacio | S3-07 |
| S3-09 | Cierre del partido en una transacción | Ignacio | S3-08 |
| S3-10 | Reapertura de partido | Ignacio | S3-09 |
| S3-11 | Pantalla de eventos | Ignacio | S3-07 |
| S3-12 | Cálculo de la tabla | Mauricio | S3-09 |
| S3-13 | Criterios de desempate | Mauricio | S3-12, S1-13 |
| S3-14 | Tablas por categoría y grupo | Mauricio | S3-13 |
| S3-15 | Pantalla de tabla de posiciones | Mauricio | S3-14 |
| S3-16 | Endpoint de reprogramación | Alan | S2-17 |
| S3-17 | Validar conflictos | Alan | S3-16 |
| S3-18 | Historial de reprogramaciones | Alan | S3-16 |
| S3-19 | Notificar a los equipos | Alan | S3-16 |
| S3-20 | Pantalla de reprogramación | Alan | S3-16 |
| S3-21 | Prueba del flujo completo del partido | Alan | S3-09, S3-14 |
| S3-22 | Pruebas unitarias del sprint 3 | Todos | S3-01 a S3-20 |

### Sprint 4 (28/10 al 04/11) - disciplina, API, estadísticas, app, pruebas finales

| ID | Tarea | Resp. | Depende de |
| --- | --- | --- | --- |
| S4-01 | Acumulación de tarjetas | Miguel | S3-09 |
| S4-02 | Suspensión automática | Miguel | S4-01, S1-12 |
| S4-03 | Descuento y bloqueo en planilla | Miguel | S4-02, S3-02 |
| S4-04 | Lista de sancionados | Miguel | S4-02 |
| S4-05 | Sanción manual y apelación | Miguel | S4-02 |
| S4-06 | Pantalla de régimen disciplinario | Miguel | S4-04 |
| S4-07 | Endpoints REST e interfaz `TorneosConsultaService` | Mauricio | S3-14 |
| S4-08 | Documentar contratos en OpenAPI | Mauricio | S4-07, S1-04 |
| S4-09 | Evento `PartidoFinalizado` | Mauricio | S3-09 |
| S4-10 | Seguridad de la API | Mauricio | S2-01 |
| S4-11 | Tablas del torneo (goleadores, asistencias, vallas, fair play) | Alan | S3-09 |
| S4-12 | Resumen por jugador | Alan | S3-08 |
| S4-13 | Pantallas de estadísticas | Alan | S4-11 |
| S4-14 | Pantallas de consulta móvil | Ignacio | S4-07 |
| S4-15 | Login y aviso de suspensión en la app | Ignacio | S4-02, S2-01 |
| S4-16 | Notificaciones | Ignacio | S3-19 |
| S4-17 | Pruebas en dispositivos | Ignacio | S4-16 |
| S4-18 | Pruebas de integración entre módulos | Mauricio | S4-08 |
| S4-19 | Pruebas de aceptación | Alan | S4-18 |
| S4-20 | Regresión y corrección de errores | Todos | S4-19 |

Las tareas de "Pantalla" dependen del frontend, cuya tecnología todavía no está definida. Primero se hace el backend (endpoint, servicio, pruebas) y la pantalla después.

---

## 9. Cómo trabajar con IA en este proyecto

Reglas para la IA de cada integrante:

1. **Leer este archivo y `bd_torneos_propuesta.sql` antes de empezar.** Después leer las clases del repo relacionadas con la tarea.
2. **Trabajar una tarea a la vez, por su ID** (por ejemplo "hacé la S2-06"). No adelantar tareas de otro integrante.
3. **Hacer `git pull` antes de empezar** para tener lo que subieron los demás.
4. **Una rama por tarea**: `torneos/S2-06-lista-buena-fe`. Nunca trabajar directo en `main`.
5. **Solo tocar código de torneos** (subpaquetes `torneos`). Si hace falta cambiar algo fuera (otra entidad, `pom.xml`, configuración), no hacerlo sin avisar al grupo.
6. **No cambiar `Partido.java` ni `ParticipacionPartido.java`** salvo en la S1-06. Si falta una columna, se avisa y la agrega una sola persona.
7. **Antes de subir**: `mvnw.cmd clean verify` tiene que pasar. Cada servicio nuevo con sus pruebas unitarias.
8. **Commit**: `[S2-06] Registrar jugadores en la lista de buena fe`. Después un Pull Request a `main` para que otro integrante lo revise.
9. **Si una tarea depende de otra que no está hecha**: crear lo mínimo necesario (interfaz o mock) y dejarlo anotado en el Pull Request, o avisar al responsable.
10. **No inventar** columnas, estados ni reglas que no estén aquí o en el script. Si algo no está claro, preguntar al integrante antes de decidir.

### Cómo se marca el avance

- Al empezar una tarea: en el Sprint Backlog (Google Doc), columna Estado = **En progreso**.
- Al abrir el Pull Request: **En revisión**.
- Cuando cumple la definición de terminado y está en `main`: **Completado**.
- Si no puede avanzar por una dependencia: **Bloqueado**, con el motivo en Comentarios.

### Definición de terminado

Una tarea está Completada cuando: el código está en `main` del repositorio semillero-app y lo revisó otro integrante; cumple los criterios de aceptación de su HU; tiene pruebas unitarias que pasan; el endpoint está documentado en Swagger/OpenAPI; y funciona con lo demás del sprint sin romper lo anterior.

---

## 10. Pendientes de acordar con los otros grupos

- Que acepten las columnas que torneos agrega a `partidos`, `participaciones_partido` y `jugadores`.
- Qué grupo crea `roles`, `usuarios_roles`, `documentos_jugador`, `auditoria` y `notificaciones`.
- Quién hace el núcleo común y el login con JWT.
- Ampliar `clubes.nombre_club` (hoy `varchar(15)`).
- Códigos de `usuario_trabaja.tipo_usuario_club` (por ejemplo EN = entrenador).
- Si se usa un subpaquete por grupo dentro de cada capa.
- Si se agrega Flyway antes de desplegar.
- Tecnología del frontend web y de la app móvil.
