-- Solo lectura. Ejecutar con psql contra la base usada realmente por el backend.
-- No migra, no repara checksums y no obtiene datos personales ni credenciales.
BEGIN READ ONLY;
SET LOCAL statement_timeout = '10s';
SELECT current_database() AS base, current_user AS rol,
       current_schema() AS esquema, current_setting('search_path') AS search_path;
SELECT to_regclass('public.flyway_schema_history') AS historial,
       to_regclass('public.inscripciones_prueba') AS tabla_backend_final,
       to_regclass('public.scouting_inscripcion_prueba') AS tabla_rama_integracion;
-- Si no hay historial, esta consulta falla y la transacción no modifica nada.
SELECT installed_rank, version, description, script, checksum, success
  FROM public.flyway_schema_history ORDER BY installed_rank;
SELECT version, count(*) AS repeticiones
  FROM public.flyway_schema_history
 WHERE type = 'SQL' AND success GROUP BY version HAVING count(*) > 1;
ROLLBACK;
