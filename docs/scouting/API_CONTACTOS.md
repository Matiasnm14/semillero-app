# API de la red de contactos para Flutter

Base: `/api/v1/scouting`. JSON camelCase. Todas las rutas requieren
`Authorization: Bearer {token}` obtenido del login existente `/api/v1/auth/login`.
El titular/club se comprueba contra la base actual; los IDs del cuerpo no otorgan permisos.
OpenAPI: `/v3/api-docs`, Swagger UI: `/swagger-ui.html`, esquema `scoutingBearer`.

## Directorio y perfiles

| Método | Ruta | Uso |
|---|---|---|
| GET | `/jugadores?q=&posicionId=&clubId=&page=0&size=20` | Adultos visibles, ACTIVO; los filtros son opcionales |
| GET | `/jugadores/{id}` | Perfil deportivo visible; oculto/inexistente: 404 |
| GET | `/clubes?q=&page=0&size=20` | Clubes por nombre |
| GET | `/mi-perfil/preferencias` | Preferencias del jugador autenticado |
| PUT | `/mi-perfil/preferencias` | Cambiar visibilidad/recepción de solicitudes |

Páginas: `{ "content": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0 }`.
`page >= 0`, `1 <= size <= 100`, texto hasta 80 caracteres. El orden lo fija el servidor.
Jugador: `id`, `nombres`, `apellidos`, `altura`, `peso`, `piernaHabil`, `descripcion`.
Club: `id`, `nombre`, `ciudadId`, `anioFundacion`, `descripcion`. No se copian los perfiles.
No se devuelven documentos, fecha de nacimiento, email ni teléfono en estas consultas.

Preferencias iniciales: `visible=false`, `aceptaSolicitudes=false`, `version=0`, `actualizadaEn=null`.
Actualizar enviando siempre la versión más reciente:

```json
{"visible":true,"aceptaSolicitudes":true,"version":0}
```

No se envía jugadorId: se obtiene del usuario autenticado. Un club no puede consentir por un jugador.
Para ocultarse: enviar ambas banderas en false y la versión actual. Un 409 requiere recargar datos.
Los perfiles de menores/edad desconocida no pueden habilitarse todavía.

## Solicitudes

| Método | Ruta | Uso |
|---|---|---|
| POST | `/solicitudes` | Crear; exige `Idempotency-Key` UUID |
| GET | `/solicitudes?estado=PENDIENTE&page=0&size=20` | Bandeja propia, estado opcional |
| GET | `/solicitudes/{id}` | Detalle privado |
| POST | `/solicitudes/{id}/respuesta` | Aceptar, rechazar o cancelar |
| GET | `/solicitudes/{id}/historial?page=0&size=20` | Eventos paginados |
| POST | `/solicitudes/{id}/datos-contacto` | Correos tras aceptación; acceso auditado |

Crear (IDs de ejemplo, reemplazar por IDs reales):

```http
POST /api/v1/scouting/solicitudes
Authorization: Bearer TOKEN
Idempotency-Key: 43bf09ef-cda7-4b3d-9641-dab61bc6731a
Content-Type: application/json

{"clubId":1,"jugadorId":2,"motivo":"Nos interesa coordinar una prueba deportiva."}
```

Devuelve 201 con Location la primera vez y 200 al repetir la clave con el mismo contenido.
Una clave nueva por cada intención nueva; ante timeout/reintento conservar la misma.
La comparación ignora solo espacios al principio/final del motivo. No reutilizar clave con otro cuerpo.

Respuesta:

```json
{
  "id":"711e4b53-e76c-4ee7-8db8-0fcf3d55d367",
  "clubId":1,"jugadorId":2,"solicitanteId":3,
  "motivo":"Nos interesa coordinar una prueba deportiva.",
  "estado":"PENDIENTE","creadaEn":"2026-10-29T14:00:00Z",
  "actualizadaEn":"2026-10-29T14:00:00Z","version":0
}
```

Responder:

```json
{"estado":"ACEPTADA","version":0,"motivo":"Quiero conocer los detalles de la prueba."}
```

El jugador puede aceptar o rechazar; un integrante actual del club puede cancelar.
Solo se resuelve una PENDIENTE. Estados finales: ACEPTADA, RECHAZADA, CANCELADA.
Una pendiente por club/jugador; se bloquean nuevas solicitudes del mismo par durante siete días.
El motivo de creación admite hasta 1000 caracteres; el de respuesta hasta 500.

Consultar contacto después de aceptación y mientras siga habilitado:

```json
{"proposito":"Coordinar la prueba deportiva aceptada"}
```

Respuesta: `{ "correoJugador": "jugador@example.test", "correoRepresentanteClub": "club@example.test" }`.
El propósito es obligatorio, hasta 300 caracteres. No persistir/cachar estos datos en Flutter.
El servidor responde `Cache-Control: no-store` y registra actor, propósito, fecha y solicitud.
La revocación de permiso bloquea consultas posteriores; no elimina correos ya vistos por una persona.

## Jugadores guardados por club

| Método | Ruta | Resultado |
|---|---|---|
| PUT | `/clubes/{clubId}/guardados/{jugadorId}` | 200 `{clubId,jugadorId,creadoEn}`; repetir no duplica |
| GET | `/clubes/{clubId}/guardados?page=0&size=20` | Página de guardados del club |
| DELETE | `/clubes/{clubId}/guardados/{jugadorId}` | 204, incluso si ya no estaba guardado |

Guardar requiere perfil visible; guardarlo no crea una solicitud ni envía correo.
Un guardado anterior puede conservar un ID aunque se oculte el perfil: mostrar “perfil no disponible” al recibir 404.

## Errores y estados de interfaz

Errores de negocio siguen ProblemDetail: `status`, `title`, `detail` y, para validación, `errors` cuando aplica.
400: cuerpo/parámetro inválido o regla incumplida; 401: iniciar sesión; 403: no puede actuar por el club;
404: recurso no disponible, también si es ajeno; 409: duplicado, cooldown, versión obsoleta o consentimiento retirado.

No confundir solicitud creada/aceptada con correo entregado. Esta entrega crea notificaciones pendientes
y prepara textos; no hay proveedor conectado. El frontend debe mostrar “solicitud registrada”, nunca
“correo enviado”. Los correos no se exponen en la bandeja o en el detalle; requieren la consulta sensible.
La ruta futura del enlace de notificación será `/contactos/solicitudes/{id}` y requiere coordinación con frontend.

Recomendación de UI: pestañas solicitudes recibidas/enviadas según contexto, filtros por estado,
botones según titularidad, confirmación al responder, reintento con la misma clave y recarga ante 409.
El backend aplica estas restricciones aunque Flutter muestre botones incorrectos.
