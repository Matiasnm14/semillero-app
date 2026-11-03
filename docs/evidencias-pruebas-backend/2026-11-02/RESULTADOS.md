# Resultados de pruebas del backend — 2026-11-02

Rama `video-data-analysis`, commit fuente `659ec7c42d61be21600a7f8220e709e73e1c1d55`. Inicio UTC: 2026-11-02T22:11:48.443724+00:00. Cierre de registro UTC: 2026-11-02T22:21:23.452125+00:00.

| Ejecución | Resultado |
| --- | --- |
| Suite Java permanente, con las tres suites PostgreSQL habilitadas | **392 aprobadas**, 0 fallos, 0 errores, 0 omitidas |
| Script Python de YouTube, HTTP/OAuth simulados | **14 aprobadas** |
| Diagnósticos adicionales de JWT en copia aislada | **7 aprobados, 1 fallo**, 0 errores/omitidos |
| Guardia de migraciones del workflow, ejecutada localmente | **5 aprobadas** |
| Comprobaciones HTTPS públicas, sin cambios de entidades | **28 aprobadas, 3 fallos CORS** de 31 comprobaciones |
| Acceso SSH para salud interna y loopback | **Bloqueado**: Permission denied (publickey) |

El Excel registra **110 casos aprobados, 2 fallidos, 24 bloqueados y 3 pendientes**. Sus 139 filas son casos operativos, no un recuento adicional de invocaciones: varios referencian un método parametrizado. El inventario incluye 337 métodos del código fuente, todos ejecutados satisfactoriamente en esta ronda. Al cruzar los XML se incorporó un método parametrizado con anotación cualificada que faltaba en la primera versión del inventario. Los ocho diagnósticos JWT son adicionales y temporales.

## CORS — PEND-001 fallido

Con `Origin: https://semillero.mandarina-group.cc`, la consulta `GET /api/v1/paises?size=1`, el login incompleto `POST /api/v1/auth/login` con cuerpo `{}` y su preflight `OPTIONS` devuelven **403 Invalid CORS request**. Se esperaban 200, 400 y 200, respectivamente. El rechazo de un origen externo sí funciona.

Una consulta sin sesión a `/analisis/1/resultados` agotó inicialmente 20 segundos. Se repitió sin cambiar datos y respondió 401; ambas observaciones se conservan en el JSON. Los tres fallos finales corresponden a CORS, no a ese timeout transitorio.

## Cabecera JWT — PEND-003 fallido

Los tokens vencidos, firmados con otra clave y con payload alterado se rechazan con 401. El control positivo de JWT válido accede con 200. Sin embargo, un token con cabecera `alg: none` y **HMAC HS256 válido calculado con la clave sintética del diagnóstico** accede con 200 en lugar del 401 esperado.

`JwtService.validar` comprueba HMAC y vencimiento, pero no valida el algoritmo declarado en la cabecera. Debe exigir el algoritmo permitido y rechazar cabeceras inválidas. Este ensayo **no demuestra falsificación sin conocer la clave** ni aceptación de un token sin firma. Fuente de reproducción: [JwtVariantsDiagnosticTest.java](JwtVariantsDiagnosticTest.java).

## Configuración JWT — PEND-030 parcial

Tres diagnósticos locales aprobaron: el default de desarrollo resuelve; el perfil VPS sin `JWT_SECRET` falla explícitamente; con secreto sintético se crea el servicio y valida JWT firmados. Esto no equivale al arranque integral del contenedor real, por eso el caso permanece pendiente. Fuente: [JwtStartupDiagnosticTest.java](JwtStartupDiagnosticTest.java).

## Pendientes y límites

- Correcciones del PR #13: deben probarse en su revisión corregida; no se mezclan con esta rama.
- Modelo/GPU y YouTube reales: no se ejecutaron; los tests automatizados utilizan simulaciones.
- Frontend real y login positivo desplegado: requieren cuenta y ejecución integrada autorizadas.
- Subidas por bloques/12 GiB, identidad por dorsal y política de borrado: dependen de implementación/contrato del equipo.
- Fallos de disco y cargas abandonadas: permanecen pendientes de automatización.
- CI/CD: no se ejecutó Actions, no se publicó en GHCR y no se hizo despliegue. La guardia de migraciones se aprobó únicamente como programa Bash local.
- VPS interno: la conexión SSH fue rechazada por clave pública; no se reiniciaron servicios ni se leyeron archivos privados. La lectura HTTPS pública sí respondió correctamente sin Origin.
- Imagen, restauración y carga de rendimiento: no se ensayaron en producción ni se presentan como aprobadas.

## Reproducción y evidencia

Java se ejecutó con Java 21, FFmpeg/FFprobe y PostgreSQL 17 local desechable en `127.0.0.1:55435`, con estas variables:

```bash
MIGRATIONS_PG_TEST_URL=jdbc:postgresql://127.0.0.1:55435/migrations_test \
ANALYSIS_PG_TEST_URL=jdbc:postgresql://127.0.0.1:55435/analysis_hu0203_test \
METRICS_PG_TEST_URL=jdbc:postgresql://127.0.0.1:55435/metrics_hu12_test \
bash ./mvnw --batch-mode --no-transfer-progress verify
```

Crear antes las tres bases y sus usuarios de prueba (`migrations_test`, `analysis_test`, `metrics_test`) en un PostgreSQL local desechable. Las pruebas borran y recrean `public`; no usar la base principal. Los nombres exactos y el alcance se documentan en la hoja Entornos del Excel.

Python: `.local/youtube/venv/bin/python -m unittest discover -s scripts/tests -p test_youtube_upload.py -v`; dependencias en `scripts/youtube-requirements.txt`.

Para repetir los diagnósticos, copiar los dos `.java` de esta carpeta a `semillero-api/src/test/java/edu/upb/semillero/config/` de una **copia aislada del commit fuente** y ejecutar:

```bash
bash ./mvnw -pl semillero-api -am \
  '-Dtest=JwtVariantsDiagnosticTest,JwtStartupDiagnosticTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

El diagnóstico del algoritmo falla de forma esperada hasta corregir el código. No retirar su aserción para aparentar una aprobación.

La guardia se reproduce con `python3 docs/evidencias-pruebas-backend/2026-11-02/probar_guardia_migraciones.py`, desde la raíz del proyecto. La copia entregada del script utiliza el bloque del workflow en `docs/ci-cd`; no llama GitHub ni ejecuta migraciones.

[resultados.json](resultados.json) contiene resultados sanitizados, estados por caso, invocaciones y hashes SHA-256 de los reportes originales. Los XML y logs completos quedan en `.local/vps/casos-excel-20261102/`, ignorados por Git. Los secretos y cuerpos con datos personales no se copian a este reporte.
