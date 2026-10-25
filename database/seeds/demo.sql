-- Datos sintéticos opcionales, para pruebas locales. No se cargan automáticamente.
-- Los identificadores se generan; no se presupone que empiecen en 1.
BEGIN;

DO $$
DECLARE
    pais_demo bigint;
    ciudad_demo bigint;
    local_demo bigint;
    visitante_demo bigint;
    jugador_local bigint;
    jugador_visitante bigint;
    partido_demo bigint;
BEGIN
    IF EXISTS (SELECT 1 FROM clubes WHERE nombre_club IN ('Demo Local', 'Demo Visitante')) THEN
        RAISE EXCEPTION 'Los clubes de demo ya existen. Se omite la carga para evitar duplicados.';
    END IF;

    INSERT INTO pais(nombre_pais) VALUES ('Bolivia') RETURNING id INTO pais_demo;
    INSERT INTO ciudad(nombre_ciudad, pais_id) VALUES ('La Paz', pais_demo) RETURNING id INTO ciudad_demo;
    INSERT INTO clubes(nombre_club, ciudad_id) VALUES ('Demo Local', ciudad_demo) RETURNING id INTO local_demo;
    INSERT INTO clubes(nombre_club, ciudad_id) VALUES ('Demo Visitante', ciudad_demo) RETURNING id INTO visitante_demo;

    INSERT INTO jugadores(pierna_habil, altura, peso, descripcion_jugador)
    VALUES ('D', 1.75, 70, 'Jugador de prueba local') RETURNING id INTO jugador_local;
    INSERT INTO jugadores(pierna_habil, altura, peso, descripcion_jugador)
    VALUES ('I', 1.80, 75, 'Jugador de prueba visitante') RETURNING id INTO jugador_visitante;

    INSERT INTO partidos(fecha_inicio, club_local_id, club_visitante_id, estado)
    VALUES (TIMESTAMP '2026-10-22 10:00:00', local_demo, visitante_demo, 'PROGRAMADO')
    RETURNING id INTO partido_demo;

    INSERT INTO participaciones_partido(partidos_id, jugadores_id, clubes_id, es_titular, dorsal)
    VALUES (partido_demo, jugador_local, local_demo, true, 9),
           (partido_demo, jugador_visitante, visitante_demo, true, 10);

    RAISE NOTICE 'Demo: partido %, jugador local %, jugador visitante %',
        partido_demo, jugador_local, jugador_visitante;
END;
$$;

COMMIT;
