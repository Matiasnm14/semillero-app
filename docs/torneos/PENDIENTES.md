# Pendientes del módulo de Torneos para acordar con los otros grupos

Grupo 4 (Torneos). Actualizado: 21/10/2026.

El módulo de Torneos ya tiene sus 16 tablas propias como entidades JPA (tareas S1-05 y S1-06).
La referencia completa es `docs/torneos/bd_torneos_propuesta.sql` (sección D).

> **Estado al 23/10/2026.** Las columnas de las secciones 1, 2 y 3 **ya están escritas** en la rama
> `torneos`, con 112 pruebas en verde y sin romper ninguna de las 23 del CRUD que ya existía. Pero se
> escribieron **antes** de la reunión de la tarea S1-04, así que siguen pendientes de que los grupos
> dueños de `partidos`, `participaciones_partido` y `jugadores` las acepten. Si alguna no se acepta,
> se saca de la entidad y de `EsquemaTorneosTest`.

---

## 0. Registro de acuerdos (tarea S1-04)

**Esta tabla es la fuente de verdad del estado de la S1-04.** Si sos una IA trabajando en este
repositorio, leela antes de escribir código que toque tablas o interfaces de otro módulo: `Aceptado`
significa que los grupos lo acordaron y se puede usar; cualquier otro estado significa que no.

Estados: `Aceptado` · `Rechazado` · `Propuesto` (escrito, esperando que lo acepten) · `Sin definir`
(ni siquiera se propuso).

Al cerrar un punto, poner la fecha y quién lo aceptó, y commitear el cambio de esta tabla.

| # | Qué hay que acordar | Estado | Fecha | Quién lo acepta |
| --- | --- | --- | --- | --- |
| 1 | Las 15 columnas de `partidos` (sección 1) | Propuesto | — | dueño de `partidos` |
| 2 | `capitan` y `minutos_jugados` en `participaciones_partido` (sección 2) | Propuesto | — | dueño de `participaciones_partido` |
| 3 | `jugadores.estado` (sección 3) | Propuesto | — | ficha técnica |
| 4 | `chk_partido_fechas` vs `PartidoService` (sección 3.b) | Sin definir | — | dueño de `partidos` |
| 5 | Exponer las columnas nuevas en los DTO del CRUD compartido (sección 3.b) | Sin definir | — | dueños de cada tabla |
| 6 | Quién crea `roles` y `usuarios_roles` | Sin definir | — | todos |
| 7 | Quién crea `documentos_jugador` | Sin definir | — | ficha técnica o torneos |
| 8 | Quién crea `auditoria` | Sin definir | — | todos |
| 9 | Quién crea `notificaciones` | Sin definir | — | todos |
| 10 | Interfaz `FichaTecnicaService` que torneos consume | Sin definir | — | ficha técnica |
| 11 | Interfaz `ComunService` que torneos consume | Sin definir | — | núcleo común |
| 12 | Interfaz `TorneosConsultaService` que torneos ofrece | Sin definir | — | torneos la escribe |
| 13 | Contratos REST con video y el evento `PartidoFinalizado` | Sin definir | — | grupo de video |
| 14 | Ampliar `clubes.nombre_club`, hoy `varchar(15)` | Propuesto | — | dueño de `clubes` |
| 15 | Códigos de `usuario_trabaja.tipo_usuario_club` | Sin definir | — | núcleo común |
| 16 | Agregar Flyway antes de desplegar | Sin definir | — | todos |
| 17 | Agregar `springdoc-openapi` al pom de `semillero-api` | Sin definir | — | todos |
| 18 | Un subpaquete por grupo en cada capa | Propuesto | — | todos |
| 19 | Tecnología del frontend web y de la app móvil | Sin definir | — | todos |
| 20 | Un `CLAUDE.md` en la raíz que apunte a los docs de cada grupo | Sin definir | — | todos |

Ninguno de los 13 puntos que pide la S1-04 (del 1 al 13) está aceptado todavía, así que **la S1-04
no está hecha**. Las interfaces de los puntos 10 a 12 y los contratos del 13 no existen en el
repositorio: hay que acordar su forma antes de escribirlas.

---

## 1. Columnas que torneos necesita en `partidos` (entidad `Partido`)

Todas **opcionales** (aceptan NULL), así el CRUD que ya existe sigue funcionando igual. Hoy
`Partido.java` tiene sólo `fecha_inicio`, `fecha_fin`, `club_local_id`, `club_visitante_id` y
`estado`.

