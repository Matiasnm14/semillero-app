# Configuración inicial de YouTube

Objetivo de la primera prueba: subir `cronoshift.mp4` al canal `@Semillero-Video-Analysis` con título `2026-10-21` y visibilidad **No listado** (`unlisted`). El canal y el cliente OAuth de escritorio ya están configurados; para ejecutar la subida se necesita la autorización interactiva de su propietario.

**Prueba completada el 2026-10-21:** se verificó el canal autorizado, se subieron los 60.013.506 bytes y YouTube confirmó título, visibilidad `unlisted` y procesamiento terminado (`AVAILABLE`). El ID y enlace del video están en el recibo local `.local/youtube/cronoshift-upload-state.json`. La autorización también quedó guardada localmente para las siguientes pruebas.

## 1. Crear el canal

1. Iniciar sesión en YouTube con la nueva cuenta del proyecto.
2. Abrir la [lista de canales](https://www.youtube.com/channel_switcher) y seleccionar **Crear un canal**.
3. Usar **Semillero** como nombre y elegir un identificador disponible, por ejemplo uno relacionado con el proyecto. La foto puede configurarse después.
4. Confirmar la creación y abrir [YouTube Studio](https://studio.youtube.com/).
5. Guardar el enlace o el identificador `@...` del canal para comprobar que la integración apunta al canal correcto.

La ruta de creación permite utilizar un nombre de proyecto y un canal asociado a una cuenta de marca. [Instrucciones oficiales](https://support.google.com/youtube/answer/1646861?hl=es).

El clip local dura aproximadamente 2 minutos y 35 segundos. Para las futuras grabaciones de partidos de más de 15 minutos, completar la [verificación del canal](https://www.youtube.com/verify). [Ayuda sobre duración](https://support.google.com/youtube/answer/71673?hl=es).

## 2. Preparar el acceso a la API

Después de crear el canal:

1. Crear un proyecto **Semillero** en [Google Cloud Console](https://console.cloud.google.com/).
2. Habilitar **YouTube Data API v3** en ese proyecto.
3. Configurar Google Auth Platform: nombre de aplicación, correo de contacto y audiencia. Para la prueba con una cuenta Gmail, usar audiencia externa en modo de prueba y agregar la cuenta propietaria como usuario de prueba.
4. Crear un cliente OAuth de tipo **Aplicación de escritorio** para esta prueba local. Este cliente sirve para una autorización interactiva mediante retorno local; cuando se integre en el servidor se configurará el cliente web y su URI de retorno correspondientes.
5. Guardar el JSON descargado localmente como `.local/youtube/client_secret.json`. La carpeta está ignorada por Git; no compartir el contenido del archivo en el chat ni incluirlo en el repositorio.

La utilidad utiliza `https://www.googleapis.com/auth/youtube.upload` para subir y `https://www.googleapis.com/auth/youtube.readonly` para verificar el canal y consultar el procesamiento y la visibilidad. La autorización debe concederse desde la cuenta propietaria del canal. Usa el flujo de aplicación instalada de la biblioteca de Google, con retorno por loopback y PKCE, comprobación de `state` y tokens locales en `.local/youtube/`. [OAuth para aplicaciones instaladas](https://developers.google.com/identity/protocols/oauth2/native-app), [API de subida](https://developers.google.com/youtube/data/reference/rest/v3/videos/insert).

## 3. Parámetros preparados para la prueba

```json
{
  "snippet": {
    "title": "2026-10-21",
    "categoryId": "22"
  },
  "status": {
    "privacyStatus": "unlisted"
  }
}
```

La categoría `22` es la categoría general utilizada en la guía de ejemplo de Google para subidas; no pretende clasificar este clip como un partido. En la integración de partidos se elegirá la categoría que corresponda. [Guía de subida](https://developers.google.com/youtube/data/guides/uploading_a_video).

Se preparó una ficha local en `.local/youtube/cronoshift-upload.json` con archivo, tamaño, checksum y metadatos de esta prueba. Es un registro de preparación, no una confirmación de subida. El video original se conserva en la raíz y también está ignorado por Git.

La subida usa `notifySubscribers=false` y visibilidad explícita `unlisted`. Guarda el ID devuelto y comprueba el título, la visibilidad y el procesamiento antes de comunicar el enlace como disponible. Si la respuesta o la visibilidad no coinciden, informa el estado real.

La fecha `2026-10-21` corresponde al día en que se solicitó esta prueba, en la zona `America/La_Paz`. Si la prueba se realiza otro día y se pide nuevamente la fecha de ese día, regenerar el título antes de subir.

## 4. Ejecutar la prueba local

La utilidad `scripts/youtube_upload.py` funciona de forma independiente de la base de datos y del microservicio. Requiere Python 3.10 o posterior en Linux/macOS; se usa como herramienta de prueba para la futura integración del backend Java.

Desde la raíz del repositorio:

```bash
python3 -m venv .local/youtube/venv
.local/youtube/venv/bin/python -m pip install -r scripts/youtube-requirements.txt
.local/youtube/venv/bin/python scripts/youtube_upload.py authorize
```

Abrir el enlace que muestra la utilidad en el navegador de este mismo equipo y autorizar con la cuenta propietaria del canal. El retorno escucha solo en `127.0.0.1:8765` y espera cinco minutos. Se comprueba el ID del canal autorizado frente al `@identificador` esperado antes de permitir una subida. Si se seleccionó otra cuenta, ejecutar `authorize --reauthorize` y elegir la correcta.

Después:

```bash
.local/youtube/venv/bin/python scripts/youtube_upload.py upload --title 2026-10-21
.local/youtube/venv/bin/python scripts/youtube_upload.py status
```

Sin `--title`, se usa automáticamente la fecha de ejecución en `America/La_Paz`. La visibilidad está fijada a `unlisted`. El canal por defecto es `Semillero-Video-Analysis` y el archivo por defecto es `cronoshift.mp4`.

La sesión reanudable, el checksum y el ID remoto se guardan en `.local/youtube/cronoshift-upload-state.json`, con permisos de lectura/escritura solo para el propietario. Repetir `upload` con el mismo archivo, título y canal recupera la sesión o consulta el video existente. Si una sesión expira, se pide revisar el canal antes de crear una nueva, para evitar publicar duplicados. No borrar el recibo para reintentar una subida cuyo resultado sea incierto.

El comando `status` devuelve `PROCESSING` mientras YouTube procesa el archivo y `AVAILABLE` cuando confirma que terminó. Si detecta otro título, canal o visibilidad, registra la discrepancia e informa el estado real. La URL de la sesión y los tokens se conservan únicamente en la carpeta ignorada; los mensajes de consola no muestran sus valores.

Pruebas automatizadas, sin conexión a Google:

```bash
.local/youtube/venv/bin/python -m unittest discover -s scripts/tests -v
```

Si Google muestra `403: access_denied` indicando que la aplicación está en pruebas y solo admite usuarios aprobados:

1. Abrir el proyecto correcto en Google Cloud: para el cliente actual, `semillero-512312`.
2. Entrar a **Google Auth Platform → Audiencia → Usuarios de prueba → Añadir usuarios**.
3. Agregar el correo exacto de la cuenta de Google propietaria del canal. Un nombre de canal o su `@identificador` no sustituye al correo de acceso.
4. Guardar y abrir nuevamente el enlace de autorización, eligiendo esa misma cuenta.
5. Si ya terminó la espera de cinco minutos, ejecutar otra vez `authorize` y usar el enlace nuevo.

Se puede mantener el estado **Prueba** para esta prueba controlada. [Gestión de audiencia](https://support.google.com/cloud/answer/15549945?hl=es). Si falla la renovación del token, repetir `authorize --reauthorize`.
