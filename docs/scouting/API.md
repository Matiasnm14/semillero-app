# Scouting API

La API de scouting vive en `/api/v1/scouting`. Usa la autenticación común del backend (`Authorization: Bearer <token>`) y comparte `users`, `jugadores`, `clubes`, `partidos` y torneos por sus IDs. Los datos de scouting se guardan en tablas propias y no modifican la ficha de un jugador.

## Roles y acceso

Un administrador de Semillero no obtiene acceso a datos deportivos por tener `ADMIN`. Un administrador asigna explícitamente los roles de scouting desde `/api/v1/usuarios/{id}/roles`.

| Rol | Acceso principal |
| --- | --- |
| `SCOUT` | Prospectos asignados, captura de observaciones y referencias de evidencia. |
| `COORDINADOR_SCOUTING` | Alcances por categoría/sede, asignaciones, flujo de decisiones y operación. |
| `RECLUTADOR` | Decisiones dentro de sus asignaciones, contacto autorizado y pruebas. |
| `EVALUADOR` | Criterios versionados, observaciones y resultados de pruebas asignadas. |
| `RESPONSABLE_SALVAGUARDA` | Consentimientos dentro de los alcances que le otorgue coordinación. |
| `SCOUTING_AUDITOR` | Eventos de auditoría, alertas y estados operativos permitidos. |
| `SCOUTING_STATUS` | Estado de comunicaciones y consentimientos; nunca recibe destinatarios ni mensajes. |
| `SCOUTING_CONFIG` | Configuración técnica de scouting; no da acceso al contenido de prospectos. |

Los scouts acceden a sus prospectos asignados. Coordinación y salvaguarda requieren un alcance activo por categoría, sede o ambos. El permiso de solo estado no puede leer perfiles, observaciones, teléfonos ni mensajes. Los rechazos de acceso se registran; cinco rechazos de una cuenta en quince minutos generan una alerta consultable por auditoría y coordinación.

## Flujo habitual

1. Revisa duplicados con `GET /prospects/duplicates?firstName=...&lastName=...&birthDate=YYYY-MM-DD` y crea con `POST /prospects`. La búsqueda actual compara nombre completo normalizado y fecha exacta; solo informa coincidencias dentro del alcance del usuario.
2. Coordinación/evaluación crea un conjunto de criterios nuevo con `POST /criteria-sets` y lo publica con `POST /criteria-sets/{id}/publish`. Publicar una versión nueva retira la anterior, que sigue disponible para los informes históricos.
3. Coordinación asigna uno o más scouts con `POST /assignments`, indicando el `criteriaSetId` publicado. Una reasignación requiere `reassign: true` y un motivo; también puede asociar un partido, lugar y fecha límite. Crear un prospecto no crea una asignación de observación automáticamente.
4. El scout sincroniza `POST /observations/reports` con un `Idempotency-Key` estable y el `assignmentId` que recibió. El conjunto `criteriaSetId` debe coincidir con el formulario fijado en esa asignación. El servidor guarda el informe una sola vez si se reintenta la misma solicitud; reutilizar la clave con otro contenido produce `409`.
5. Los cambios de etapa se solicitan con `POST /prospects/{id}/stage-transitions`. `RECOMMENDED` y `DISCARDED` quedan pendientes hasta que otra persona los apruebe en `/stage-approval`.
6. Para menores, salvaguarda registra el consentimiento vigente en `/prospects/{id}/consents`. Coordinación da alcance a esa persona en `/scopes`. Revocarlo cancela en el acto las comunicaciones que aún no se enviaron.
7. Reclutamiento crea comunicaciones usando una plantilla aprobada y coordina la prueba. La etapa `CONTACTED` requiere que el proveedor institucional acepte un mensaje autorizado. La etapa `TRIAL` requiere invitación aceptada y fecha; `ACCEPTED` requiere prueba completada con resultado favorable.
8. `POST /prospects/{id}/link-player` vincula al jugador existente solo si coinciden nombre y fecha de nacimiento. La aceptación publica un evento durable `ProspectoAceptado` en el outbox.

## Rutas

