-- Flyway aplica esta migración al arrancar el backend.
-- Es idempotente para instalaciones de desarrollo donde Hibernate creó antes
-- las columnas, y conserva los valores de imágenes que ya existan.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS foto_perfil TEXT;

ALTER TABLE clubes
    ADD COLUMN IF NOT EXISTS foto_club TEXT;
