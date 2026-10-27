-- V005 crea TEXT, pero instalaciones anteriores con @Lob pueden conservar OID.
-- Convertir el contenido del objeto grande, sin convertir su identificador a texto.
-- Conservar los objetos originales: podrían estar referenciados desde otras filas.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_attribute
               WHERE attrelid='users'::regclass AND attname='foto_perfil'
                 AND atttypid='oid'::regtype AND NOT attisdropped) THEN
        ALTER TABLE users ALTER COLUMN foto_perfil TYPE text
            USING CASE WHEN foto_perfil IS NULL THEN NULL
                       ELSE convert_from(lo_get(foto_perfil), 'UTF8') END;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_attribute
               WHERE attrelid='clubes'::regclass AND attname='foto_club'
                 AND atttypid='oid'::regtype AND NOT attisdropped) THEN
        ALTER TABLE clubes ALTER COLUMN foto_club TYPE text
            USING CASE WHEN foto_club IS NULL THEN NULL
                       ELSE convert_from(lo_get(foto_club), 'UTF8') END;
    END IF;
END;
$$;
