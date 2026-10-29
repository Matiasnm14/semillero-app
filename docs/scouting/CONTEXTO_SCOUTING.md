# Scouting: primera entrega de red de contactos

Estado: implementación local en `scouting/backend-inicial`, pendiente de revisión del equipo.
La prioridad indicada por nuestro grupo es conectar clubes y jugadores con perfiles actualizados.
Esta entrega acota el alcance amplio del PDF: no implementa todo el expediente de prospectos.

## Alcance implementado

- Directorio paginado de jugadores adultos que habilitaron su visibilidad y de clubes.
- Búsqueda de jugadores por nombre, posición y fichaje vigente; búsqueda de clubes por nombre.
- Lectura directa de proyecciones de los perfiles maestros: sus actualizaciones se reflejan sin copiar perfiles.
- Preferencias propias del jugador: visible y acepta solicitudes. Por defecto ambas son falsas.
- Jugadores guardados por club (alta y baja idempotentes).
- Solicitudes club → jugador: creación idempotente, bandeja privada, filtros por estado y detalle.
- Aceptación/rechazo por el titular, cancelación por un integrante actual del club, control de versión.
- Historial de cambios y consulta de correos después de aceptación, con propósito y auditoría.
- Outbox transaccional de correo y preparación de textos institucionales. No hay envío real en esta entrega.
- Pruebas de API con JWT real, permisos, validación, concurrencia, consentimiento y PostgreSQL.

El 60–70 % solicitado es una referencia de avance de esta entrega orientada a contactos,
no una medición de cumplimiento de todos los RF/RNF del documento académico.

## Propiedad e integración

Se añaden únicamente tablas `scouting_*` mediante V011 y clases en subpaquetes `scouting`.
No se alteran tablas/migraciones previas, el catálogo de roles, login, controladores de otros módulos,
ni la tabla `conversa`. La numeración V011 debe comprobarse otra vez antes de integrar la rama.

El sistema reutiliza el JWT existente y `ComunService` para comprobar las vinculaciones actuales
del usuario con clubes. No basta con enviar un `clubId` o tener un rol en el token. Un ADMIN
sin vinculación tampoco obtiene acceso automático a las solicitudes.
Una cadena Spring Security propia cubre exclusivamente `/api/v1/scouting/**`.

Las lecturas de ficha usan un repositorio de proyecciones sin operaciones de escritura, junto
con los repositorios compartidos de jugadores y usuarios para identidad y titularidad.
Son adaptadores de lectura a la base actual: cuando exista un contrato público completo de
ficha técnica, se podrán sustituir sin cambiar el contrato REST de contactos.
Los IDs externos siguen siendo bigint. Solicitudes, eventos, guardados y correo usan UUID.
No se crean cuentas ni fichas de jugador duplicadas ni se añaden FK a las tablas de otro equipo.

La edición del perfil deportivo/club sigue en sus APIs existentes. Scouting solo modifica sus
preferencias y registros propios. No debe utilizarse este módulo para editar identidad o fichajes.

## Reglas de esta entrega

1. Solo el titular de la ficha modifica sus preferencias, identificado desde el JWT.
2. Visible es requisito para aceptar solicitudes. Los permisos se comprueban en el servidor.
3. Solo se publican jugadores ACTIVO con fecha de nacimiento que confirme al menos 18 años.
   El corte utiliza fecha UTC; se debe acordar una zona institucional si difiere la política de edad.
   Menores o edad desconocida quedan bloqueados hasta implementar tutor y consentimiento verificable.
4. Un integrante actual del club crea solicitudes y accede a su bandeja. No hay permisos globales
   para roles nuevos ni se modifica la lista compartida de roles.
5. Una solicitud pendiente por par club/jugador. Se impide reiterar solicitudes del mismo club
   al jugador dentro de siete días, también después de rechazo o cancelación.
6. La clave idempotente UUID pertenece al usuario solicitante. Repetirla con el mismo contenido
   devuelve la solicitud actual; con otro contenido devuelve 409. Dos integrantes diferentes
   del mismo club siguen sujetos al límite de una pendiente por par.
7. La aceptación/rechazo solo corresponde al destinatario. Las respuestas son finales;
   no hay PATCH genérico, reapertura ni cambio libre de estado.
8. La respuesta lleva la versión leída. Una versión obsoleta recibe 409. Se serializan
   cambios concurrentes con bloqueos transaccionales; PostgreSQL añade un índice único parcial.
