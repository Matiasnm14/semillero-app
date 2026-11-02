-- Nuevas cargas: original validado + análisis PENDIENTE antes de responder 202.
-- NULL conserva la procedencia desconocida de los videos históricos.
ALTER TABLE videos_partido ADD COLUMN IF NOT EXISTS usuario_carga_id bigint;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='videos_partido'::regclass AND conname='fk_video_uploader') THEN
        ALTER TABLE videos_partido ADD CONSTRAINT fk_video_uploader FOREIGN KEY (usuario_carga_id) REFERENCES users(id);
    END IF;
END;
$$;
CREATE INDEX IF NOT EXISTS idx_video_uploader ON videos_partido(usuario_carga_id);

-- Mantener el nombre usado por V001 permite adoptar una base inicializada por init.sql.
ALTER TABLE videos_partido DROP CONSTRAINT IF EXISTS videos_partido_estado_youtube_check;
ALTER TABLE videos_partido ADD CONSTRAINT videos_partido_estado_youtube_check CHECK (estado_youtube IN (
    'DESHABILITADO', 'ESPERANDO_ANALISIS', 'PENDIENTE', 'SUBIENDO', 'PROCESANDO',
    'DISPONIBLE', 'ERROR', 'REVISION_REQUERIDA'));
