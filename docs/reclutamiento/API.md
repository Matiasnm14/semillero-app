# API de reclutamiento y contacto

La migración `V015__club_recruitment_and_notifications.sql` amplía `pruebas` y agrega posiciones buscadas, solicitudes de contacto, postulaciones y metadatos de notificación. No reutiliza el flujo `/api/v1/scouting`, que gestiona prospectos.

## Directorio

`GET /api/v1/jugadores` requiere sesión y conserva la paginación. Acepta `posicion`, `piernaHabil`, `edadMin`, `edadMax` y `nombre`. Cada jugador incorpora `nombres`, `apellidos`, `fotoPerfil`, `posiciones` y `clubActual` (o `null`). Las fichas vigentes consideran fecha de inicio y fin.

Los filtros `ciudad` y `disponible` se dejan pendientes: el jugador no tiene ciudad vinculada y el equipo aún debe definir si disponibilidad significa “sin fichaje vigente” o una preferencia que controla el jugador. No se presenta una aproximación como si fuera dato confirmado.

## Pruebas y postulaciones

- `GET /api/v1/pruebas?clubId=...` lista pruebas y puede limitar por club.
- `POST /api/v1/pruebas` acepta `titulo`, `fechaPrueba`, `horaPrueba`, `lugar`, `categoria`, `cupos`, `descripcion`, `requisitos`, `posiciones` (nombres existentes) y `estado`. Las cuentas de club obtienen club y perfil de club desde su sesión. ADMIN puede crear para un `clubId` explícito.
- `PUT /api/v1/pruebas/{id}` y `DELETE /api/v1/pruebas/{id}` requieren ser del club dueño o ADMIN. DELETE elimina una prueba sin postulaciones activas y marca `CANCELADA` si tiene postulaciones.
- `POST /api/v1/pruebas/{id}/inscripciones` permite que el jugador autenticado se postule. El `jugadorId` se deriva de su sesión; el cuerpo solo lleva `nota`.
- `GET /api/v1/pruebas/{id}/inscripciones` y `PATCH /api/v1/pruebas/{id}/inscripciones/{inscripcionId}` son para el club dueño o ADMIN. El PATCH recibe `estado` (`ACEPTADO` o `RECHAZADO`) y `notaClub`.
- `DELETE /api/v1/pruebas/{id}/inscripciones/{inscripcionId}` marca la postulación propia como `RETIRADO`.

La postulación bloquea la fila de la prueba antes de comprobar capacidad para evitar superar los cupos con solicitudes simultáneas. La unicidad `(prueba_id, jugador_id)` impide duplicados, incluso si la persona se retiró.

## Contacto

- `POST /api/v1/solicitudes-contacto` recibe `jugadorId`, `asunto` y `mensaje`; el club y perfil del remitente se derivan de la sesión.
- `GET /api/v1/solicitudes-contacto/club/{clubId}` permite al club dueño o a ADMIN ver las solicitudes enviadas.
- `GET /api/v1/solicitudes-contacto/jugador/{jugadorId}` permite al jugador dueño o ADMIN ver las recibidas; al leerlas, las `PENDIENTE` pasan a `VISTO`.
- `PATCH /api/v1/solicitudes-contacto/{id}/estado` recibe `estado` (`RESPONDIDO`, `ACEPTADO` o `RECHAZADO`) y `respuesta`.

El alta desde una cuenta ADMIN se deja sin impersonación: el token no contiene un club actuante y el contrato indica que esa relación sale de la sesión. ADMIN puede consultar y responder solicitudes.

## Notificaciones

- `GET /api/v1/notificaciones?leida=false` pagina las del usuario autenticado y agrega `totalNoLeidas`.
- `PATCH /api/v1/notificaciones/{id}/marcar-leida` marca solo una notificación propia.

Los flujos de contacto y postulaciones crean notificaciones con `tipo`, `referenciaId` y `referenciaTipo`.

## Seguridad y compatibilidad de registro

Las rutas no públicas requieren sesión por defecto. El cliente Flutter actual crea cuentas antes del login, por lo que se conservan excepciones explícitas de registro (`POST /usuarios`, `POST /jugadores`, `POST /clubes`, `POST /posiciones`, `POST /posiciones-jugador`, las relaciones iniciales de club y lecturas de datos de referencia). `POST /fichados` conserva autorización por rol porque crea vínculos de propiedad; el alta de jugador con fichaje inicial necesita coordinarse con el front para usar una sesión o un flujo transaccional de registro.

## Despliegue

En el VPS, Flyway está desactivado y el esquema lo administra el migrador central. Aplicar V015 a la base compartida antes de desplegar este backend; el runtime continúa usando validación de esquema, sin `ddl-auto=update`.
