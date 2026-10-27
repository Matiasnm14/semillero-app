# Pendientes del módulo de Torneos para acordar con los otros grupos

Grupo 4 (Torneos). Actualizado: 26/10/2026.

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

Estados: `Hecho` (decidido y ya implementado) · `Decidido` (decidido, falta implementarlo) ·
`Asignado` (le toca a otro grupo, **sin que ellos lo hayan confirmado todavía**) · `Pospuesto`.

Al cerrar un punto, poner la fecha y quién lo decidió, y commitear el cambio de esta tabla.

| # | Qué se acordó | Estado | Fecha | Quién |
| --- | --- | --- | --- | --- |
| 1 | Las 15 columnas de `partidos` quedan como están, todas nullable | Hecho | 26/10/2026 | Miguel (torneos) |
| 2 | `capitan` y `minutos_jugados` en `participaciones_partido` | Hecho | 26/10/2026 | Miguel (torneos) |
| 3 | `jugadores.estado`, la mantiene ficha técnica | Hecho | 26/10/2026 | Miguel (torneos) |
| 4 | `PartidoService` rechaza también fechas iguales, para alinearse con `chk_partido_fechas` | Hecho | 26/10/2026 | Miguel (torneos) |
| 5 | Los DTO del CRUD compartido **no** exponen las columnas nuevas; torneos las escribe por sus endpoints | Decidido | 26/10/2026 | Miguel (torneos) |
| 6 | `roles` y `usuarios_roles` las crea **ficha técnica**, junto con el login JWT | Asignado | 26/10/2026 | Ficha técnica |
| 7 | `documentos_jugador` la crea **ficha técnica** | Asignado | 26/10/2026 | Ficha técnica |
| 8 | `auditoria` la crea **torneos**, compartida, con columna `modulo` | Hecho | 26/10/2026 | Miguel (torneos) |
| 9 | `notificaciones` la crea **torneos**, mismo criterio | Hecho | 26/10/2026 | Miguel (torneos) |
| 10 | `FichaTecnicaService`: interfaz y stub escritos; la implementación real es de ficha técnica | Hecho | 26/10/2026 | Miguel (torneos) |
| 11 | `ComunService`: interfaz y stub escritos; **falta la implementación de ficha técnica** | Asignado | 26/10/2026 | Ficha técnica |
| 12 | `TorneosConsultaService`: interfaz escrita; la implementación es la S4-07 | Hecho | 26/10/2026 | Miguel (torneos) |
| 13 | Contrato de video: el **mínimo**, solo `partidoId` y `fechaCierre` | Decidido | 26/10/2026 | Miguel (torneos) |
| 14 | `clubes.nombre_club` ampliado a `varchar(80)`, entidad y DTO | Hecho | 26/10/2026 | Miguel (torneos) |
| 15 | `usuario_trabaja.tipo_usuario_club`: `EN`, `DT`, `PF`, `MD`, `DI` | Decidido | 26/10/2026 | Miguel (torneos) |
| 16 | Flyway: **ya está en `main`**, lo agregó el grupo de video. La decisión de posponerlo quedó obsoleta | Hecho | 26/10/2026 | Grupo de video |
| 17 | `springdoc-openapi-starter-webmvc-ui` 2.8.6 en `semillero-api` | Hecho | 26/10/2026 | Miguel (torneos) |
| 18 | Un subpaquete por grupo en cada capa | Decidido | 26/10/2026 | Miguel (torneos) |
| 19 | Frontend en **Flutter**, en el repositorio `AdriBc16/furbo` | Decidido | 26/10/2026 | Miguel (torneos) |
| 20 | `CLAUDE.md` en la raíz, con una sección por grupo | Hecho | 26/10/2026 | Miguel (torneos) |
| 21 | Los 5 roles de la HU 16 se **derivan** de lo que ya existe (ver abajo) | Decidido | 26/10/2026 | Miguel (torneos) |

| 22 | **Migración Flyway de torneos**: `database/migrations/V007__torneos.sql` | Hecho | 27/10/2026 | Miguel (torneos) |

### Punto 22: la migración Flyway, resuelta el 27/10

`main` pasó a Flyway con `spring.jpa.hibernate.ddl-auto=validate`, **también en el perfil `dev`**, y
ninguna de sus siete migraciones menciona una tabla de torneos. Sin una migración propia, al mergear
`main` la aplicación no arrancaba.

`V007__torneos.sql` lo cubre: las 16 tablas propias, `auditoria` y `notificaciones`, las 18 columnas
de la S1-06, el `ALTER` de `clubes.nombre_club` del punto 14, los índices, las restricciones y los
cinco criterios de desempate. Es idempotente, como `V000`, así que corre también sobre una base que
Hibernate ya creó con `ddl-auto=update`.

**Es V007 y no V006 porque `main` ya tiene un `V006__normalize_image_text_storage.sql`.**

Cómo se verificó, porque el build verde no alcanzaba para esto: se creó una base limpia, se le
aplicaron las siete migraciones de `main` y después la V007, y se arrancó la aplicación contra ella
con `ddl-auto=validate`. Arrancó sin diferencias, o sea que la migración coincide exactamente con
las 35 entidades. Para repetirlo:

```bash
psql -h localhost -p 5433 -U semillero -d postgres -c "CREATE DATABASE semillero_mig OWNER semillero;"
# aplicar V000..V006 de main y despues database/migrations/V007__torneos.sql
DB_URL=jdbc:postgresql://localhost:5433/semillero_mig \
  java -jar semillero-api/target/semillero-api-0.0.1-SNAPSHOT.jar --spring.jpa.hibernate.ddl-auto=validate
```

