# Pruebas manuales del sprint 1 · Módulo de Torneos

Colección de Postman que prueba de punta a punta todo lo que el módulo de Torneos entregó en el
sprint 1: **52 requests y 86 aserciones** repartidas en 7 carpetas.

| Carpeta | Qué cubre | Tareas |
| --- | --- | --- |
| 00 · Datos base | País, ciudad, 3 clubes y 2 usuarios | — |
| 01 · HU 1 Crear torneo | Torneo en BORRADOR, categorías, abrir inscripciones | S1-08, S1-09, S1-10 |
| 02 · HU 1 validaciones | Los 8 casos que tienen que fallar | S1-08, S1-09 |
| 03 · HU 2 Reglamento | Reglamento, valores por defecto, orden de desempates | S1-12, S1-13 |
| 04 · HU 3 Sedes y canchas | CRUD, superficie, dimensiones, descripción | S1-16, S1-17 |
| 05 · HU 4 Inscripción | Inscribir equipo, duplicado, cupo | S1-20, S1-21 |
| 06 · Torneo en BORRADOR | Que no se pueda inscribir ni abrir sin categorías | S1-10, S1-20 |

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

## Paso 2 · Levantar la API

```bash
./mvnw -pl semillero-api -am spring-boot:run -Dspring-boot.run.profiles=dev
```

En Windows: `.\mvnw.cmd` en vez de `./mvnw`.

Con el perfil `dev` esta rama usa `ddl-auto=update`, así que **Hibernate crea las 19 tablas solo** a
partir de las entidades. No hay que correr ningún script.

Para saber que arrancó bien, en el log tiene que aparecer `Started SemilleroApplication` y ninguna
excepción de Hibernate. Verificación rápida:

```bash
curl http://localhost:8080/api/v1/torneos
```

Tiene que responder un `PageResponse` vacío, no un error de conexión.

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

Da un reporte de las 86 aserciones en una sola corrida:

```bash
npx newman run docs/torneos/postman/semillero-torneos-sprint1.postman_collection.json
```

Si la API no está en `localhost:8080`:

```bash
npx newman run docs/torneos/postman/semillero-torneos-sprint1.postman_collection.json --env-var baseUrl=http://otro-host:8080/api/v1
```

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
| `password authentication failed for user "semillero"` al arrancar | Falta el paso 1, o la contraseña del `.env` no coincide con la del rol |
| El primer request da 500 | Hibernate no pudo crear las tablas: mirá el log, suele ser que el usuario `semillero` no es dueño de la base |
| Falla "cupo lleno" con 201 en vez de 400 | Estás corriendo contra una base donde ya se llenó otra categoría; la colección es re-ejecutable, pero si editaste los cupos a mano revisá eso |
| Falla "fechaCorte por defecto" | La temporada del torneo y la de la fecha de corte no coinciden: pasa si editaste el body a mano con una temporada fija |

Las pruebas automáticas (`./mvnw clean verify`, 121 casos) cubren lo mismo sobre H2 y **no**
necesitan nada de esto. Esta colección sirve para verificar contra PostgreSQL de verdad, que es lo
que pide la definición de terminado antes de cerrar el sprint.
