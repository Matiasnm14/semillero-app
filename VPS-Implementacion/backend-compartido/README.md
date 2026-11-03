# Backend compartido en VPS

Configuración indicada por el administrador para `backend@80.241.208.3`, Docker rootless y arquitectura `linux/amd64`. Se construye la imagen localmente y se transfiere una versión identificada; no se compila en el VPS. Ver el [acuerdo de configuración](PREPARACION_VPS.md).

## Red y base compartida

Compose utiliza `network_mode: host`, sin publicación `ports`. Spring escucha exclusivamente en `127.0.0.1:18083`, igual que el healthcheck y el destino configurado en Caddy. El administrador verificó el acceso desde esta red a `jdbc:postgresql://127.0.0.1:5432/semillero`. No reemplazar esa entrada estable por `172.20.0.2`.

Las credenciales existentes están en `/home/backend/proyecto/.env.db`. El archivo no se copia a Git, a la imagen ni al paquete compartible. Compose lee ese archivo con `--env-file /home/backend/proyecto/.env.db` y después un `.env` privado del despliegue, con permisos `0600`, que conserva `JWT_SECRET`, versión y CORS sin duplicar las credenciales SQL. No ejecutar el contenido de los archivos de configuración como código shell.

El migrador central mantiene el esquema. El perfil `vps` y Compose desactivan Flyway y conservan Hibernate `validate`. El backend de esta entrega necesita el esquema compatible con V000–V013 y V015. Un error de validación se resuelve coordinando la migración central; no cambiando a `update`, activando Flyway en el runtime ni ejecutando `init.sql`. En desarrollo local continúa el flujo habitual de Flyway al iniciar.

## Imagen y almacenamiento

Java 21, curl, FFmpeg/FFprobe; UID/GID 10001, raíz de solo lectura, límite inicial de 1 GiB/una CPU. El script de arranque crea las carpetas faltantes sin elevar privilegios. El volumen `backend_data` se monta en `/var/lib/semillero`:

| Ruta interna | Uso |
| --- | --- |
| `videos` | Originales MP4 |
| `analysis` | Mapas de calor PNG |
| `tmp/multipart` | Temporales de recepción |
| `tmp/runtime` | Temporales de Java y procesos auxiliares |

Los videos no se escriben en el pequeño tmpfs `/tmp`. Reservar disco para originales y temporales concurrentes. Recrear el contenedor conserva el volumen; no usar `down -v` para actualizar ni cambiar permisos recursivamente sobre datos existentes.

La carga autorizada valida el MP4 y responde `202` con el análisis pendiente persistido. Spark y su worker están deshabilitados por defecto y pueden activarse mediante las variables de la plantilla después de aplicar V015 y configurar el token; ver [integración Spark](../../docs/INTEGRACION_SPARK_BACKEND.md). YouTube permanece deshabilitado; no se incluye Python/uploader/OAuth. Se mantiene multipart hasta 2 GiB; las cargas reanudables de 12 GiB y la limpieza automática del original están pendientes.

## Construcción y publicación

Desde el commit de la rama que se va a publicar, con el código sin modificaciones locales:

```bash
./mvnw verify
VERSION=$(git rev-parse --short=12 HEAD)
REVISION=$(git rev-parse HEAD)
docker build --platform linux/amd64 \
  --label "org.opencontainers.image.revision=$REVISION" \
  --label "org.opencontainers.image.source=https://github.com/Matiasnm14/semillero-app" \
  -f VPS-Implementacion/backend-compartido/Dockerfile \
  -t "semillero-backend:$VERSION" .
docker save --output imagen.tar "semillero-backend:$VERSION"
```

El contexto de construcción incluye solo el JAR y el script de arranque. Usar una etiqueta distinta por entrega y registrar revisión, ID de imagen y checksums del paquete. Las imágenes anteriores no contienen esta configuración de red y migración.

Preparar un directorio privado `~/proyecto/backend` con Compose, plantilla, imagen y `.env` real. Transferir y comprobar los checksums, cargar la imagen y validar sin imprimir secretos:

```bash
docker context show
docker load -i imagen.tar
docker compose --env-file /home/backend/proyecto/.env.db --env-file .env config --quiet
docker compose --env-file /home/backend/proyecto/.env.db --env-file .env up -d --no-build --pull never --wait --wait-timeout 180
docker compose --env-file /home/backend/proyecto/.env.db --env-file .env ps
curl --fail --silent http://127.0.0.1:18083/api/v1/paises?size=1
ss -ltn 'sport = :18083'
```

No ejecutar `up` hasta verificar que esa versión del esquema está instalada y que no hay otro backend usando el puerto. Si falla Hibernate, dejar detenido el contenedor y compartir solamente el error de esquema, sin contraseñas ni tokens.

## JWT, CORS y actualizaciones

`JWT_SECRET` debe ser aleatorio y estable entre despliegues. No es un JWT de usuario ni `ANALYSIS_TOKEN`. Los secretos se mantienen fuera de Git; valores con caracteres especiales deben usar comillas simples de dotenv. No imprimir `.env` ni `docker compose config` sin `--quiet`.

`PUBLIC_WEB_ORIGIN` identifica el origen HTTPS de la web publicada; su valor predeterminado es `https://semillero.mandarina-group.cc`. Se permite explícitamente porque Caddy conecta a Spring por HTTP local, aunque el navegador use HTTPS. Un `CORS_ALLOWED_ORIGINS` vacío conserva ese permiso; la variable sirve para agregar otros orígenes separados por comas. Usar orígenes sin ruta ni barra final. No es necesario habilitar confianza general en cabeceras reenviadas para esta corrección. Las rutas protegidas conservan la autenticación y los permisos por partido. Antes de habilitar videos grandes a través del proxy, revisar sus límites de cuerpo y tiempos de espera.

La validación de JWT admite exclusivamente la cabecera `alg: HS256`, además de comprobar firma y vencimiento. Los tokens ya emitidos por el backend conservan su validez hasta expirar, sin cambiar `JWT_SECRET`.

Para actualizar, cargar una imagen nueva, cambiar solo `APP_IMAGE` y recrear con Compose. Conservar secretos, base y volumen. Una reversión de imagen requiere comprobar compatibilidad con el esquema administrado centralmente.

La validación anterior de almacenamiento aprobó 45 comprobaciones locales, incluyendo MP4 de 142.608.793 bytes, actualización de volumen, permisos y original/PNG/JWT persistentes. Corresponde a la imagen anterior; la nueva configuración requiere comprobar su arranque real en el VPS. La publicación no valida inferencia, YouTube ni el acceso HTTPS público.
