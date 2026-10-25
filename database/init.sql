-- Utilidad manual para una base vacía; el arranque normal usa Flyway desde Spring.
-- Solo para una base vacía. Para actualizar una existente, arrancar Spring;
-- Flyway aplica las migraciones pendientes según docs/BASE_DATOS_DOCKER.md.
\set ON_ERROR_STOP on

BEGIN;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public') THEN
        RAISE EXCEPTION 'La base ya tiene tablas. init.sql es solo para una base vacía; consulta las migraciones pendientes.';
    END IF;
END;
$$;

-- \ir resuelve las rutas desde este archivo, no desde el directorio del proceso.
\ir migrations/V000__base.sql
\ir migrations/V001__videos_partido.sql
\ir migrations/V002__analysis_jobs.sql
\ir migrations/V003__player_match_metrics.sql
\ir migrations/V004__analysis_future_response.sql

COMMIT;

\echo 'Semillero: esquema completo inicializado (000-004).'
