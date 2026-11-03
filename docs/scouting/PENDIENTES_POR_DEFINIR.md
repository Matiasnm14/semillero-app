# Scouting: datos pendientes de otros responsables

Esta lista registra decisiones y contratos que no se deben inventar dentro del backend. El comportamiento implementado hoy se describe en [API.md](API.md).

## Decisiones funcionales

- **Duplicados similares:** el backend compara el nombre completo normalizado y la fecha exacta de nacimiento. Para buscar coincidencias aproximadas, el equipo de scouting debe definir los campos, el umbral de similitud y quién revisa una coincidencia de otra sede o categoría. Hasta entonces, la API solo informa candidatos dentro del alcance del usuario.
- **Propósito de recolección:** el informe pide conservar la finalidad de la captura, pero no define un catálogo ni una política de valores. El responsable funcional debe entregar esos valores antes de agregar el campo; no aceptar texto libre sensible por defecto.
- **Campañas de observación:** el informe menciona campañas, pero las historias priorizadas solo describen asignaciones. El responsable funcional debe definir campos, estados y relación entre campaña y tareas antes de crear ese recurso.
- **Respuesta segura del tutor a invitaciones de prueba:** el backlog amplio pide aceptar o rechazar por canal seguro, pero no define autenticación del tutor, vencimiento del enlace ni tratamiento de revocaciones. La API actual acepta una afirmación del reclutador (`invitationAccepted`); no equivale a una respuesta autenticada del tutor y no debe contarse como cumplimiento de ese criterio hasta definir e implementar el flujo seguro.
- **Alcance del consentimiento:** las historias piden canal y evidencia; el informe amplio añade alcance y preferencias. El responsable de salvaguarda debe precisar esas opciones antes de almacenarlas o usarlas para decidir destinatarios.
- **Política de retención:** el valor técnico actual es 365 días después del archivo. El responsable de salvaguarda debe confirmar o reemplazar ese plazo y definir excepciones de conservación.
- **Exportación:** la API exige motivo y permite elegir `profile`, `history` y `tournamentContext`; la marca de agua lógica identifica exportación, solicitante y hora. El responsable funcional debe confirmar si necesita secciones o campos adicionales.

## Contratos externos

- **Identidad institucional OIDC:** proveedor, issuer, audience, configuración de cliente móvil y proceso para vincular `subject` con usuarios Semillero. Los secretos y valores de producción se inyectan por entorno, nunca en Git.
- **Mensajería institucional:** URL HTTPS, esquema de autenticación, canales y respuesta que confirma aceptación para `SCOUTING_CONTACTS_URL`.
- **Ficha técnica:** URL HTTPS del consumidor del evento `ProspectoAceptado`, token de servicio y reglas de idempotencia para `SCOUTING_EVENTS_URL`.
- **Identidad al aceptar:** confirmar si `ProspectoAceptado` crea una ficha nueva o solicita vincular una existente, y qué sistema es dueño de la resolución de duplicados. Hoy el evento se genera al aceptar y la API también ofrece un vínculo explícito a un jugador existente.
- **Torneos y rendimiento:** contratos API y responsables de `clubes`, torneos, partidos y métricas, o confirmación de que esos datos seguirán siendo propiedad del mismo backend. El contexto actual consulta las tablas locales; no se inventó una API externa.

## Verificación en entorno de aceptación

El informe propone objetivos de rendimiento, disponibilidad, carga, restauración y controles ASVS/MASVS. Para certificar esos objetivos hace falta un entorno representativo, datos de prueba y responsables que acepten los umbrales; la suite funcional local no equivale a esa certificación.