| Método | Ruta | Uso |
| --- | --- | --- |
| `GET` | `/prospects?page=0&size=20&stage=DISCOVERED&category=U16` | Lista paginada dentro del alcance del usuario. `page` empieza en 0 y `size` admite hasta 100. |
| `POST` | `/prospects` | Alta con nombre, apellido, fecha de nacimiento, categoría y posición/origen opcionales. |
| `GET` | `/prospects/{id}` | Perfil del prospecto para personal con permiso de contenido. |
| `GET` | `/prospects/duplicates?firstName=&lastName=&birthDate=` | Busca coincidencias exactas sin exponer datos fuera de alcance. |
| `POST` | `/prospects/{id}/duplicate-review` | Revisión `NOT_DUPLICATE` o consolidación `MERGE` con `canonicalProspectId` y motivo. |
| `GET`, `POST` | `/assignments` | Asignación multi-scout; requiere fecha (`scheduledAt`), lugar y conjunto de criterios publicado para la categoría del prospecto. `GET /assignments/mine` lista tareas propias y su `criteriaSetId`. |
| `POST`, `PUT` | `/scopes`, `/scopes/{id}/revoke` | Otorga o revoca alcances por categoría/sede. |
| `GET`, `POST` | `/criteria-sets` | Consulta/crea una versión inmutable de criterios con cinco descriptores observables por criterio. |
| `POST` | `/criteria-sets/{id}/publish` | Publica la versión para nuevos informes. |
| `POST` | `/observations/reports` | Sincroniza el informe y sus puntajes; admite `Idempotency-Key`. |
| `GET` | `/prospects/{id}/history` | Observaciones, decisiones, contactos y `timeline` cronológico; incluye número de observaciones y scouts distintos. No calcula una nota global ni expone destinatarios o mensajes. |
| `GET` | `/prospects/{id}/context` | Contexto de torneo, partidos y métricas vigentes si ya existe vínculo con ficha. Lectura solamente. |
| `POST` | `/prospects/{id}/stage-transitions` | Solicita cambio con motivo. Para reabrir, incluye `newEvidenceReference`. |
| `POST` | `/prospects/{id}/stage-approval` | Aprueba/rechaza `RECOMMENDED` o `DISCARDED`; debe actuar otra persona. |
| `GET`, `POST` | `/prospects/{id}/consents` | Consulta estado o registra consentimiento de tutor. El alta exige `evidenceReference`; detalles del tutor solo se devuelven a salvaguarda. |
| `POST` | `/prospects/{id}/consents/{consentId}/revoke` | Revocación inmediata con motivo. |
| `GET`, `POST` | `/prospects/{id}/communications` | Solicita mensaje con plantilla aprobada; la consulta devuelve estado, nunca contenido. |
| `POST` | `/communications/{id}/retry` | Reintenta una entrega fallida, tras comprobar consentimiento vigente. |
| `GET`, `POST` | `/message-templates` | Consulta/crea una versión de plantilla. Salvaguarda/coordinación aprueba con `/{id}/approve`. |
| `POST`, `PUT` | `/trials`, `/trials/{id}/result` | Agenda y registra el resultado de una prueba. |
| `POST` | `/prospects/{id}/link-player` | Vincula a `{"playerId": 123}` si la identidad coincide con el jugador existente. |
| `POST`, `GET` | `/exports`, `/exports/{id}` | Genera/descarga JSON cifrado con motivo, secciones elegidas y marca de agua lógica; vence en hasta 60 minutos y solo quien lo solicitó lo descarga. |
| `POST` | `/prospects/{id}/anonymize` | Anonimización inmediata de un prospecto archivado; conserva trazabilidad sin PII. |
| `GET`, `POST` | `/audit-events`, `/security-alerts` | Consulta eventos/alertas; coordinación o auditoría puede reconocer una alerta. |
| `GET`, `POST` | `/outbox`, `/outbox/{id}/retry` | Consulta eventos pendientes y reintenta eventos fallidos. |
| `GET`, `POST`, `PUT` | `/config/oidc-identities` | Administración `ADMIN` de vínculos OIDC con usuarios ya existentes. |

## Ejemplos

Alta:

```json
{
  "firstName": "Alex",
  "lastName": "Pérez",
  "birthDate": "2010-05-01",
  "category": "U16",
  "primaryPosition": "DEL",
  "originClubId": 12,
  "originTournamentCategoryId": 8,
  "sourceSite": "La Paz",
  "email": "adulto@example.org",
  "phone": "+59170000000"
}
```

Los campos personales se cifran en base de datos. La búsqueda de duplicados usa una huella HMAC, no nombres ni fecha de nacimiento en claro.

Asignación:

```json
{
  "prospectId": "c144cb5f-534b-4b3d-9dce-1e536bbf60b0",
  "scoutUserIds": [42, 43],
  "criteriaSetId": "88a7dc12-1b7b-4da4-a2d0-fc1b54d8109b",
  "scheduledAt": "2026-11-05T15:00:00-04:00",
  "matchId": 12,
  "dueAt": "2026-11-05T18:00:00-04:00",
  "location": "La Paz",
  "reason": "Primera observación"
}
```

Exportación autorizada:

```json
{
  "prospectId": "c144cb5f-534b-4b3d-9dce-1e536bbf60b0",
  "reason": "Revisión del comité deportivo",
  "fields": ["profile", "history"],
  "expiresInMinutes": 30
}
```

`fields` admite `profile`, `history` y `tournamentContext`. La respuesta incluye un watermark lógico con ID de exportación, solicitante y hora de generación.

Informe offline:

```http
POST /api/v1/scouting/observations/reports
Authorization: Bearer <token>
Idempotency-Key: device-123:report-456
Content-Type: application/json
```

