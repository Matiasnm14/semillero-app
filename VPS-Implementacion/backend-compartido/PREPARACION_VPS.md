# Preparación para publicar el backend

Revisión del 2026-11-02. Se verificó la configuración mediante SSH y consultas SQL de solo lectura. No se instaló la aplicación, no se aplicaron migraciones ni se modificaron servicios del VPS.

## Configuración confirmada

| Recurso | Resultado |
| --- | --- |
| Cuenta | `backend`, UID 1005 |
| Plataforma | `x86_64`, Docker contexto `rootless` |
| Cuota de la cuenta | 4 CPU, memoria máxima 4 GiB y umbral 3 GiB |
| Contenedor propuesto | 1 CPU y 1 GiB |
| Disco compartido disponible | Aproximadamente 187 GiB al revisar; no es una reserva exclusiva |
| Proxy backend | Caddy apunta a `127.0.0.1:18083` |
| Puerto backend | `18083` libre al revisar; confirmar nuevamente antes de iniciar |
| Base compartida | PostgreSQL 17, base `semillero`; responde desde el host |
| Archivos | Volumen persistente para originales, PNG y temporales |
| Análisis y YouTube | Deshabilitados en el perfil `vps` |

## Pendientes que impiden iniciar

1. **Conexión desde el contenedor.** La configuración privada existente usa `jdbc:postgresql://127.0.0.1:5432/semillero`. PostgreSQL escucha en loopback del host. Docker rootless usa Slirp4netns con `--disable-host-loopback`: la conectividad desde el host no demuestra conectividad desde el contenedor. El administrador debe proporcionar una ruta autorizada desde esa red. No usar la URL actual como si ya estuviera validada, ni abrir PostgreSQL a Internet o desactivar aislamiento como solución automática.
2. **Permisos para Flyway.** El usuario `app_analisis` tiene `USAGE` en `public`, pero no `CREATE`, no pertenece a los roles propietarios de las 41 tablas y no pudo consultar `flyway_schema_history`. Con estas credenciales el arranque no puede ejecutar las migraciones automáticamente. Un `GRANT` de lectura/escritura tampoco permite los `ALTER TABLE` necesarios. El administrador debe acordar credenciales con permisos sobre el esquema y los objetos existentes, o ejecutar las mismas migraciones con el propietario y entregar permisos de aplicación completos. Cualquier cambio de propietario o privilegios requiere revisar primero a los demás consumidores de la base compartida.
3. **URL pública y CORS.** Se observaron entradas HTTP de Caddy, pero no una URL HTTPS pública confirmada. Definir origen de la web y URL de API. Si se sirven bajo el mismo origen, `CORS_ALLOWED_ORIGINS` puede quedar vacío; para orígenes diferentes se necesitan URLs exactas. Validar también límites de cuerpo y tiempos de espera del proxy antes de habilitar cargas grandes.

No adjuntar contraseñas ni el contenido de archivos `.env` a issues, PR o mensajes. El administrador puede dejar la configuración en un archivo privado de la cuenta `backend` con permisos `0600`.

## Token y secretos

El login ya emite JWT HS256 y los endpoints protegidos validan el Bearer token y los permisos del usuario. `JWT_SECRET` es el secreto de firma del backend; debe ser aleatorio, exclusivo del VPS y estable entre recreaciones. No es un token que se deba entregar a Flutter. `ANALYSIS_TOKEN` corresponde a la conexión con Spark y no hace falta mientras el análisis permanezca deshabilitado.

Se conserva un borrador privado local bajo `.local/vps/configuracion/produccion.env.pendiente`, excluido de Git. Contiene el puerto, límites y un secreto JWT generado una sola vez; deja en blanco las credenciales de DB pendientes. No es una configuración lista para iniciar. Nunca regenerar el secreto en cada actualización ni incluirlo en la imagen o en el paquete compartible de entrega.

## Versión y entrega

Después de aprobar las pruebas, crear el commit de `video-data-analysis` y una imagen `semillero-backend:<commit-corto>`. Asociar la revisión completa mediante la etiqueta OCI `org.opencontainers.image.revision`. El paquete local debe incluir la imagen exportada, Compose, plantilla sin secretos, instrucciones, revisión, ID de imagen y checksums.

La construcción se hace localmente. Crear un PR hacia `main` permite al equipo revisar el cambio; no implica fusionarlo ni desplegarlo. No ejecutar todavía `docker compose up` en el VPS.

## Despliegue posterior

Una vez resueltos los pendientes y autorizado el paso de publicación: respaldar SQL y archivos; comprobar conectividad desde la misma red del contenedor; transferir y verificar checksums; cargar la imagen; completar el `.env` privado conservando `JWT_SECRET`; validar con `docker compose config --quiet`; iniciar y revisar Flyway y salud; probar login, permisos, CORS y una carga autorizada a través del proxy. No ejecutar `init.sql` sobre la base existente ni eliminar el volumen para actualizar.
