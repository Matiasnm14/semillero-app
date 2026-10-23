-- Punto de entrada para psql y /docker-entrypoint-initdb.d de la imagen oficial.
-- Solo para una base vacía. Para actualizar una existente, aplicar únicamente
-- las migraciones pendientes, según docs/BASE_DATOS_DOCKER.md.
\set ON_ERROR_STOP on

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public') THEN
        RAISE EXCEPTION 'La base ya tiene tablas. init.sql es solo para una base vacía; consulta las migraciones pendientes.';
    END IF;
END;
$$;

-- \ir resuelve las rutas desde este archivo, no desde el directorio del proceso.
\ir migrations/000_base.sql
\ir migrations/001_videos_partido.sql
\ir migrations/002_analysis_jobs.sql
\ir migrations/003_player_match_metrics.sql
\ir migrations/004_analysis_future_response.sql

\echo 'Semillero: esquema completo inicializado (000-004).'
