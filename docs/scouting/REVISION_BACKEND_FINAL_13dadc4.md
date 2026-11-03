# Revisión del push 13dadc4 de scouting/backend-final

Revisión del 02/11/2026 sobre `13dadc4` («Completa reclutamiento backend»), comparado
con su anterior `6d88d49`, con main `d2ca1c2` y con nuestra integración `2d17d23`.
El nuevo commit cambia 50 archivos: aproximadamente 1471 inserciones y 55 borrados.

## Decisión

El nuevo push **sí incorpora la red club–jugador**: contactos, convocatorias,
postulaciones y notificaciones. La evaluación anterior de que backend-final solo
servía al flujo interno de scouts ya no describe todo su alcance.

No sustituir nuestra rama ni hacer un merge directo. Conviene recuperar las
convocatorias/postulaciones y las mejoras de consultas por lotes, adaptándolas a
los permisos ya corregidos y a un único contrato de contacto. No mantener dos
sistemas de solicitudes que permitan eludir las restricciones uno del otro.

## Hallazgos que bloquean la integración directa

### P1: la migración V015 colisiona con main

La rama agrega `V015__club_recruitment_and_notifications.sql`, pero main ya tiene
`V015__spark_result_checkpoints.sql`. También conserva la colisión previa de V013
scouting frente a documentos de jugador en main. Esquema/versiones deben coordinarse
antes de integrar; no editar historial aplicado ni ejecutar repair para ocultarlo.
Además, el nuevo SQL modifica `pruebas` y `notificaciones`, tablas compartidas.

Fuente: `database/migrations/V015__club_recruitment_and_notifications.sql`, líneas 2–56.

### P1: se puede fabricar la afiliación que habilita el nuevo reclutamiento

`POST /usuarios-club` y `POST /usuarios-trabaja` siguen públicos. El servicio nuevo
comprueba pertenencia actual al club, lo cual es bueno, pero confía en vínculos que
un tercero pudo crear sin aprobación. Reproducción en H2: POST anónimo de una
afiliación devuelve 201. Mantener el alta administrativa de nuestra rama y diseñar
por separado solicitudes de representación, no reabrir afiliaciones por compatibilidad.

Fuente: `SeguridadConfiguration.java`, líneas 108–120; `SolicitudContactoService.java`, líneas 44–55.

### P1: contacto directo a menores sin consentimiento

El servicio nuevo comprueba actor y afiliación, pero no edad, estado, opt-in ni
tutor. Reproducción: fecha de nacimiento correspondiente a 16 años, POST contacto
desde un club válido, respuesta 201. La notificación va a la cuenta del jugador.
El directorio tampoco aplica una preferencia de visibilidad.

Fuente: `SolicitudContactoService.java`, líneas 56–72;
`JugadorService.java`, líneas 37–61; `JugadorSpecifications.java`.

### P1: autenticación global no sustituye privacidad por objeto

El cambio de `.permitAll()` a `.authenticated()` elimina varias escrituras y
lecturas anónimas, pero cualquier cuenta autenticada todavía puede leer otra
cuenta por `/usuarios/{id}`. Reproducción: una cuenta de club obtiene 200 y el
email privado del jugador sin aceptación. Se conserva además el permiso de
actualizar cuentas de jugadores desde personal de club, que incluye contraseña,
y el cambio libre de `usuarioId` al editar la ficha.

Fuentes: `SeguridadConfiguration.java`, líneas 121 y 156;
`UsuarioService.java`, método `apply`; `JugadorService.java`, línea 91.
La lectura del email se reprodujo en esta revisión; los otros dos caminos se
verificaron por inspección y ya se habían reproducido en la auditoría anterior.

### P1: ADMIN puede aceptar por el jugador

`responder` excluye explícitamente a ADMIN de la comprobación del destinatario.
Reproducción: un principal ADMIN envía ACEPTADO y obtiene 200 sobre la solicitud
del jugador. Administrar la plataforma no equivale a consentir por otra persona.

Fuente: `SolicitudContactoService.java`, líneas 89–111.

### P2: duplicación y cambios concurrentes de estado

Dos POST idénticos generan dos pendientes, ambas con 201. No hay clave idempotente,
restricción de pendiente por pareja, cooldown o cuota. La entidad tampoco lleva
`@Version` ni la respuesta bloquea la fila; aceptar y rechazar simultáneamente
puede perder una de las decisiones. La duplicación se reprodujo; el riesgo de
concurrencia se identificó por inspección, sin declararlo una prueba ejecutada.

Fuente: `SolicitudContactoService.java`, métodos `crear` y `responder`;
`entity/SolicitudContacto.java`; SQL V015, tabla `solicitudes_contacto`.

## Cambios útiles

- Directorio con filtros SQL por nombre, posición, edad y pierna, y consultas por
  lotes para posiciones y club actual. Adaptar estos datos a nuestro directorio
  consentido en lugar de exponer otro directorio que ignore preferencias.
- Convocatorias con dueño de club comprobado, postulaciones con jugador obtenido
  desde la sesión y bloqueo de la convocatoria antes de comprobar cupo.
- Restricción única de inscripción por prueba/jugador, retiro y cancelación lógica.
- Notificaciones propias y verificación de propietario al marcarlas leídas.
- Las nuevas rutas exigen autenticación y el DELETE de jugadores controla titularidad.
- No se agrega un servidor de correo ni se exponen credenciales al cliente.

## Qué todavía no resuelve

Las notificaciones son registros internos, **no emails**. No hay integración con
SMTP/proveedor ni confirmación de entrega. El modelo guarda asunto, mensaje y
respuesta libres: se acerca a una bandeja de mensajes y difiere del contacto
mínimo con aceptación e intercambio de emails autorizados.

El GET de recibidas cambia las pendientes a VISTO, por lo que una lectura tiene
efectos de escritura. Separar una operación explícita de marcar visto.

Las inscripciones no tienen versión/bloqueo al gestionar y retirar. El conteo
de cupos incluye RECHAZADO; aclarar si una postulación rechazada debe seguir
ocupando cupo antes de adoptarlo como regla. Estas dos observaciones son de
inspección, no resultados de pruebas de carrera ni acuerdos funcionales.

## Evidencia ejecutada

En una copia temporal aislada `/tmp/semillero-audit/final-nuevo`:

- Los tres tests originales de `ReclutamientoIntegrationTest` pasan.
- Cinco reproducciones temporales adicionales confirman los problemas de
  duplicados, menor sin tutor, aceptación por ADMIN, email ajeno y afiliación anónima.
- Ejecución conjunta: ocho casos, cero fallas/errores. Que las reproducciones
  pasen confirma las exposiciones; no constituyen una suite de seguridad aprobada.

Se usó H2 y datos sintéticos. No se aplicó SQL al VPS ni se usaron credenciales
seed. No se validaron estas migraciones en PostgreSQL 17 y no se declaró verde
la suite completa de backend-final. Las pruebas temporales no se incorporaron
a la rama compartida ni sobrescribieron trabajo local.

## Aplicación en nuestra rama

Se conserva la integración segura de contactos. Se implementaron bloqueos y
cuotas persistentes en las tablas propias propuestas de V017: el bloqueo impide
nuevas solicitudes, aceptación y exposición de emails, y desbloquear no restaura
autorizaciones previas. Las cuotas se reservan atómicamente por usuario, club y
destinatario; los reintentos idempotentes no consumen una cuota nueva.

No se incorporó el commit 13dadc4 entero: hacerlo ahora reabriría los problemas
de permisos descritos e introduciría colisiones SQL y un segundo flujo de contacto.
