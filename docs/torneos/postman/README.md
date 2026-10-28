# Pruebas manuales de los sprints 1 y 2 · Módulo de Torneos

Colección de Postman que prueba de punta a punta lo que entregó el módulo de Torneos: las 54
requests del sprint 1 más 14 del sprint 2, en 8 carpetas.

> **Desde el sprint 2 la API pide token.** Todo lo que está bajo `/api/v1/torneos` necesita un JWT
> con el rol que corresponda (HU 16, tareas S2-01 a S2-04). La colección tiene la autenticación
> *Bearer `{{token}}`* a nivel colección y el primer request de la carpeta 07 hace el login y guarda
> el token. **Antes de correrla hay que crear un usuario con rol** y poner su email y contraseña en
> las variables `adminEmail` y `adminPassword`; está explicado en el paso 3.

| Carpeta | Qué cubre | Tareas |
| --- | --- | --- |
| 00 · Datos base | País, ciudad, 3 clubes y 2 usuarios | — |
| 01 · HU 1 Crear torneo | Torneo en BORRADOR, categorías, abrir inscripciones | S1-08, S1-09, S1-10 |
| 02 · HU 1 validaciones | Los 8 casos que tienen que fallar | S1-08, S1-09 |
| 03 · HU 2 Reglamento | Reglamento, valores por defecto, orden de desempates | S1-12, S1-13 |
| 04 · HU 3 Sedes y canchas | CRUD, superficie, dimensiones, descripción | S1-16, S1-17 |
| 05 · HU 4 Inscripción | Inscribir equipo, duplicado, cupo | S1-20, S1-21 |
| 06 · Torneo en BORRADOR | Que no se pueda inscribir ni abrir sin categorías | S1-10, S1-20 |
| 07 · Sprint 2 | Login con token, aprobación e historial de inscripciones, lista de buena fe con su PDF, fixture de liga con su PDF y publicación, y el 401 sin token | S2-01, S2-02, S2-06 a S2-18 |

La colección se corre entera de arriba abajo: cada carpeta deja en variables los IDs que usa la
siguiente. **Las fechas se calculan solas respecto de hoy**, en el script `prerequest` de la
colección, porque la ventana de inscripción depende de `now()` y con fechas fijas la colección se
vencía. Se puede correr muchas veces seguidas sin limpiar la base.

---

## Paso 1 · La base de datos

La API necesita PostgreSQL con la base `semillero` y el usuario `semillero`. Hay dos caminos.

### Con el PostgreSQL que ya tengas instalado

Abrí **pgAdmin** o una consola `psql` como superusuario `postgres` y corré, reemplazando la
contraseña por la que tengas en tu `.env`:

```sql
CREATE ROLE semillero WITH LOGIN PASSWORD 'la-del-env';
CREATE DATABASE semillero OWNER semillero;
```

En Windows, `psql` no está en el PATH. La ruta es:

```
"C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres
```

### Con Docker

```bash
docker compose up -d --wait db
```

El `docker-compose.yml` crea el usuario y la base solo, leyendo el `.env`, así que no hace falta
ninguna contraseña de superusuario. **Ojo:** si ya tenés un PostgreSQL nativo escuchando en el 5432,
el contenedor no va a poder levantar. O paras el servicio de Windows (`postgresql-x64-18`), o cambiás
`DB_PORT_EXTERNAL` en el `.env` y ajustás `DB_URL`.

---

## Paso 1b · Un usuario con rol, para el token

El endpoint que asigna roles lo puede usar solo un administrador, así que el primero se carga a
mano, una sola vez, con la API levantada y el usuario ya creado (lo crea la carpeta 00 de la
colección, o `POST /api/v1/usuarios`):

```sql
INSERT INTO usuarios_roles (users_id, roles_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.email = 'organizador@example.com' AND r.codigo = 'ORGANIZADOR'
ON CONFLICT DO NOTHING;
```

Después, en el entorno de Postman, poné ese email en `adminEmail` y su contraseña en
`adminPassword`. Con un usuario ADMIN alcanza para repartir el resto de los roles por
`PUT /api/v1/usuarios/{id}/roles` con un cuerpo como `["ORGANIZADOR","ENTRENADOR"]`.

Los cinco roles (`ADMIN`, `ORGANIZADOR`, `ENTRENADOR`, `MESA_CONTROL`, `CONSULTA`) los siembra la
migración `V008` y también `RolesSeeder` al arrancar, así que la tabla `roles` ya viene cargada.

---

## Paso 2 · Levantar la API

Primero compilar una vez, si no se hizo todavía:

```bash
./mvnw clean verify
```

Y después arrancar el jar ejecutable que eso dejó:

