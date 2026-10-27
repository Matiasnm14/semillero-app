# semillero-app

Sistema **SEMILLERO**, gestión para escuelas de fútbol. Un solo repositorio para los cuatro grupos
de Proyecto de Ingeniería de Software, UPB Santa Cruz. Entrega: 04/11/2026.

Spring Boot 4.1.1, Java 21, Maven Wrapper, Lombok, PostgreSQL 17 en Docker. Paquete raíz
`edu.upb.semillero`. Módulos Maven: `semillero-api` → `core` → `data`.

## Reglas que valen para todos los grupos

1. **La BD acordada entre grupos no se cambia.** Las 17 tablas (`pais`, `ciudad`, `clubes`, `users`,
   `administrador`, `usuarios_club`, `usuario_trabaja`, `jugadores`, `posiciones`,
   `posiciones_jugador`, `fichado`, `pruebas`, `clinica_de_pago`, `videos`, `conversa`, `partidos`,
   `participaciones_partido`) no pierden ni renombran columnas. Se pueden **agregar** columnas
   opcionales, acordándolo antes con el grupo dueño.
2. **Cada grupo trabaja en su subpaquete**: `entity/<grupo>`, `repos/<grupo>`, `dto/*/<grupo>`,
   `enums/<grupo>`, `service/<grupo>`, `controller/<grupo>`. Tocar código de otro grupo se acuerda
   antes.
3. **Una rama por tarea**, con el ID del sprint backlog en la rama y en el commit:
   `[S1-08] Endpoint crear torneo`. Nunca trabajar directo en `main`.
4. **Antes de pushear, `./mvnw clean verify` tiene que pasar.** Si el número de pruebas baja
   respecto del commit anterior, algo se rompió. Ojo: las pruebas del módulo de video necesitan
   **FFmpeg** instalado (`ffmpeg` y `ffprobe` en el PATH). Sin eso `VideoPartidoIntegrationTest`
   falla 28 casos con `503`, en `main` igual que en cualquier rama, y no es algo que hayas roto vos.
   Para verificar el resto mientras no lo tengas instalado:
   `./mvnw clean verify -Dtest='!VideoPartidoIntegrationTest' -DfailIfNoTests=false`.
5. **Nada de contraseñas ni `.env` en Git.**

## Si sos una IA trabajando en este repositorio

Leé primero el contexto del grupo cuyo código vas a tocar, y el registro de acuerdos entre grupos.
**Si un punto no dice `Aceptado`, no lo uses como si estuviera acordado**, y no inventes columnas,
estados ni reglas que no estén en esos documentos.

| Módulo | Grupo | Dónde está su contexto |
| --- | --- | --- |
| Torneos | 4 | `docs/torneos/CONTEXTO_TORNEOS.md` y `docs/torneos/bd_torneos_propuesta.sql`. El registro de acuerdos entre grupos está en la **sección 0 de `docs/torneos/PENDIENTES.md`** |
| Análisis de rendimiento por video | — | `docs/HU01_CARGA_VIDEOS.md`, `docs/HU02_HU03_ANALISIS.md`, `docs/HU12_MODELO_INTEGRIDAD.md` |
| Ficha técnica | — | Frontend en Flutter, en el repositorio aparte `AdriBc16/furbo`. Falta su documento de contexto |
| Scouting, reclutamiento y contacto | — | Falta su documento de contexto |

Cada grupo completa su fila cuando tenga sus documentos.

## Cómo levantar y probar

```bash
cp .env.example .env          # y poner DB_PASSWORD
docker compose up -d --wait db
./mvnw clean verify
```

Las pruebas usan **H2 en memoria** y no necesitan Docker. La API queda en
`http://localhost:8080/api/v1` con el perfil `dev`, donde **Flyway crea y actualiza el esquema**
con los scripts de `database/migrations/` y después Hibernate solo lo valida
(`ddl-auto=validate`, también en `dev`). No se corre ningún script SQL a mano: una tabla o columna
nueva se agrega con una migración `V<n>__<nombre>.sql`, nunca dejando que Hibernate la cree.

Documentación OpenAPI en `/v3/api-docs` y Swagger UI en `/swagger-ui.html`.

**IntelliJ:** abrir la carpeta que tiene el `pom.xml`, no la de arriba. En Settings → Build Tools →
Maven, poner **Maven home path** en `Bundled (Maven 3)`: el wrapper fija Maven 3.10.0 y el
importador de IntelliJ 2025.3 solo soporta hasta 3.9.x. JDK 21 como Project SDK y como JDK for
importer, y Lombok con *Enable annotation processing*.
