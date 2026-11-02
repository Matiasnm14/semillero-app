-- Conserva los análisis históricos y permite recuperar los trabajos Spark por su identidad.
ALTER TABLE analysis_jobs ADD COLUMN IF NOT EXISTS remote_analysis_id varchar(64);
ALTER TABLE analysis_jobs ADD COLUMN IF NOT EXISTS segundos_procesados double precision;
ALTER TABLE analysis_jobs ADD COLUMN IF NOT EXISTS segundos_totales double precision;
ALTER TABLE analysis_jobs ADD COLUMN IF NOT EXISTS remote_cleanup_pending boolean NOT NULL DEFAULT false;
CREATE UNIQUE INDEX IF NOT EXISTS uk_analysis_remote_id ON analysis_jobs(remote_analysis_id);
CREATE INDEX IF NOT EXISTS idx_analysis_cleanup ON analysis_jobs(id) WHERE remote_cleanup_pending;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='analysis_jobs'::regclass AND conname='chk_analysis_seconds') THEN
        ALTER TABLE analysis_jobs ADD CONSTRAINT chk_analysis_seconds CHECK (
            (segundos_procesados IS NULL OR segundos_procesados >= 0 AND segundos_procesados < 'Infinity'::float8)
            AND (segundos_totales IS NULL OR segundos_totales > 0 AND segundos_totales < 'Infinity'::float8)
            AND (segundos_procesados IS NULL OR segundos_totales IS NULL OR segundos_procesados <= segundos_totales));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='analysis_jobs'::regclass AND conname='chk_analysis_cleanup') THEN
        ALTER TABLE analysis_jobs ADD CONSTRAINT chk_analysis_cleanup CHECK (
            NOT remote_cleanup_pending OR (estado = 'COMPLETADO' AND respuesta_json IS NOT NULL AND remote_analysis_id IS NOT NULL));
    END IF;
END;
$$;
