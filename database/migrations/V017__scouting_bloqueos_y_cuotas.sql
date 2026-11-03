-- New version: never edits V016 or other teams' tables. Numbers pending DB coordination.
CREATE TABLE IF NOT EXISTS scouting_bloqueo (
    id varchar(80) PRIMARY KEY,
    jugador_id bigint NOT NULL,
    club_id bigint NOT NULL,
    activo boolean NOT NULL,
    bloqueado_en timestamptz NOT NULL,
    desbloqueado_en timestamptz,
    CONSTRAINT uk_sc_bloqueo_par UNIQUE(jugador_id, club_id),
    CONSTRAINT chk_sc_bloqueo_fechas CHECK (
        (activo AND desbloqueado_en IS NULL) OR
        (NOT activo AND desbloqueado_en IS NOT NULL AND desbloqueado_en >= bloqueado_en))
);
CREATE INDEX IF NOT EXISTS idx_sc_bloqueo_jugador ON scouting_bloqueo(jugador_id,activo,bloqueado_en DESC,id);
CREATE TABLE IF NOT EXISTS scouting_cuota (
    id varchar(100) PRIMARY KEY,
    fecha date NOT NULL,
    usadas integer NOT NULL CHECK(usadas >= 0)
);
CREATE INDEX IF NOT EXISTS idx_sc_cuota_fecha ON scouting_cuota(fecha);
