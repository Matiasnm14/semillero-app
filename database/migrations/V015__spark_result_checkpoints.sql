-- V014 pertenecía a una propuesta retirada de roles: no reutilizar esa versión.
-- Un JSON ya recibido debe sobrevivir a la entrega del segundo recurso y a reinicios.
ALTER TABLE analysis_jobs ADD COLUMN IF NOT EXISTS estado_heatmap varchar(24);
UPDATE analysis_jobs SET estado_heatmap = CASE
    WHEN estado IN ('PENDIENTE', 'PROCESANDO') THEN 'PENDIENTE' ELSE 'DESCONOCIDO' END
    WHERE estado_heatmap IS NULL;
ALTER TABLE analysis_jobs ALTER COLUMN estado_heatmap SET DEFAULT 'PENDIENTE';
ALTER TABLE analysis_jobs ALTER COLUMN estado_heatmap SET NOT NULL;
-- V004 solo admitía JSON al finalizar; ahora también es un checkpoint de un trabajo remoto activo.
ALTER TABLE analysis_jobs DROP CONSTRAINT IF EXISTS chk_analysis_response;
ALTER TABLE analysis_jobs ADD CONSTRAINT chk_analysis_response CHECK (
    respuesta_json IS NULL OR (
        (estado = 'COMPLETADO' OR (estado = 'PROCESANDO' AND enviado AND remote_analysis_id IS NOT NULL))
        AND jsonb_typeof(respuesta_json::jsonb) IN ('object', 'array')
    )
);
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='analysis_jobs'::regclass AND conname='chk_analysis_heatmap') THEN
        ALTER TABLE analysis_jobs ADD CONSTRAINT chk_analysis_heatmap CHECK (
            estado_heatmap IN ('DESCONOCIDO', 'PENDIENTE', 'DISPONIBLE', 'NO_DISPONIBLE', 'ERROR_DESCARGA'));
    END IF;
END;
$$;