```bash
set -a && . ./.env && set +a && java -jar semillero-api/target/semillero-api-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

En Windows: `.\mvnw.cmd` en vez de `./mvnw`. La línea de arriba es de Git Bash; en PowerShell hay
que exportar las variables a mano.

Tres cosas que hacen falta y no son obvias:

1. **Spring Boot no lee el archivo `.env`.** El `.env` es solo para Docker Compose. La API saca
   `DB_PASSWORD` y `DB_URL` de variables de entorno, así que hay que cargarlas antes (`set -a && . ./.env`).
   Sin eso falla pidiendo `DB_PASSWORD`, o se conecta al puerto equivocado: el valor por omisión en
   `application.properties` es el 5432 y el `.env` del equipo apunta al 5433.
2. **No usar `-pl semillero-api -am spring-boot:run`.** El `-am` incluye el pom raíz en la ejecución
   y Maven falla con `Unable to find a suitable main class`, porque el pom raíz no tiene clase main.
3. **Si el puerto 8080 está ocupado** (un XAMPP o WAMP suele tomarlo), agregar `--server.port=8081`
   y usar ese puerto en todo lo que sigue.

Con el perfil `dev` el esquema lo crea **Flyway**, con los scripts de `database/migrations/`
(`V000` a `V007`), y después Hibernate solo lo valida (`ddl-auto=validate`). Las 35 tablas quedan
creadas al arrancar, sin correr nada a mano.

**Importante si ya usaste esta base antes del merge con `main`:** si la base la creó Hibernate con
`ddl-auto=update`, no tiene la tabla `flyway_schema_history` y la aplicación se niega a arrancar con
`Esquema existente no reconocido como Semillero`. La salida es recrearla vacía y dejar que Flyway la
construya desde cero:

```bash
docker compose down -v && docker compose up -d --wait db
```

El `-v` borra el volumen, o sea los datos. Son datos de prueba, así que no se pierde nada; la
colección los vuelve a crear.

Para saber que arrancó bien, en el log tiene que aparecer `Started SemilleroApplication` y ninguna
excepción. Verificación rápida:

```bash
curl http://localhost:8080/api/v1/torneos
```

Tiene que responder un `PageResponse`, no un error de conexión.

---

## Paso 3 · Swagger

Con la API levantada:

- **Swagger UI**: <http://localhost:8080/swagger-ui.html>
- **Documento OpenAPI en JSON**: <http://localhost:8080/v3/api-docs>

Ahí están los 11 endpoints de torneos y los de los otros grupos, con los esquemas de cada DTO.
Desde Swagger UI se pueden probar a mano con *Try it out*, aunque para probar todo de corrido
conviene la colección.

---

## Paso 4 · Correr la colección

### Opción A · Por terminal, recomendada

Da un reporte de las 93 aserciones en una sola corrida:

```bash
npx newman run docs/torneos/postman/semillero-torneos-sprint1.postman_collection.json
```

Si la API quedó en otro puerto, por ejemplo el 8081 porque el 8080 estaba ocupado:

```bash
npx newman run docs/torneos/postman/semillero-torneos-sprint1.postman_collection.json --env-var baseUrl=http://localhost:8081/api/v1
```

Las URLs de los requests van como texto plano (`{{baseUrl}}/torneos`), no como objeto con `raw`:
newman no resuelve un objeto que solo trae `raw` y falla con `request url is empty` en los 54
requests. Si alguien edita la colección a mano, conviene no cambiar eso.

Al final imprime una tabla. **Lo que hay que mirar es que `assertions` tenga 0 en la columna
`failed`.**

### Opción B · En la aplicación de Postman

1. **Import** → arrastrá los dos archivos de esta carpeta: la colección y
   `semillero-local.postman_environment.json`.
2. Arriba a la derecha, seleccioná el environment **SEMILLERO local (perfil dev)**.
3. Botón derecho en la colección → **Run collection** → **Run**.
4. Mirá la columna de tests: tienen que estar todos en verde.

Los requests individuales también se pueden correr de a uno, pero **en orden**: el primero crea el
país y el último necesita el torneo que creó la carpeta 01.

---

## Si algo falla

| Síntoma | Causa |
| --- | --- |
| Todos los requests dan `ECONNREFUSED` | La API no está levantada, o está en otro puerto |
| `Could not resolve placeholder 'DB_PASSWORD'` al arrancar | No se cargó el `.env`: ver el paso 2 |
| `Port 8080 was already in use` | Otro programa lo tiene, casi siempre Apache de XAMPP. Arrancar con `--server.port=8081` |
| `Unable to find a suitable main class` | Se usó `-am` con `spring-boot:run`: ver el paso 2 |
| Los 54 requests fallan con `request url is empty` | La colección se editó y las URLs quedaron como objeto `{raw}` en vez de texto plano |
| `password authentication failed for user "semillero"` al arrancar | Falta el paso 1, o la contraseña del `.env` no coincide con la del rol |
| El primer request da 500 | Hibernate no pudo crear las tablas: mirá el log, suele ser que el usuario `semillero` no es dueño de la base |
| Falla "cupo lleno" con 201 en vez de 400 | Estás corriendo contra una base donde ya se llenó otra categoría; la colección es re-ejecutable, pero si editaste los cupos a mano revisá eso |
| Falla "fechaCorte por defecto" | La temporada del torneo y la de la fecha de corte no coinciden: pasa si editaste el body a mano con una temporada fija |
| `Esquema existente no reconocido como Semillero` al arrancar | La base la creó Hibernate antes del merge con `main` y no tiene `flyway_schema_history`: ver el aviso del paso 1 |
| `VideoPartidoIntegrationTest` falla 28 casos con `503` en `clean verify` | Falta FFmpeg en el PATH. Es del módulo de video y pasa igual en `main`; no lo rompió torneos |

Las pruebas automáticas (`./mvnw clean verify`, 126 casos) cubren lo mismo sobre H2 y **no**
necesitan nada de esto. Esta colección sirve para verificar contra PostgreSQL de verdad, que es lo
que pide la definición de terminado antes de cerrar el sprint.
