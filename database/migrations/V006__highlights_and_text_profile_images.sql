-- Los perfiles almacenan texto (URL o data URI), no un Large Object PostgreSQL.
ALTER TABLE clubes
    ALTER COLUMN foto_club TYPE TEXT USING foto_club::text;

ALTER TABLE users
    ALTER COLUMN foto_perfil TYPE TEXT USING foto_perfil::text;

-- Un highlight reutiliza el registro de video del jugador y agrega sus
-- propiedades de publicación para no duplicar contenido existente.
ALTER TABLE videos
    ALTER COLUMN link_video TYPE TEXT,
    ADD COLUMN IF NOT EXISTS youtube_video_id varchar(11),
    ADD COLUMN IF NOT EXISTS visible boolean NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS created_at timestamp without time zone NOT NULL DEFAULT current_timestamp;

CREATE INDEX IF NOT EXISTS idx_videos_jugador_created
    ON videos (jugadores_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_videos_jugador_visible_created
    ON videos (jugadores_id, visible, created_at DESC);