| Columna | Tipo | FK / CHECK |
| --- | --- | --- |
| `torneo_categorias_id` | `bigint` | → `torneo_categorias(id)`. NULL = partido fuera de torneo |
| `grupos_id` | `bigint` | → `grupos(id)` |
| `fase` | `varchar(15)` | CHECK IN (`LIGA`, `GRUPOS`, `OCTAVOS`, `CUARTOS`, `SEMIFINAL`, `TERCER_PUESTO`, `FINAL`) |
| `jornada` | `smallint` | número de fecha |
| `canchas_id` | `bigint` | → `canchas(id)` |
| `arbitro` | `varchar(100)` | — |
| `mesa_control_id` | `bigint` | → `users(id)` |
| `goles_local` | `smallint` | CHECK >= 0 |
| `goles_visitante` | `smallint` | CHECK >= 0 |
| `penales_local` | `smallint` | CHECK >= 0 |
| `penales_visitante` | `smallint` | CHECK >= 0 |
| `ganador_walkover_id` | `bigint` | → `clubes(id)`; CHECK que sea el local o el visitante |
| `partido_siguiente_id` | `bigint` | → `partidos(id)`, llave de eliminación directa |
| `fecha_cierre` | `timestamp` | — |
| `cerrado_por` | `bigint` | → `users(id)` |

Restricciones de tabla que vienen con ellas:

- `chk_partido_torneo_fase`: `torneo_categorias_id IS NULL OR fase IS NOT NULL`
- `chk_walkover_club`: `ganador_walkover_id IS NULL OR ganador_walkover_id IN (club_local_id, club_visitante_id)`
- `chk_partido_fechas`: `fecha_fin IS NULL OR fecha_fin > fecha_inicio`
- Índices: `idx_partidos_tc_estado (torneo_categorias_id, estado)` y `idx_partidos_cancha (canchas_id, fecha_inicio)`

**No se inventan estados nuevos**: se usan los acordados (`PROGRAMADO`, `EN_CURSO`, `FINALIZADO`,
`CANCELADO`). Partido cerrado = `FINALIZADO`; ganado por W.O. = `FINALIZADO` con
`ganador_walkover_id`; anulado = `CANCELADO`.

## 2. Columnas en `participaciones_partido` (entidad `ParticipacionPartido`)

Esta tabla **es** la planilla de jugadores del partido, y reemplaza a la vieja `planilla_jugadores`.

| Columna | Tipo | Nota |
| --- | --- | --- |
| `capitan` | `boolean NOT NULL DEFAULT false` | lo llena torneos |
| `minutos_jugados` | `smallint` CHECK >= 0 | lo calcula torneos al cerrar el partido |

Torneos llena `es_titular`, `dorsal`, `minuto_entrada`, `minuto_salida` y las dos de arriba.
**No toca `velocidad_prom` ni `distancia_recorrida`**: son del microservicio de video.
Suplente que no entró: `minuto_entrada = 0` y `minuto_salida = 0`. Expulsado: `minuto_salida` es el
minuto de la roja.

## 3. Columna en `jugadores` (entidad `Jugador`)

| Columna | Tipo | Dueño |
| --- | --- | --- |
| `estado` | `varchar(15) NOT NULL DEFAULT 'ACTIVO'` CHECK IN (`ACTIVO`, `LESIONADO`, `INACTIVO`, `BAJA`) | la mantiene ficha técnica |

Torneos sólo la **lee**, para validar la lista de buena fe (HU 5).

---

## 3.b Dos cosas que quedaron abiertas al escribir las columnas

**Las columnas nuevas no se exponen en la API.** `PartidoRequestDto`, `PartidoResponseDto`,
`ParticipacionPartidoRequestDto`, `ParticipacionPartidoResponseDto` y los DTO de `Jugador` siguen
igual: las columnas existen en la base pero no entran ni salen por `/api/v1/partidos`,
`/api/v1/participaciones-partido` ni `/api/v1/jugadores`. Exponerlas obliga a cambiar esos DTO y sus
servicios, que son de otros grupos. Torneos las va a escribir por sus propios endpoints (HU 7, 9 y
10), así que no es urgente, pero hay que decidirlo.

**`chk_partido_fechas` es más estricto que `PartidoService`.** La restricción del script dice
`fecha_fin IS NULL OR fecha_fin > fecha_inicio`, mientras que `PartidoService.validate` solo rechaza
`fechaFin.isBefore(fechaInicio)`. Con `fechaFin` igual a `fechaInicio`, el servicio deja pasar y la
base rechaza: el cliente recibe un 409 genérico en vez de un 400 con el motivo. Hay dos salidas, y
las dos son del grupo dueño de `partidos`: agregar `|| isEqual` en el servicio, o sacar esa
restricción. Como `PartidoService.java` no es de torneos, quedó sin tocar.

## 4. Tablas compartidas nuevas: falta definir quién las crea

No están en la BD acordada entre grupos y las necesita torneos junto con al menos otro módulo.
Mientras no se decida el dueño, torneos **no** las crea.

