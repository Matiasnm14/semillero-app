-- Ejecutar únicamente con la identidad de DB autorizada y por túnel SSH.
-- No obtiene contraseñas, hashes, documentos ni teléfonos.
BEGIN READ ONLY;
SET LOCAL statement_timeout = '10s';
SELECT current_database() AS base, current_user AS rol,
       current_setting('server_version') AS version_postgresql;
SELECT has_schema_privilege(current_user, 'public', 'USAGE') AS puede_usar_esquema,
       has_schema_privilege(current_user, 'public', 'CREATE') AS puede_crear_en_esquema;
SELECT tablename FROM pg_tables
 WHERE schemaname='public' AND (tablename LIKE 'scouting_%' OR tablename='flyway_schema_history')
 ORDER BY tablename;
SELECT table_name, column_name, data_type, is_nullable
 FROM information_schema.columns WHERE table_schema='public'
 AND table_name IN ('users','jugadores','usuarios_club','usuario_trabaja')
 ORDER BY table_name, ordinal_position;
SELECT u.id, u.email,
       (SELECT count(*) FROM jugadores j WHERE j.users_id=u.id) AS fichas,
       (SELECT count(*) FROM usuarios_club uc JOIN usuario_trabaja ut
          ON ut.usuarios_club_id=uc.id WHERE uc.users_id=u.id) AS afiliaciones
 FROM users u WHERE u.email IN ('jugador1@seed.test','club1@seed.test');
SELECT count(*) AS cuentas_con_fichas_duplicadas FROM
 (SELECT users_id FROM jugadores WHERE users_id IS NOT NULL
  GROUP BY users_id HAVING count(*)>1) duplicados;
ROLLBACK;