```json
{
  "prospectId": "c144cb5f-534b-4b3d-9dce-1e536bbf60b0",
  "assignmentId": "75a2a080-1438-4d3e-870d-a061745f0894",
  "criteriaSetId": "88a7dc12-1b7b-4da4-a2d0-fc1b54d8109b",
  "observedAt": "2026-11-02T15:30:00-04:00",
  "opponent": "Academia visitante",
  "playedPosition": "DEL",
  "minutesPlayed": 80,
  "currentPerformance": 4,
  "potential": 5,
  "confidence": "MEDIUM",
  "narrative": "Atacó espacios al lado débil.",
  "scores": [
    {"criterionId":"9c9448c2-ac88-4433-a94d-793405861abb","observed":true,"score":4,"note":"Control orientado bajo presión"},
    {"criterionId":"5478e2f5-0320-4773-ac0f-8b981cb52af0","observed":false,"score":null}
  ],
  "evidence": [
    {"type":"VIDEO","uri":"https://media.example.org/scouting/clip-123","description":"Acción de presión"}
  ]
}
```

`scores` incluye todos los criterios de esa versión. Si `observed` es `false`, `score` debe ser `null`; el backend nunca convierte no observado en cero. Evidencias deben ser URLs HTTPS sin credenciales, query ni fragmento. Cada informe conserva el ID de la versión usada.

## Etapas y reglas

`DISCOVERED → PRESELECTED → OBSERVING → OBSERVED → EVALUATED → RECOMMENDED → CONTACTED → TRIAL → ACCEPTED → INCORPORATED`.

`DISCARDED` requiere segunda revisión. `ARCHIVED` puede solicitarse desde etapas activas; archivar cierra tareas y cancela comunicaciones/pruebas pendientes. Para volver de `DISCARDED` o `ARCHIVED` a `PRESELECTED`, se requiere motivo y referencia a evidencia nueva. Un prospecto anonimizado o consolidado no se reabre. No hay selección ni puntaje automático.

Para criterios `PSYCHOLOGICAL`, registra conductas deportivas observables; el servidor rechaza términos de diagnóstico clínico. La confianza y el potencial son campos separados del rendimiento actual.

## Entrega institucional y configuración

La API mantiene en el outbox los eventos `ProspectoAceptado` hasta que el receptor confirme `2xx`. Se envía `Idempotency-Key` y un envelope JSON con `id`, `type`, `version`, `occurredAt`, `source`, `correlationId` y `data.prospectId`. El evento no contiene datos personales. En `SCOUTING_EVENTS_URL` se configura el endpoint HTTPS del módulo de ficha técnica y en `SCOUTING_EVENTS_TOKEN` su token de servicio.

Los contactos usan el endpoint institucional configurado en `SCOUTING_CONTACTS_URL`, con `SCOUTING_CONTACTS_TOKEN`, HTTPS y el mismo encabezado idempotente. El receptor acepta `{communicationId,channel,recipient,message}` y debe tratar una respuesta `2xx` como aceptación de entrega. Sin endpoint, el estado queda `PENDING_CONFIGURATION`; la API no afirma que el mensaje se envió.

| Variable | Predeterminado | Función |
| --- | --- | --- |
| `SCOUTING_ENCRYPTION_SECRET` | Deriva de `JWT_SECRET` | Clave estable para cifrado AES-GCM y huellas HMAC. En producción fija una clave propia y consérvala en respaldos; cambiarla sin migrar datos impide leer PII existente. |
| `SCOUTING_OIDC_ENABLED` | `false` | Habilita autenticación OIDC dedicada a scouting. |
| `SCOUTING_OIDC_ISSUER_URI` | vacío | Emisor OIDC esperado. |
| `SCOUTING_OIDC_AUDIENCE` | vacío | Audiencia obligatoria del token. |
| `SCOUTING_EVENTS_URL`, `SCOUTING_EVENTS_TOKEN` | vacíos | Receptor de eventos para ficha técnica. |
| `SCOUTING_CONTACTS_URL`, `SCOUTING_CONTACTS_TOKEN` | vacíos | Adaptador institucional de comunicaciones. |
| `SCOUTING_RETENTION_DAYS` | `365` | Días después del archivo antes de anonimizar registros sin tareas activas. |
| `SCOUTING_WORKERS_ENABLED` | `true` | Enciende entrega de outbox/contactos, alertas y retención. |

Para OIDC, el token debe pasar la validación de emisor, firma, vencimiento y audiencia. Un administrador vincula `subject` a un usuario Semillero existente mediante `POST /config/oidc-identities`; roles se leen de la base de datos, nunca de roles/autorizaciones declaradas por el proveedor. No hay aprovisionamiento automático de usuarios.

Las migraciones `V016__scouting_module.sql` y `V017__scouting_assignment_and_export_controls.sql` se ejecutan con Flyway al iniciar el backend. También se incluyen en `database/init.sql` para bases descartables nuevas. V017 recupera el formulario de asignaciones históricas solo cuando todos sus informes existentes usaron la misma versión; una asignación activa sin versión asociable debe volver a asignarse antes de recibir informes nuevos.
