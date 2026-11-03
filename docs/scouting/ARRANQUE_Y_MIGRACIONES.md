# Diagnóstico: falta inscripciones_prueba

Revisión del 03/11/2026 tras `git fetch`. La entidad `InscripcionPrueba` y la
tabla `inscripciones_prueba` pertenecen a `scouting/backend-final`, no a nuestra
rama `scouting/HU-13-integracion-segura`. Se comprobó la otra rama en una copia
aislada; no se fusionaron ramas ni se modificó Flutter.

## Comprobaciones solicitadas

1. **Migración publicada:** el commit remoto `25ad48e` contiene
   `database/migrations/V018__club_recruitment_and_notifications.sql`, con
   `CREATE TABLE inscripciones_prueba`, índices, FK y unicidad prueba/jugador.
   Maven lo empaqueta bajo `db/migration` aunque el archivo no tenga el nombre
   exacto de la tabla.
2. **Versiones:** ese commit no contiene ninguna V014 ni versiones duplicadas.
   Su secuencia es V000–V013 y V015–V018; los saltos son válidos. `origin/main`
   llega a V015. **La última versión aplicada en el VPS sigue sin comprobarse**:
   no se obtuvo acceso a esa DB. Se deben comparar versión, script y checksum
   del historial real, no deducirlos de los archivos del repositorio.
3. **Flyway en dev:** se compiló ese commit y arrancó con PostgreSQL 17.10 en
   Docker, perfil `dev` y Hibernate `validate`. Flyway aplicó 18 migraciones a
   una base vacía y la aplicación arrancó. En otra base se limitó Flyway a V017:
   el arranque falló exactamente con
   `Schema validation: missing table [inscripciones_prueba]`. Sobre esa misma
   base, el mismo JAR sin ese límite aplicó únicamente V018, creó la tabla y
   arrancó correctamente. Se verificó también el registro V018 exitoso en DB.

Esto confirma el SQL y el orden Flyway → Hibernate, pero no la configuración
del entorno que reportó el error.

## Qué revisar en el entorno que falla

Ejecutar [diagnostico-migraciones.sql](../../scripts/scouting/diagnostico-migraciones.sql)
con la conexión autorizada a la **misma DB del backend**. Es de solo lectura e
informa base, esquema, tablas e historial. Si no existe historial, esa consulta
falla sin modificar la base.

- `dev` activa Flyway por la autoconfiguración de Spring Boot. Revisar overrides
  que lo desactiven, `spring.flyway.target=17` o ubicaciones distintas.
- **`vps` desactiva Flyway explícitamente:** el runtime solo valida. El responsable
  del migrador central debe aplicar la migración antes de publicar el JAR.
  Subir código o hacer merge no ejecuta ese paso automáticamente.
- El JAR de `backend-final` debe contener
  `BOOT-INF/classes/db/migration/V018__club_recruitment_and_notifications.sql`.
  Reconstruir desde la raíz de esa rama con `./mvnw clean package -DskipTests`
  si el artefacto no la incluye. Maven copia SQL externo al módulo.
- Si aparecen dos V014, identificar sus rutas. No están en el árbol de `25ad48e`;
  pueden pertenecer a otro checkout o ser recursos antiguos en `target`. `clean`
  limpia los recursos generados. Esta explicación es una hipótesis, no una causa
  comprobada en el entorno reportado.

No usar `ddl-auto=update`, crear solo la tabla a mano, hacer `Flyway repair` ni
renumerar scripts ya aplicados. Si el historial registra V018 pero falta la tabla,
o su script/checksum es de otra rama, reconciliar esquema e historial con el
responsable de la base antes de seguir.

## Ramas incompatibles

Nuestra rama tiene otras V016 y V017 y una V018 nueva que crea
`scouting_inscripcion_prueba`, con UUID y evaluación individual. `backend-final`
usa `inscripciones_prueba`, con IDs numéricos y otro contrato. No intercambiar
sus JAR ni combinar sus migraciones en la misma DB sin una migración de integración
acordada. Agregar una tabla con el nombre del error a nuestra rama no resuelve
esa incompatibilidad.
