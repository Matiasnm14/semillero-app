-- Metadata owned by scouting; existing pruebas/users/jugadores keep their schemas.
CREATE TABLE IF NOT EXISTS scouting_convocatoria (
 id bigint PRIMARY KEY REFERENCES pruebas(id), titulo varchar(120) NOT NULL,
 lugar varchar(240) NOT NULL, categoria varchar(80) NOT NULL, descripcion varchar(2000) NOT NULL,
 cupos integer NOT NULL CHECK(cupos BETWEEN 1 AND 1000), limite_inscripcion timestamptz NOT NULL,
 estado varchar(20) NOT NULL CHECK(estado IN ('ABIERTA','EN_CURSO','CERRADA','CANCELADA')),
 edad_min integer CHECK(edad_min BETWEEN 18 AND 100), edad_max integer CHECK(edad_max BETWEEN 18 AND 100),
 creador_id bigint NOT NULL REFERENCES users(id), creada_en timestamptz NOT NULL, version bigint NOT NULL DEFAULT 0,
 CHECK(edad_min IS NULL OR edad_max IS NULL OR edad_min<=edad_max)
);
CREATE TABLE IF NOT EXISTS scouting_convocatoria_posicion (
 convocatoria_id bigint NOT NULL REFERENCES scouting_convocatoria(id), posicion varchar(80) NOT NULL,
 PRIMARY KEY(convocatoria_id,posicion)
);
CREATE TABLE IF NOT EXISTS scouting_convocatoria_requisito (
 convocatoria_id bigint NOT NULL REFERENCES scouting_convocatoria(id), requisito varchar(240) NOT NULL,
 PRIMARY KEY(convocatoria_id,requisito)
);
CREATE TABLE IF NOT EXISTS scouting_inscripcion_prueba (
 id uuid PRIMARY KEY, convocatoria_id bigint NOT NULL REFERENCES scouting_convocatoria(id),
 jugador_id bigint NOT NULL REFERENCES jugadores(id), estado varchar(24) NOT NULL,
 nota varchar(500), creada_en timestamptz NOT NULL, asistencia varchar(20) NOT NULL,
 observaciones varchar(1000), evaluada_en timestamptz, version bigint NOT NULL DEFAULT 0,
 CONSTRAINT uk_sc_inscripcion UNIQUE(convocatoria_id,jugador_id),
 CHECK(estado IN ('PENDIENTE','PRESELECCIONADO','ACEPTADO','RECHAZADO','RETIRADO')),
 CHECK(asistencia IN ('SIN_REGISTRAR','PRESENTE','AUSENTE'))
);
CREATE TABLE IF NOT EXISTS scouting_evaluacion_prueba (
 id uuid PRIMARY KEY, inscripcion_id uuid NOT NULL REFERENCES scouting_inscripcion_prueba(id),
 evaluador_id bigint NOT NULL REFERENCES users(id), fecha timestamptz NOT NULL,
 asistencia varchar(20) NOT NULL CHECK(asistencia IN ('PRESENTE','AUSENTE')),
 resultado varchar(24) NOT NULL CHECK(resultado IN ('PENDIENTE','PRESELECCIONADO','ACEPTADO','RECHAZADO')),
 observaciones varchar(1000) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_sc_convocatoria_fecha ON scouting_convocatoria(creada_en DESC,id);
CREATE INDEX IF NOT EXISTS idx_sc_inscripcion_lista ON scouting_inscripcion_prueba(convocatoria_id,creada_en DESC,id);
CREATE INDEX IF NOT EXISTS idx_sc_evaluacion_historial ON scouting_evaluacion_prueba(inscripcion_id,fecha DESC,id);
