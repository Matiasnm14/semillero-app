# Backend compartido: imagen y almacenamiento para VPS

Cuenta de despliegue: `backend@80.241.208.3`, Docker en contexto `rootless`. El acceso fue comprobado; la publicación de esta imagen y su URL siguen pendientes. El responsable del backend publica una versión conjunta del repositorio, sin levantar copias por módulo.

Esta entrega agrega FFmpeg/FFprobe y almacenamiento de disco para originales, PNG y temporales. Las cargas autorizadas pueden responder `202` con un análisis persistido pendiente, aunque Spark esté deshabilitada. El modelo y YouTube continúan apagados en el perfil `vps`; no se incluye aún Python, uploader ni OAuth. El cliente debe mostrar que un video recibido todavía puede estar pendiente de análisis.

## Componentes

- `Dockerfile`: Java 21, curl, FFmpeg y FFprobe; aplicación ejecutada como UID/GID 10001.
- `entrypoint.sh`: crea las carpetas faltantes con el mismo usuario, también en un volumen de una versión anterior, y arranca Java con su temporal en disco. No requiere root ni cambia permisos de datos existentes.
- `compose.yaml`: una API en loopback, reinicio automático, raíz de solo lectura, volumen `backend_data`, comprobación de salud y límites de recursos.
- Perfil `vps`: credenciales explícitas, Hibernate `validate`, migraciones Flyway y CORS configurable.
- `.env.example`: plantilla sin credenciales reales. Configurar sus valores en un archivo privado fuera de Git.

## Carpetas persistentes

El volumen Docker se monta en `/var/lib/semillero`:

| Ruta interna | Contenido |
| --- | --- |
| `/var/lib/semillero/videos` | Originales y recibos de YouTube cuando se habilite |
| `/var/lib/semillero/analysis` | Mapas de calor PNG |
| `/var/lib/semillero/tmp/multipart` | Archivos temporales de recepción multipart |
| `/var/lib/semillero/tmp/runtime` | Temporales de Java y procesos auxiliares |

Compose configura `VIDEO_STORAGE_DIR`, `ANALYSIS_ARTIFACTS_DIR`, `SPRING_SERVLET_MULTIPART_LOCATION`, `JAVA_IO_TMPDIR` y `TMPDIR`. El script de arranque aplica `java.io.tmpdir` al proceso Java. El `/tmp` de 128 MiB permanece para operaciones pequeñas; los archivos de video no se escriben allí ni consumen ese tmpfs.

Recrear el contenedor conserva los datos del volumen y los registros de PostgreSQL. Respaldar ambos. La copia de un MP4 recibida por el servidor y el original preparado pueden coexistir durante la aceptación: reservar disco para temporales además de los originales, considerando cargas concurrentes. El temporal multipart se elimina normalmente al terminar la petición. Una caída puede dejar temporales que requieren conciliación; no se borra indiscriminadamente la carpeta al arrancar.

El script permite completar carpetas ausentes en un volumen previo cuyo directorio pertenece a UID 10001. Un volumen con otro propietario debe revisarse antes de publicar: el contenedor no eleva privilegios ni hace un `chown` recursivo sobre datos existentes.

## Construcción local

Con Java 21 y Docker, desde la raíz del repositorio:

```bash
./mvnw clean verify
VERSION=$(git rev-parse --short=12 HEAD)
REVISION=$(git rev-parse HEAD)
docker build --platform linux/amd64 \
  --label "org.opencontainers.image.revision=$REVISION" \
  --label "org.opencontainers.image.source=https://github.com/Matiasnm14/semillero-app" \
  -f VPS-Implementacion/backend-compartido/Dockerfile \
  -t "semillero-backend:$VERSION" .
```

Construir desde el commit que se va a publicar, con el código de aplicación sin cambios locales. Registrar además el ID de imagen y el checksum del archivo exportado: la etiqueta identifica el código, pero no garantiza por sí sola una reconstrucción idéntica de dependencias del sistema.

También se puede usar `docker buildx build --platform linux/amd64 --load` con esos argumentos cuando Buildx esté instalado. El contexto permite únicamente el JAR compilado y `entrypoint.sh`: videos, `.env`, claves SSH y OAuth quedan fuera. El archivo de imagen exportado tampoco contiene las credenciales que se entregan al iniciar el contenedor.

Usar una etiqueta nueva por entrega. La imagen anterior `vps-20261101` no contiene estas funciones ni las últimas migraciones. Activar YouTube en otra entrega requerirá incorporar Python, dependencias y uploader, más montar sus credenciales fuera de la imagen.

## Preparación del VPS

La revisión del 2026-11-02 confirmó que Caddy dirige la API a `127.0.0.1:18083`, sin proceso escuchando todavía en ese puerto. La cuenta dispone de un máximo de 4 GiB y cuatro CPU, con umbral de memoria de 3 GiB y aproximadamente 187 GiB libres en el disco compartido. Se conservan límites iniciales de 1 GiB y una CPU para el contenedor. La cuenta usa Docker rootless y no tenía imágenes ni contenedores activos.

