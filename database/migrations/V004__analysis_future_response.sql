-- Migración administrada por Flyway; compatible con instalaciones previas.
ALTER TABLE analysis_jobs ADD COLUMN IF NOT EXISTS respuesta_json text;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='analysis_jobs'::regclass AND conname='chk_analysis_response') THEN
        ALTER TABLE analysis_jobs ADD CONSTRAINT chk_analysis_response CHECK (
    respuesta_json IS NULL OR (
        estado = 'COMPLETADO' AND jsonb_typeof(respuesta_json::jsonb) IN ('object', 'array')
    )
);
    END IF;
END;
$$;
