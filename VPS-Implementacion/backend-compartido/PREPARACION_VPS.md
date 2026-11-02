# Configuración acordada con el administrador del VPS

El administrador confirmó el acceso a PostgreSQL desde un contenedor rootless del usuario `backend` usando `--network host`. Esto sustituye la hipótesis anterior de que había que buscar otro host o ampliar los permisos DDL del usuario de aplicación.

| Configuración | Valor |
| --- | --- |
| Cuenta | `backend@80.241.208.3`, UID 1005 |
| Docker | Contexto `rootless`, plataforma `linux/amd64` |
| Red Compose | `network_mode: host`, sin `ports` |
| Spring y healthcheck | `127.0.0.1:18083` |
| PostgreSQL estable | `jdbc:postgresql://127.0.0.1:5432/semillero` |
| Credenciales existentes | `/home/backend/proyecto/.env.db`, privado, permisos `0600` |
| Runtime | Flyway desactivado, Hibernate `validate` |
| Migraciones | Exclusivamente mediante el migrador central |
| Recursos iniciales | 1 GiB y una CPU; cuenta con cuota máxima de 4 GiB y cuatro CPU |
| Archivos | Volumen `backend_data`, originales, PNG y temporales |
| Spark y YouTube | Deshabilitados |

No cambiar PostgreSQL a `172.20.0.2`: es una dirección interna que reporta su contenedor, no la entrada estable de la aplicación. No abrir el puerto PostgreSQL públicamente ni cambiar la red rootless del administrador.

El runtime no debe pedir permisos para modificar el esquema. El migrador central debe aplicar las migraciones compatibles con la versión del backend antes del arranque. Si Hibernate informa tablas o columnas ausentes, detener esa versión y pasar el error al administrador; no activar Flyway, usar `ddl-auto=update` ni ejecutar `init.sql` sobre la base compartida.

`JWT_SECRET` firma los tokens del login. Se conserva entre actualizaciones en un archivo privado del despliegue; no pertenece a la imagen ni se entrega al front. Hay un borrador local excluido de Git en `.local/vps/configuracion/produccion.env.pendiente`. Se reutiliza si el servidor todavía no tiene un secreto configurado. No regenerarlo al reiniciar.

Antes de iniciar, revisar que el puerto esté libre y no haya otro backend desplegado. Conservar cualquier configuración y volumen existentes. Validar Compose con `docker compose config --quiet`, comprobar salud, una consulta SQL a través de la API y una ruta protegida sin token. Verificar desde el host que el proceso escucha exclusivamente en loopback.

La URL HTTPS pública y los orígenes de la web aún deben confirmarse. CORS vacío admite uso desde el mismo origen; otros orígenes requieren una lista explícita. La validación local del contenedor no sustituye las pruebas HTTP a través del proxy, ni la de inferencia real, que permanece deshabilitada.
