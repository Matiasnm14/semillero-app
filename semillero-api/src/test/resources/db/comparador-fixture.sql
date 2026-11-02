INSERT INTO users(id,name,email,password_hash,nombres,apellidos) VALUES
 (70001,'stats-a','stats-a@example.test','no-login-test','Jugador','Prueba A'),
 (70002,'stats-b','stats-b@example.test','no-login-test','Jugador','Prueba B'),
 (70003,'stats-rival','stats-rival@example.test','no-login-test','Jugador','Rival'),
 (70004,'stats-banco','stats-banco@example.test','no-login-test','Jugador','Suplente');
INSERT INTO clubes(id,nombre_club) VALUES (71001,'Estadisticas Local'),(71002,'Estadisticas Visitante');
INSERT INTO jugadores(id,users_id,fecha_nacimiento,altura,peso) VALUES
 (72001,70001,'2011-01-01',1.58,49),(72002,70002,'2011-02-01',1.57,48),
 (72003,70003,'2011-03-01',1.60,50),(72004,70004,'2011-04-01',1.59,49);
INSERT INTO partidos(id,fecha_inicio,fecha_fin,club_local_id,club_visitante_id,estado,goles_local,goles_visitante)
VALUES (73001,'2026-10-20 10:00:00','2026-10-20 11:30:00',71001,71002,'FINALIZADO',3,0);
INSERT INTO participaciones_partido(id,partidos_id,jugadores_id,clubes_id,es_titular,dorsal,
                                  minuto_entrada,minuto_salida,minutos_jugados) VALUES
 (74001,73001,72001,71001,true,9,0,80,80),
 (74002,73001,72002,71001,true,11,0,90,90),
 (74003,73001,72003,71002,true,4,0,90,90),
 (74004,73001,72004,71001,false,12,0,0,0);
INSERT INTO eventos_partido(id,partidos_id,tipo,minuto,participaciones_partido_id,
                           participacion_rel_id,clubes_id,tipo_gol,users_id) VALUES
 (75001,73001,'GOL',10,74001,74002,71001,'JUGADA',70001),
 (75002,73001,'GOL',20,74001,NULL,71001,'JUGADA',70001),
 (75003,73001,'GOL',30,74002,74001,71001,'JUGADA',70001);
