# Integración de main y correcciones de Torneos

Verificado el 2 de noviembre de 2026 en `video-data-analysis`.

Se integró `origin/main` (`1b7663ace26df60553b3447ca5f782ea757541b5`, con el PR #13 aceptado) mediante el merge `ad151ed9be0367b7b4a8f9e8da91dd75efa06f95`, sin conflictos. Se conservaron las correcciones de CORS y JWT de la rama.

## Correcciones

| Problema | Comportamiento corregido |
| --- | --- |
| El acceso a documentos consideraba vigente cualquier fichaje sin fecha final y rechazaba todos los que tenían fecha final. | El detalle y el listado usan la misma consulta de vigencia por día: inicio alcanzado y fin no vencido, con fechas nulas abiertas y límites inclusivos. Un fichaje futuro o vencido no concede acceso al personal del club. |
| El borrado del reglamento y la sustitución de desempates registraban el usuario enviado por el cliente. | Validan la identidad antes de modificar datos y auditan al actor autenticado. Enviar otro usuario produce `403`; no permite atribuir la acción a otra persona. |
| Los servicios de documentos y Torneos concedían permisos cuando faltaba un principal real. | Una identidad ausente, anónima, genérica, no autenticada o sin ID válido se rechaza. Los fixtures proporcionan identidades explícitas; se retiraron las excepciones permisivas del código de producción. |

Las pruebas que enviaban un organizador/actor diferente al autenticado ahora esperan `403`, porque la identidad del actor se verifica antes de resolver esa relación. El resto del contrato HTTP se conserva.

## Validación

```bash
MIGRATIONS_PG_TEST_URL=jdbc:postgresql://127.0.0.1:55437/migrations_test \
ANALYSIS_PG_TEST_URL=jdbc:postgresql://127.0.0.1:55437/analysis_hu0203_test \
METRICS_PG_TEST_URL=jdbc:postgresql://127.0.0.1:55437/metrics_hu12_test \
bash ./mvnw --batch-mode --no-transfer-progress clean verify
```

Entorno: Java 21, FFmpeg/FFprobe y PostgreSQL 17 local desechable, con las tres bases preparadas para las suites correspondientes. Resultado: **508 pruebas, 0 fallos, 0 errores, 0 omitidas; BUILD SUCCESS**. Las 17 comprobaciones de migraciones PostgreSQL también se ejecutaron.

La nueva clase `SeguridadServiciosTorneosIntegrationTest` aporta 18 ejecuciones de regresión: autoría A/B y A/A, acceso directo a servicios, identidades inválidas y fichajes futuros, vencidos y en límites de fecha. Las siete reproducciones que fallaban en la revisión anterior ahora pasan.

Los logs, reportes XML, resumen JSON y hashes de los 13 archivos de código y pruebas verificados están en `.local/vps/integracion-torneos-20261102/`, fuera de Git. El contenedor de PostgreSQL de estas pruebas se detuvo al finalizar. No se modificó la base del VPS.

## Antes del despliegue

Esta corrección no modifica migraciones ni `init.sql`. La integración de main incorpora **V013**, que agrega `documentos_jugador`: el migrador central debe aplicarla, con respaldo, antes de iniciar la imagen actualizada. Mantener Flyway desactivado en el runtime e Hibernate en `validate`; no editar migraciones ya aplicadas.

Este resultado corresponde a la integración de Torneos y sus correcciones. La adaptación al contrato nuevo de Spark, las pruebas reales de Spark/YouTube y el despliegue al VPS siguen siendo fases posteriores del plan.
