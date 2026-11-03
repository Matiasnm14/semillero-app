# HU-13: integración de contactos y cierre de accesos compartidos

Base: `main` remoto `d2ca1c2`. Rama: `scouting/HU-13-integracion-segura`.
Se recuperó el módulo de `scouting/backend-inicial` sin incorporar la implementación
incompatible de prospectos de `scouting/backend-final`.

## Cambio de comportamiento

- Solo ADMIN crea o cambia afiliaciones en `usuarios-club` y `usuarios-trabaja`.
  Crear una cuenta no permite atribuirse la representación de un club existente.
- `/usuarios` (listado) requiere ADMIN. `/usuarios/{id}` exige titular o ADMIN,
  también en lectura. Un representante no puede cambiar la contraseña de un jugador.
- `/jugadores` (listado) requiere ADMIN; el detalle y la edición deportiva requieren
  titular, personal actualmente vinculado al club del fichaje vigente o ADMIN.
- La edición deportiva no cambia `usuarioId` si el actor no es ADMIN. Omitirlo
  conserva el titular existente. La reasignación administrativa sigue disponible.
- El PUT de clubes exige pertenencia actual o ADMIN; la revocación se comprueba
  en DB y no depende del `clubId` antiguo en el JWT.
- El borrado/alta de fichas y las escrituras compartidas no enumeradas requieren ADMIN.
  Los catálogos públicos, las lecturas deportivas expresas y los contratos protegidos
  de torneos/video se conservan. Highlights conserva su autorización en el servicio.
- `/scouting/**` incorpora directorio de adultos visibles, preferencias propias,
  guardados privados y solicitudes con decisión, versión e idempotencia.

**Incompatibilidad deliberada con Flutter actual:** contactos debe consultar
`/scouting/jugadores`, no listar `/jugadores` y luego descargar `/usuarios/{id}`.
El personal de club ya no edita cuentas privadas. El alta pública antigua que
encadenaba cuentas, fichas y afiliaciones deja de ser un mecanismo permitido:
el registro de representantes necesita un flujo de solicitud/aprobación y el alta
de fichas queda administrativa en esta entrega. No desplegar solo el backend
sin coordinar esos cambios con frontend. El registro genérico de cuentas continúa
disponible; no concede rol ni afiliación por sí mismo.

El contrato de contactos está en [API_CONTACTOS.md](API_CONTACTOS.md).
El correo sigue en outbox pendiente de configuración: **no hay envío real**.
Menores y edad desconocida quedan bloqueados hasta implementar tutor verificado.

## DB y migración

`V016__scouting_contactos.sql` crea seis tablas propias. V017 agrega dos tablas
propias de bloqueos y cuotas, sin modificar V016. No se cambian scripts
históricos, roles ni tablas maestras. V011 ya está ocupada por Spark y V013 por
documentos de jugador. **V016 es una propuesta pendiente de confirmar contra
el historial real y de reservar con el administrador.** No usar Flyway repair.

El runtime VPS mantiene Flyway desactivado y Hibernate en validate. El administrador
aplica el esquema con el migrador central y respaldo previo; después se publica el
backend. Como se agregan entidades JPA, no publicar el JAR antes del esquema.
Preparar recuperación con la imagen anterior; no borrar tablas para volver atrás.

El acceso SSH intentado con las identidades locales fue rechazado con
`Permission denied (publickey)` en backend y scouting. La huella del host coincide
con la guía. Se preparó una nueva clave local y una solicitud privada de alta;
**todavía no se inspeccionó la DB remota ni se aplicó SQL**.

Después de habilitar SSH, usar la configuración privada del equipo y un túnel
local, nunca publicar PostgreSQL ni copiar credenciales a Git. El script
`scripts/scouting/inspeccion-db.sql` consulta solo metadatos y cuentas sintéticas
con una transacción de solo lectura. No ejecutar tests de integración sobre la
base compartida: usan borrados/recreación de esquema en bases desechables.

## Integración manual sin CI/CD

1. El equipo revisa el diff de esta rama, especialmente seguridad compartida y los
   contratos afectados de Flutter. El responsable de DB confirma la versión SQL.
2. Ejecutar pruebas locales y adjuntar resultados al PR o revisión manual. Las pruebas
   de seguridad nuevas son `ScoutingSecurityRegressionTest`; contactos tiene 27 casos.
3. Antes de publicar, correr `./mvnw clean verify` con Java 21, FFmpeg/ffprobe y las
   pruebas PostgreSQL habilitadas en una base **desechable PostgreSQL 17**.
4. El responsable del repositorio integra la rama a main mediante PR o merge revisado.
   Resolver conflictos en la rama y repetir las pruebas si cambian contratos.
5. El responsable de DB aplica la migración acordada. El responsable del backend
   construye/publica una imagen del commit exacto integrado y actualiza el servicio
   común. Un merge **no** ejecuta ninguno de esos pasos automáticamente.
6. Comprobar login, directorio, opt-in, solicitud, aceptación y denegación entre clubes;
   comunicar commit desplegado, URL, migración y resultado. Luego actualizar Flutter.

No se agregó CI/CD ni se hizo push, merge o despliegue durante esta corrección.
Los cambios a fixtures administrativos de torneos solo preparan datos compartidos;
las peticiones que verifican permisos del módulo mantienen su actor original.

## Pendientes funcionales posteriores

Conectar Flutter al contrato, registro/aprobación de representantes, alta de jugador
autenticada y atómica, verificación de email, proveedor de correo, despacho con
reintentos, denuncia, límite por IP, expiración, retención y tutor.
Bloqueos y cuotas persistentes por usuario/club/destinatario ya están implementados;
el contrato y los valores técnicos iniciales están en API_CONTACTOS.md.
Esta entrega cierra accesos concretos; no declara terminado todo el MVP ni cumplimiento
integral de OWASP/NIST.

## Validación de esta entrega

La regresión local ejecutada reportó 512 casos, 0 fallas, 0 errores y 20 omitidos.
Incluye los 27 casos de contactos y siete regresiones nuevas de seguridad; se
conservó la prueba anterior de alta de representante, cambiando su expectativa
para exigir rechazo cuando no hay autorización.

En este entorno no hay FFmpeg/ffprobe. Se excluyeron explícitamente
`VideoPartidoIntegrationTest`, `SparkWorkflowIntegrationTest` y
`VideoPermissionsIntegrationTest`. Los casos PostgreSQL condicionados por entorno
permanecieron omitidos. No equivale a la validación completa requerida antes de push
o despliegue. El frontend todavía necesita adaptar las llamadas indicadas arriba.

### Segunda corrección: bloqueo y cuotas

Se agregaron seis casos de API (33 de contactos en total): bloqueo propio/idempotente,
no reactivación del consentimiento al desbloquear, cancelación de invitación
preparada, cuotas compartidas por usuario/club/destinatario y concurrencia.
La regresión limpia reportó 518 casos, 0 fallas, 0 errores y los mismos 20 omitidos,
con las tres exclusiones de FFmpeg indicadas anteriormente. V017 no se aplicó al VPS.
La revisión del nuevo push externo está en REVISION_BACKEND_FINAL_13dadc4.md.