**Todavía no se puede iniciar esta entrega contra la base compartida.** La comprobación de solo lectura encontró dos pendientes: la URL disponible apunta al loopback del host y el usuario de aplicación no tiene permisos suficientes para Flyway. La URL accesible desde el contenedor y el mecanismo de migración deben resolverse con el administrador; no desactivar el aislamiento de RootlessKit ni abrir PostgreSQL públicamente. Ver el detalle y los requisitos en [PREPARACION_VPS.md](PREPARACION_VPS.md).

El backend usa PostgreSQL compartido. `DB_URL` debe ser alcanzable desde la red del contenedor: `localhost` dentro de él no apunta a PostgreSQL del host. Esta carpeta no crea ni reemplaza la base de producción. Respaldarla antes de la primera migración de esta entrega.

Preparar una carpeta de entrega privada con `compose.yaml`, `.env.example`, un `.gitignore` que excluya `.env` y archivos de imagen, y la imagen exportada:

```bash
VERSION=$(git rev-parse --short=12 HEAD)
docker save --output imagen.tar "semillero-backend:$VERSION"
ssh backend@80.241.208.3 'mkdir -p ~/proyecto/backend'
scp imagen.tar compose.yaml .env.example .gitignore backend@80.241.208.3:~/proyecto/backend/
```

Son instrucciones para la publicación autorizada; no indican que esta transferencia ya se ejecutó. La imagen se construye en la computadora y se carga en el VPS, sin compilar allí.

## Configuración y arranque

En `~/proyecto/backend`, dentro de SSH:

```bash
docker context show
docker load -i imagen.tar
if [ ! -e .env ]; then
  umask 077
  cp .env.example .env
fi
chmod 600 .env
nano .env
```

Completar `APP_IMAGE`, `BACKEND_HOST_PORT`, `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET` y los orígenes necesarios. `JWT_SECRET` firma los tokens del login: generar un secreto aleatorio de al menos 32 bytes y conservarlo entre despliegues. No enviarlo a Flutter ni confundirlo con un JWT de usuario o con `ANALYSIS_TOKEN`, que autentica a Spring frente a Spark.

Los secretos con caracteres especiales deben escribirse entre comillas simples en `.env` para evitar interpolación. Nunca mostrar el archivo ni ejecutar `docker compose config` sin `--quiet` en reportes: puede revelar credenciales.

```bash
docker compose config --quiet
docker compose up -d --no-build --pull never --wait --wait-timeout 180
docker compose ps
```

Flyway aplica automáticamente V000–V012 y JPA valida después. No ejecutar `init.sql` sobre la base existente. El healthcheck consulta `/api/v1/paises?size=1`, verificando HTTP y acceso a DB. Revisar errores de arranque sin copiar secretos de logs.

El administrador debe enlazar el proxy HTTPS con el puerto de loopback reservado. Probar login, JWT, CORS y una carga autorizada desde la web. Las rutas de video/análisis verifican acceso al partido; los CRUD compartidos de partidos, participaciones y fichajes requieren ADMIN u ORGANIZADOR para escribir. La respuesta de carga es `202`, con `analysisId` y `Location`; no esperar inferencia dentro de esa petición.

## Actualizaciones y límites actuales

Para actualizar: construir otra etiqueta, transferirla, ejecutar `docker load`, cambiar solo `APP_IMAGE` del `.env` existente y repetir `docker compose up`. Un `restart` no cambia de imagen. Mantener secretos, base y volumen; no usar `down -v` en producción. La reversión de imagen requiere revisar compatibilidad del esquema y no revierte migraciones automáticamente.

Se mantiene multipart completo de hasta 2 GiB. Las cargas reanudables de 12 GiB, cuotas, cancelación y limpieza automática del original corresponden a otra etapa. Con Spark deshabilitada la cola no se despacha; su plazo configurado de análisis sigue contando desde la solicitud. Ver [cargas y permisos](../../docs/CARGAS_PERSISTENTES_Y_PERMISOS.md).

La arquitectura es `linux/amd64`, compatible con la sesión del VPS verificada. Las pruebas de esta entrega se ejecutan localmente con una base desechable y credenciales exclusivas de prueba; todavía no se ha instalado esta imagen en el VPS.

## Validación local 2026-11-02

Imagen `semillero-backend:vps-20261102-videos`, `linux/amd64`, UID 10001 y raíz de solo lectura. Pasaron **50 pruebas de configuración/permisos/carga** y **45 comprobaciones del contenedor** sobre PostgreSQL 17 desechable. Se verificaron las 13 migraciones V000–V012, login/JWT, rechazo 401/403, FFmpeg/FFprobe, carga válida de 142.608.793 bytes (más de 128 MiB) y rechazo de un MP4 inválido.

Se inicializó el volumen con la imagen anterior, sin carpetas de análisis o temporales. La imagen nueva completó esas carpetas sin root. Al recrear el contenedor, el original y un PNG de prueba conservaron sus checksums, el trabajo permaneció pendiente en SQL y el JWT anterior siguió funcionando. Se comprobó también que el temporal multipart se limpiara al finalizar la carga. Spark y YouTube permanecieron deshabilitados; no se probó inferencia real. El paquete local de entrega no contiene credenciales de producción y no se ha transferido al VPS.
