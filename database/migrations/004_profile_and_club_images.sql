-- Imágenes persistentes para perfiles de usuario y clubes.
-- Aplicar una vez en PostgreSQL antes de iniciar producción con ddl-auto=validate.
-- IF NOT EXISTS permite ejecutarla también si el perfil dev ya creó las columnas.
BEGIN;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS foto_perfil TEXT;

ALTER TABLE clubes
    ADD COLUMN IF NOT EXISTS foto_club TEXT;

COMMIT;
