# Correcciones de CORS y JWT — 2026-11-02

Versión probada: `659ec7c42d61 + cambios locales eeeff46d8f00`. Los cambios aún no están confirmados en Git; [fuentes.json](fuentes.json) identifica por SHA-256 todos los archivos modificados y los tests nuevos.

| Verificación | Resultado |
| --- | --- |
| Suite Java completa con PostgreSQL real desechable | **416 aprobadas**, 0 fallos, 0 errores, 0 omitidas |
| Regresiones nuevas incluidas en esa suite | **14 JWT y 10 CORS aprobadas** |
| Diagnósticos originales repetidos en copia aislada corregida | **8 aprobados**, incluido el caso de `alg: none` que antes fallaba |
| Compose | Configuración validada con datos sintéticos, sin imprimir secretos |
| Despliegue VPS | **Pendiente**: SSH devuelve `Permission denied (publickey)` |

## JWT corregido — PEND-003

El backend exige una cabecera JSON con `alg` textual exactamente `HS256`. Sigue comprobando la firma HMAC y el vencimiento. Rechaza algoritmos distintos, cabecera sin algoritmo, tipo incorrecto, JSON inválido y separadores adicionales del token. No cambia el secreto ni invalida tokens legítimos emitidos por el backend que aún no hayan vencido.

La regresión permanente es `semillero-api/src/test/java/edu/upb/semillero/config/JwtAutenticacionIntegrationTest.java`. Usa claves sintéticas, comprueba HTTP 401 para los tokens inválidos y mantiene un control positivo HTTP 200. El diagnóstico original que devolvía 200 con `alg: none` ahora devuelve 401.

## CORS corregido en local — REG-CORS-001

El perfil VPS permite explícitamente `PUBLIC_WEB_ORIGIN`, con valor predeterminado `https://semillero.mandarina-group.cc`. `CORS_ALLOWED_ORIGINS` agrega otros orígenes; dejarlo vacío conserva el origen público. Compose aplica el mismo valor predeterminado. Esto permite HTTPS público aunque Spring reciba HTTP de loopback, sin introducir confianza general en cabeceras reenviadas ni permitir orígenes arbitrarios.

La regresión permanente es `semillero-api/src/test/java/edu/upb/semillero/config/VpsPublicOriginCorsIntegrationTest.java`. Reproduce conexión HTTP interna y Origin HTTPS, comprueba consulta 200, login incompleto 400, preflight 200, ruta protegida sin JWT 401 y escritura con CONSULTA 403. Los orígenes ajenos siguen rechazados, incluso si envían cabeceras de proxy.

## Lo que sigue pendiente en el VPS — PEND-001

La corrección está implementada y probada en el repositorio, pero **no se desplegó**. El acceso `backend@80.241.208.3` fue rechazado por autenticación de clave pública. Por eso el Excel conserva el fallo público de PEND-001, registra la regresión local aprobada por separado y no presenta producción como corregida.

Al recuperar acceso, preparar la imagen de esta versión y la configuración Compose actualizada. Conservar los archivos privados de credenciales y JWT, la conexión estable a PostgreSQL en loopback, el volumen, Flyway desactivado y Hibernate `validate`. Este cambio no necesita SQL ni migraciones. Seguir el procedimiento de actualización de `VPS-Implementacion/backend-compartido/README.md` y comprobar después:

```bash
curl -i 'https://semillero.mandarina-group.cc/api/v1/paises?size=1' \
  -H 'Origin: https://semillero.mandarina-group.cc'

curl -i -X POST 'https://semillero.mandarina-group.cc/api/v1/auth/login' \
  -H 'Origin: https://semillero.mandarina-group.cc' \
  -H 'Content-Type: application/json' --data '{}'

curl -i -X OPTIONS 'https://semillero.mandarina-group.cc/api/v1/auth/login' \
  -H 'Origin: https://semillero.mandarina-group.cc' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: content-type'
```

Se esperan 200, 400 y 200, respectivamente, y el permiso CORS para el origen configurado. Mantener también la comprobación de rechazo de origen externo y la autenticación de rutas protegidas.

## Evidencia y reproducción

Suite completa: `bash ./mvnw --batch-mode --no-transfer-progress verify`, con las tres bases locales desechables y variables PostgreSQL indicadas en la hoja Entornos. Para ejecutar solo las regresiones nuevas:

```bash
bash ./mvnw -pl semillero-api -am \
  '-Dtest=JwtAutenticacionIntegrationTest,VpsPublicOriginCorsIntegrationTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Los ocho diagnósticos históricos se repitieron en una copia aislada con las fuentes corregidas. Los resultados sanitizados están en [resultados.json](resultados.json); los XML y logs completos se conservaron en `.local/vps/correcciones-cors-jwt-20261102/`, fuera de Git. El reporte padre conserva la observación original anterior a la corrección.
