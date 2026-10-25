-- Adaptación de HU02/HU03: respuesta final de Python conservada por Spring.
-- Aplicar una vez después de 003, también si dev ya agregó la columna.
BEGIN;

ALTER TABLE analysis_jobs ADD COLUMN IF NOT EXISTS respuesta_json text;
ALTER TABLE analysis_jobs ADD CONSTRAINT chk_analysis_response CHECK (
    respuesta_json IS NULL OR (
        estado = 'COMPLETADO' AND jsonb_typeof(respuesta_json::jsonb) IN ('object', 'array')
    )
);

COMMIT;
