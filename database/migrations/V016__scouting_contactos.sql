-- Scouting: extensión aditiva. No altera tablas o migraciones de los otros equipos.
-- Los IDs de usuarios, clubes y jugadores se validan en servicios; no se escriben sus tablas.
CREATE TABLE IF NOT EXISTS scouting_preferencia (
    jugador_id bigint PRIMARY KEY, visible boolean NOT NULL, acepta_solicitudes boolean NOT NULL,
    actualizada_en timestamptz NOT NULL, version bigint NOT NULL DEFAULT 0,
    CONSTRAINT chk_sc_preferencia CHECK (NOT acepta_solicitudes OR visible)
);
CREATE TABLE IF NOT EXISTS scouting_solicitud (
    id uuid PRIMARY KEY, club_id bigint NOT NULL, jugador_id bigint NOT NULL,
    solicitante_id bigint NOT NULL, clave uuid NOT NULL, motivo varchar(1000) NOT NULL,
    estado varchar(15) NOT NULL, creada_en timestamptz NOT NULL, actualizada_en timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_sc_solicitud_idempotencia UNIQUE (solicitante_id, clave),
    CONSTRAINT chk_sc_solicitud_estado CHECK (estado IN ('PENDIENTE','ACEPTADA','RECHAZADA','CANCELADA')),
    CONSTRAINT chk_sc_solicitud_motivo CHECK (length(trim(motivo)) > 0)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sc_pendiente ON scouting_solicitud(club_id,jugador_id) WHERE estado='PENDIENTE';
CREATE INDEX IF NOT EXISTS idx_sc_solicitud_jugador ON scouting_solicitud(jugador_id,creada_en DESC,id);
CREATE INDEX IF NOT EXISTS idx_sc_solicitud_club ON scouting_solicitud(club_id,creada_en DESC,id);
CREATE INDEX IF NOT EXISTS idx_sc_solicitud_par ON scouting_solicitud(club_id,jugador_id,creada_en DESC);
CREATE TABLE IF NOT EXISTS scouting_evento (
    id uuid PRIMARY KEY, solicitud_id uuid NOT NULL REFERENCES scouting_solicitud(id), actor_id bigint NOT NULL,
    anterior varchar(15), nuevo varchar(15) NOT NULL, motivo varchar(500) NOT NULL, fecha timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_sc_evento ON scouting_evento(solicitud_id,fecha DESC,id);
CREATE TABLE IF NOT EXISTS scouting_correo (
    id uuid PRIMARY KEY, evento_id uuid NOT NULL REFERENCES scouting_evento(id), destinatario_id bigint NOT NULL,
    plantilla varchar(30) NOT NULL, estado varchar(30) NOT NULL, creada_en timestamptz NOT NULL,
    CONSTRAINT uk_sc_correo_evento UNIQUE(evento_id,destinatario_id),
    CONSTRAINT chk_sc_correo_estado CHECK (estado='PENDIENTE_CONFIGURACION')
);
CREATE INDEX IF NOT EXISTS idx_sc_correo_pendiente ON scouting_correo(estado,creada_en,id);
CREATE TABLE IF NOT EXISTS scouting_guardado (
    id uuid PRIMARY KEY, club_id bigint NOT NULL, jugador_id bigint NOT NULL,
    creado_por bigint NOT NULL, creado_en timestamptz NOT NULL,
    CONSTRAINT uk_sc_guardado UNIQUE(club_id,jugador_id)
);
CREATE INDEX IF NOT EXISTS idx_sc_guardado_club ON scouting_guardado(club_id,creado_en DESC,id);

CREATE TABLE IF NOT EXISTS scouting_acceso (
    id uuid PRIMARY KEY, solicitud_id uuid NOT NULL REFERENCES scouting_solicitud(id), actor_id bigint NOT NULL,
    proposito varchar(300) NOT NULL, fecha timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_sc_acceso ON scouting_acceso(solicitud_id,fecha DESC,id);