9. Aceptar permite consultar los correos actuales de ambas cuentas. La consulta exige motivo,
   registra actor y fecha, y responde con Cache-Control: no-store. No entrega teléfono o documento.
10. Retirar el permiso de contacto o la vinculación del solicitante con el club vuelve a bloquear
    la consulta sensible. El historial ya registrado se conserva. La desactivación del jugador
    también impide nuevas solicitudes y consultas sensibles.
11. Un tercero obtiene 404 tanto para una solicitud ajena como para una inexistente.
    La bandeja solo contiene solicitudes del propio jugador y de los clubes donde trabaja.
12. Guardados es una lista interna del club: puede conservar IDs que ya había guardado aunque
    el jugador retire su visibilidad. El perfil oculto no vuelve a ser consultable por ese hecho.

Las búsquedas, páginas e historial se ejecutan en base de datos, con tamaño máximo 100 y orden
estable. Los comodines LIKE del texto se escapan; no se arma SQL concatenando parámetros.
Las fechas de scouting se almacenan como Instant / timestamptz; el frontend elige su zona al mostrar.

## Correo: decisión de diseño y trabajo pendiente

Se propone usar una dirección del dominio de la plataforma, con dos notificaciones separadas:
invitación al jugador y constancia al representante. No se usa CC, que revelaría direcciones
entre participantes antes de que el jugador acepte. Las notificaciones invitan a iniciar sesión
y revisar/responder en la plataforma. Tras la aceptación, ambos pueden obtener los correos
autorizados y continuar fuera de la aplicación; no hay chat ni bandeja de mensajes.

La base guarda los IDs del evento y destinatario, la plantilla y el estado
`PENDIENTE_CONFIGURACION`, sin guardar copias de correos personales ni el contenido sensible
en una cola. La solicitud, su evento y sus notificaciones se confirman en la misma transacción.
`PreparadorCorreoContacto` construye remitente, destinatario, asunto y texto sin red ni cambios de estado.
Configuración preparada: `scouting.correo.remitente` y `scouting.correo.url-publica` (HTTPS).
La ruta de frontend `/contactos/solicitudes/{id}` debe acordarse e implementarse con ese equipo.

Falta el adaptador SMTP/proveedor, dominio verificado (SPF/DKIM/DMARC), credenciales externas,
verificación de direcciones, despachador con reintentos, límites de envío y gestión de rebotes.
El despachador tendrá que revalidar consentimiento, mayoría de edad, estado y vinculación
antes de cada intento, no confiar en que el evento viejo sigue autorizado. Tendrá que cancelar
invitaciones resueltas y usar una clave estable por correo donde el proveedor soporte idempotencia.
La cola NO es garantía de entrega ni se muestra como ENVIADO. No hay correos reales enviados por las pruebas.

## Pendiente del alcance completo

- Consentimiento de tutor, verificación de contactos y políticas institucionales para menores.
- Bloqueos por usuario/club, cuotas globales contra abuso y retención/anonimización automatizada.
- Fusión de duplicados, prospectos, asignaciones, informes y pruebas deportivas del PDF.
- Integraciones públicas completas con los otros grupos; eventos externos y observabilidad.
- OIDC/PKCE y controles de infraestructura descritos en el documento académico.

Los CRUD compartidos heredados todavía tienen rutas públicas para desarrollo (por ejemplo,
algunas lecturas de usuarios). Este módulo restringe sus propias rutas y proyecciones; no corrige
ni afirma proteger todas las rutas ajenas. Hace falta revisión conjunta antes de un despliegue público.

## Verificación reproducible

Desde la raíz: `mvnw.cmd clean verify` (Java 21; FFmpeg/ffprobe para los tests de video).
Solo contactos: `mvnw.cmd test -Dtest=ScoutingContactosApiTest -Dsurefire.failIfNoSpecifiedTests=false`.

Para repetir las mismas pruebas con PostgreSQL en una **base desechable**:

```powershell
$env:SCOUTING_TEST_URL = 'jdbc:postgresql://127.0.0.1:55441/scouting_test'
$env:SCOUTING_TEST_USER = 'scouting_test'
.\mvnw.cmd test '-Dtest=ScoutingContactosApiTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dspring.flyway.enabled=true' '-Dspring.jpa.hibernate.ddl-auto=validate'
```

Las variables solo cambian el datasource de esta clase de pruebas. No usar una base de trabajo
o producción para pruebas. Flyway aplica V000–V011 y JPA valida el esquema; el test normal usa H2.