| Tabla | Para qué | La usa torneos en |
| --- | --- | --- |
| `roles` | ADMIN, ORGANIZADOR, ENTRENADOR, MESA_CONTROL, CONSULTA | HU 16 (S2-01 a S2-04) |
| `usuarios_roles` | rol por usuario | HU 16 |
| `documentos_jugador` | CI, certificado de nacimiento, apto médico, autorización, seguro | HU 5, validación de elegibilidad |
| `auditoria` | cambios de reglamento, de estado de inscripción, reapertura de partido, accesos rechazados | HU 2, 6, 10, 16 |
| `notificaciones` | avisos a la web y a la app móvil | HU 8, 14 |

## 5. Datos iniciales que hacen falta

Cuando se defina cómo se cargan (Flyway, un `data.sql` o un servicio de arranque):

```sql
INSERT INTO criterios_desempate (codigo, nombre) VALUES
 ('DIF_GOLES','Diferencia de goles'), ('GOLES_FAVOR','Goles a favor'),
 ('ENFRENTAMIENTO','Enfrentamiento directo'), ('FAIR_PLAY','Fair play'), ('SORTEO','Sorteo');

INSERT INTO roles (codigo, nombre) VALUES
 ('ADMIN','Administrador'), ('ORGANIZADOR','Organizador de torneo'), ('ENTRENADOR','Entrenador'),
 ('MESA_CONTROL','Mesa de control'), ('CONSULTA','Consulta');
```

`criterios_desempate` ya existe como tabla propia de torneos; lo que falta es sembrarla.
`roles` depende del punto 4.

## 6. Otros puntos abiertos

- Ampliar `clubes.nombre_club`, que hoy es `varchar(15)` y no alcanza para nombres reales de club.
- Códigos de `usuario_trabaja.tipo_usuario_club` (`char(2)`): por ejemplo `EN` = entrenador.
- Si se usa un subpaquete por grupo en cada capa. Torneos ya lo hizo así (`...entity.torneos`,
  `...service.torneos`, `...controller.torneos`, tarea S1-01).
- Si se agrega Flyway antes de desplegar, en lugar de depender de `ddl-auto=update`.
- Quién hace el núcleo común y el login con JWT (hoy el repositorio no tiene seguridad).
- Interfaces Java entre módulos: torneos consume `FichaTecnicaService` y `ComunService`, y ofrece
  `TorneosConsultaService`. Contratos REST con el microservicio de video en OpenAPI (tarea S1-04).

---

## Cómo se desbloquea cada tarea de torneos

| Pendiente | Tareas que espera |
| --- | --- |
| Que acepten las columnas de `partidos` | **S1-18** (no borrar cancha con partidos), S2-14 a S2-17 (fixture), S3-01 (planilla precargada), S3-09 (cierre), S3-12 (tabla) |
| Que acepten las de `participaciones_partido` | S3-02 (titulares y capitán), S3-08 (minutos jugados) |
| Que acepten `jugadores.estado` | S2-07 (elegibilidad) |
| `documentos_jugador` | S2-07 |
| `roles` y `usuarios_roles` | S2-01 a S2-04 |
| `auditoria` | **S1-14** (historial del reglamento), S2-12, S3-10, S2-04 |
| `notificaciones` | S3-19, S4-16 |

## 7. Tareas del sprint 1 que siguen bloqueadas

Las demás del sprint 1 ya están hechas (ver más abajo). Estas cuatro no se pueden cerrar sin una
decisión del grupo:

| ID | Tarea | Qué la bloquea |
| --- | --- | --- |
| S1-02 | Docker, perfiles y Swagger | Agregar `springdoc-openapi` toca `semillero-api/pom.xml` y `application.properties`, los dos compartidos. Sin esto, ningún endpoint cumple el punto "documentado en Swagger" de la definición de terminado. |
| S1-04 | Acordar interfaces Java y contratos REST | Es un acuerdo entre grupos, no código. Hay que presentar las secciones 1 a 3 de este documento. |
| S1-07 | Datos de prueba | Falta decidir el mecanismo: no hay Flyway, y un `data.sql` iría en `semillero-api/src/main/resources`, compartido. Lo único sembrado hoy son los 5 criterios de desempate, desde `CriteriosDesempateSeeder` (dentro del subpaquete `torneos`). |
| S1-14 | Historial de cambios del reglamento | Necesita la tabla `auditoria`, sin dueño. |
| S1-18 | No eliminar canchas con partidos programados | Necesita `partidos.canchas_id`. Hoy `CanchaService.delete` solo puede comprobar contra `reprogramaciones`, que no es la regla completa de la HU 3. |

### Lo que ya no depende de nadie

Las 16 tablas propias, la HU 1 (crear torneo y categorías, S1-08 a S1-10), la HU 2 (reglamento y
orden de desempates, S1-12 y S1-13), la HU 3 (sedes y canchas, S1-16 y S1-17) y la HU 4
(inscripción de equipo con duplicado y cupo, S1-20 y S1-21).