Ojo con esto al tocar entidades de torneos: `application-test.properties` tiene
`spring.flyway.enabled=false`, así que **las pruebas nunca pasan por Flyway**. Si alguien agrega una
columna a una entidad y no la agrega a una migración, el build sigue verde y la aplicación deja de
arrancar en `dev`.

### Lo que falta confirmar con los otros grupos

Los puntos 1 a 5, 8, 9, 10 y 12 a 21 los decidió torneos y ya puede ejecutarlos. Los puntos **6, 7
y 11 están asignados a ficha técnica y ellos no lo confirmaron todavía**: hasta que lo hagan,
torneos trabaja con mocks y la HU 16 queda sin seguridad real.

### Punto 21: cómo se derivan los roles

La HU 16 pide cinco roles, pero la BD acordada ya modela parte de eso. En vez de duplicarlo:

| Rol de la HU 16 | De dónde sale |
| --- | --- |
| Administrador | La tabla `administrador` que ya existe |
| Entrenador | `usuario_trabaja.tipo_usuario_club = 'EN'` (punto 15) |
| Organizador, mesa de control, consulta | Tabla `roles` + `usuarios_roles`, que crea ficha técnica (punto 6) |

La contra de esto, anotada para que nadie se sorprenda: la lógica de permisos queda repartida en
tres lugares en vez de uno, así que `ComunService.tieneRol` tiene que consultar los tres.

### springdoc sobre Spring Boot 4

`springdoc-openapi` 2.8.6 es la última versión publicada y apunta a Spring Boot 3; **no existe una
versión para Boot 4**. Se probó sobre Spring Boot 4.1.1 y funciona: publica `/v3/api-docs` y
documenta los endpoints de los cuatro módulos. `OpenApiDocsTest` lo verifica en cada build, así que
si una actualización de Spring Boot lo rompe, el build avisa.

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
- ~~Si se agrega Flyway antes de desplegar~~. **Resuelto**: `main` ya usa Flyway con
  `ddl-auto=validate` en todos los perfiles, y torneos aporta `V007__torneos.sql` (punto 22).
- Quién hace el núcleo común y el login con JWT. `main` ya trae un `AuthenticationService` con
  `/auth/login`, pero todavía no hay `roles` ni `usuarios_roles` ni filtro por rol en los endpoints.
- `ApiExceptionHandler` no maneja `HandlerMethodValidationException`. Cuando un controlador tiene
  a la vez un `@PathVariable @Positive` y un `@Valid @RequestBody`, Spring lanza esa excepción en
  lugar de `MethodArgumentNotValidException`, y la respuesta sale sin el mapa `errors` con el campo
  que falló. Pasa, por ejemplo, en `POST /torneos/{torneoId}/categorias` con un `cupoMaximo`
  inválido. Es del núcleo común, no de torneos; hace falta agregar un `@ExceptionHandler` más.
- Interfaces Java entre módulos: torneos consume `FichaTecnicaService` y `ComunService`, y ofrece
  `TorneosConsultaService`. Contratos REST con el microservicio de video en OpenAPI (tarea S1-04).

---

## Cómo se desbloquea cada tarea de torneos

| Pendiente | Tareas que espera |
| --- | --- |
| Que acepten las columnas de `partidos` | S2-14 a S2-17 (fixture), S3-01 (planilla precargada), S3-09 (cierre), S3-12 (tabla) |
| Que acepten las de `participaciones_partido` | S3-02 (titulares y capitán), S3-08 (minutos jugados) |
| Que acepten `jugadores.estado` | S2-07 (elegibilidad) |
| `documentos_jugador` | S2-07 |
| `roles` y `usuarios_roles` | S2-01 a S2-04 |
| `auditoria` | Ya la creó torneos (punto 8). La usan S2-12, S3-10 y S2-04 |
| `notificaciones` | S3-19, S4-16 |

## 7. Estado del sprint 1

De los 23 IDs del sprint 1, de torneos no queda nada pendiente. Lo que falta es de otros grupos:

| ID | Tarea | De quién | Por qué falta |
| --- | --- | --- | --- |
| S1-11, S1-15, S1-19, S1-22 | Las cuatro pantallas | Ignacio | Frontend en Flutter, en el repositorio `AdriBc16/furbo` |
| S1-04, puntos 6, 7 y 11 | `roles`, `usuarios_roles`, `documentos_jugador` y la implementación de `ComunService` | Ficha técnica | Asignado el 26/10, sin confirmar por ellos |

Hechas el 27/10/2026, las dos desbloqueadas por la S1-06:

- **S1-14**, historial de cambios del reglamento: cada cambio queda en `auditoria` con quién lo hizo
  y el reglamento completo antes y después, y se consulta en
  `GET /api/v1/torneos/{torneoId}/reglamento/historial`.
- **S1-18**, no eliminar canchas con partidos: `CanchaService.delete` consulta `partidos` por
  `canchas_id` y rechaza si hay alguno en `PROGRAMADO` o `EN_CURSO`. Los `FINALIZADO` y `CANCELADO`
  no bloquean, porque ya no se van a jugar ahí.

Y la S1-02 quedó cubierta con springdoc (punto 17), mientras que la S1-07 la resolvió el grupo de
video con `database/seeds/demo.sql` y `DevelopmentDataInitializer`.

**Ninguna está Completada** según la definición de terminado: siguen en la rama `torneos`, sin PR ni
revisión de otro integrante.

### Lo que ya no depende de nadie

Las 16 tablas propias, la HU 1 (crear torneo y categorías, S1-08 a S1-10), la HU 2 (reglamento y
orden de desempates, S1-12 y S1-13), la HU 3 (sedes y canchas, S1-16 y S1-17) y la HU 4
(inscripción de equipo con duplicado y cupo, S1-20 y S1-21).
