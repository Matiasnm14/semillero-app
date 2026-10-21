# Pendientes del módulo de Torneos para acordar con los otros grupos

Grupo 4 (Torneos). Actualizado: 21/10/2026.

El módulo de Torneos ya tiene sus 16 tablas propias como entidades JPA (tareas S1-05 y S1-06).
Lo que falta son los cambios en **archivos compartidos**, que no se tocan sin que el grupo dueño
esté de acuerdo. La referencia completa es `docs/torneos/bd_torneos_propuesta.sql` (sección D).

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
| Columnas de `partidos` | S2-14 a S2-17 (fixture), S3-01 (planilla precargada), S3-09 (cierre), S3-12 (tabla) |
| Columnas de `participaciones_partido` | S3-02 (titulares y capitán), S3-08 (minutos jugados) |
| `jugadores.estado` | S2-07 (elegibilidad) |
| `documentos_jugador` | S2-07 |
| `roles` y `usuarios_roles` | S2-01 a S2-04 |
| `auditoria` | S1-14, S2-12, S3-10, S2-04 |
| `notificaciones` | S3-19, S4-16 |

Mientras tanto, lo que ya no depende de nadie: las 16 tablas propias, la HU 1 (crear torneo y
categorías), la HU 2 (reglamento) y la HU 3 (sedes y canchas).
