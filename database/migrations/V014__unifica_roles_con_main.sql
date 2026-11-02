-- Unifica los roles con el resto de los módulos: las cuatro categorías del
-- login común (ADMINISTRADOR_GENERAL, JUGADOR, USUARIO_CLUB, USUARIO_SISTEMA).
--
-- Solo USUARIO_SISTEMA se guarda en la tabla roles (marca a una cuenta
-- operativa); las otras tres se derivan de administrador, jugadores y
-- usuarios_club. Se sacan los códigos viejos de la HU 16 (ADMIN, ORGANIZADOR,
-- ENTRENADOR, MESA_CONTROL, CONSULTA): toda cuenta que tenía alguno queda
-- marcada como USUARIO_SISTEMA. Idempotente.

INSERT INTO roles (codigo, nombre) VALUES ('USUARIO_SISTEMA', 'Usuario del sistema')
    ON CONFLICT (codigo) DO NOTHING;

INSERT INTO usuarios_roles (users_id, roles_id)
SELECT DISTINCT ur.users_id, (SELECT id FROM roles WHERE codigo = 'USUARIO_SISTEMA')
FROM usuarios_roles ur
WHERE ur.roles_id IN (
    SELECT id FROM roles WHERE codigo IN ('ADMIN', 'ORGANIZADOR', 'ENTRENADOR', 'MESA_CONTROL', 'CONSULTA'))
ON CONFLICT (users_id, roles_id) DO NOTHING;

DELETE FROM usuarios_roles WHERE roles_id IN (
    SELECT id FROM roles WHERE codigo IN ('ADMIN', 'ORGANIZADOR', 'ENTRENADOR', 'MESA_CONTROL', 'CONSULTA'));

DELETE FROM roles WHERE codigo IN ('ADMIN', 'ORGANIZADOR', 'ENTRENADOR', 'MESA_CONTROL', 'CONSULTA');
