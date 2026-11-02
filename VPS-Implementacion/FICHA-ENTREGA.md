# Ficha de entrega: backend compartido

- Repositorio: `Matiasnm14/semillero-app`.
- Artefacto: `semillero-api`, Java 21. Integra `core`, `data` y los módulos del equipo.
- Estado: configuración e imagen verificadas localmente; instalación y URL pública por confirmar.
- Cuenta SSH verificada: `backend@80.241.208.3`, Docker rootless; cuota máxima 4 GiB y cuatro CPU.
- Puerto interno: `8080`; puerto de loopback en Caddy: `18083`; prefijo REST `/api/v1`.
- Imagen: `semillero-backend:<versión>`, construida en la computadora para la arquitectura del VPS.
- Perfil: `vps`; análisis, workers y YouTube deshabilitados para esta entrega.
- Base: PostgreSQL compartido, conexión mediante `DB_URL`, `DB_USER`, `DB_PASSWORD`.
- Migraciones: Flyway V000–V012 antes de JPA; no requiere ejecutar `init.sql`.
- Autenticación: `POST /api/v1/auth/login`, token Bearer; secreto `JWT_SECRET` exclusivo del VPS.
- Web: orígenes mediante `CORS_ALLOWED_ORIGINS`; sin credenciales de DB en Flutter.
- Archivos: volumen `backend_data`, ruta interna `/var/lib/semillero` para videos, PNG y temporales de multipart/Java.
- Comprobación de salud: GET `/api/v1/paises?size=1` desde el contenedor; requiere respuesta HTTP exitosa.
- Video: carga autorizada `202`, original validado y análisis pendiente persistido; modelo externo pendiente.
- Dependencias de esta imagen: Java 21, curl, FFmpeg y FFprobe; inferencia GPU externa. Python/uploader y OAuth se incorporarán cuando se habilite YouTube.
- Pruebas: `./mvnw clean verify`, suites PostgreSQL y verificación del contenedor antes de publicar.
- Antecedente 2026-11-01: imagen anterior, 340 pruebas y 17 comprobaciones HTTP; sustituida para esta entrega por la imagen con FFmpeg y migraciones V000–V012.
- Puesta en producción pendiente: resolver acceso de PostgreSQL desde el contenedor, permisos para Flyway, copia de seguridad y URL pública/CORS. Ver [preparación del VPS](backend-compartido/PREPARACION_VPS.md).

Procedimiento completo en [`backend-compartido/README.md`](backend-compartido/README.md).

Validación actual del 2026-11-02: imagen `semillero-backend:vps-20261102-videos`, 50 pruebas y 45 comprobaciones locales del contenedor aprobadas. Carga mayor de 128 MiB, actualización de volumen anterior, original/PNG persistentes y JWT válido después de recrear. No se ha transferido al VPS.
