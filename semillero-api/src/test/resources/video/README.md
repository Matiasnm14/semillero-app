# Fixtures de HU01

Archivos sintéticos, sin contenido de usuarios. Se incluyen para que las pruebas de validación utilicen MP4 reales y no dependan de `cronoshift.mp4` ni de las credenciales de Google.

Generación desde la raíz, con FFmpeg y codificador `libx264`:

```bash
ffmpeg -nostdin -hide_banner -loglevel error -f lavfi \
  -i testsrc2=size=32x32:rate=10 -t 1 -c:v libx264 -pix_fmt yuv420p \
  -movflags +faststart -y semillero-api/src/test/resources/video/valid.mp4

ffmpeg -nostdin -hide_banner -loglevel error -f lavfi \
  -i sine=frequency=440 -t 0.2 -c:a aac \
  -y semillero-api/src/test/resources/video/audio-only.mp4
```

La prueba de paquetes corruptos altera el payload de `valid.mp4` en memoria conservando el contenedor legible. Verifica que inspeccionar metadatos no sustituye la decodificación del contenido.
