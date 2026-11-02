# Estadísticas deportivas y comparación de jugadores

Los números del comparador se consultan desde participaciones y eventos del partido, sin
necesitar análisis de video ni registros en `player_match_metrics`. El endpoint `/api/v1/metricas`
conserva su contrato de mediciones físicas del video; no es la fuente de goles, asistencias,
apariciones o dorsal de esta pantalla.

## Consultas

Requieren `Authorization: Bearer <token>`. Cualquier usuario autenticado puede leerlas, incluido
CONSULTA. Usar el **ID de `jugadores`**, no el de `users` ni el de una participación.

```http
GET /api/v1/torneos/jugadores/72001/estadisticas?temporada=2026
GET /api/v1/torneos/jugadores/comparacion?jugadorAId=72001&jugadorBId=72002&temporada=2026
```

Ambas rutas admiten `temporada` y `torneoId` opcionales. Si se envían ambos, se aplican juntos.
Sin filtros se consulta todo el historial elegible. Con `temporada`, se usa la temporada del
torneo; para partidos sin torneo se usa el año de `fecha_inicio`. Por ejemplo, un encuentro de
enero de 2027 que pertenezca a un torneo de temporada 2026 corresponde a 2026.

La comparación devuelve dos objetos con el mismo contrato del resumen individual:

```json
{
  "jugadorA": {
    "jugadorId": 72001,
    "temporada": 2026,
    "torneoId": null,
    "partidosJugados": 1,
    "goles": 2,
    "asistencias": 1,
    "minutosJugados": 80,
    "minutosRegistrados": 80,
    "minutosCompletos": true,
    "dorsal": 9,
    "dorsalPartidoId": 73001,
    "dorsalClubId": 71001,
    "calificacionMedia": null
  },
  "jugadorB": {
    "jugadorId": 72002,
    "temporada": 2026,
    "torneoId": null,
    "partidosJugados": 1,
    "goles": 1,
    "asistencias": 1,
    "minutosJugados": 90,
    "minutosRegistrados": 90,
    "minutosCompletos": true,
    "dorsal": 11,
    "dorsalPartidoId": 73001,
    "dorsalClubId": 71001,
    "calificacionMedia": null
  }
}
```

Los IDs y valores del ejemplo son sintéticos. El resumen entrega solo IDs y estadísticas;
los nombres, edad, altura, peso y posiciones siguen viniendo de la ficha técnica.

## Reglas del cálculo

- Se incluyen partidos `FINALIZADO`. Se excluyen los programados, en curso, cancelados y W.O.
- La titularidad, minutos positivos, una entrada posterior al inicio o un evento válido de
  gol/asistencia acreditan que el jugador participó. Esto excluye al suplente que no entró
  (`minuto_entrada = minuto_salida = 0`) y no tiene evidencia de haber jugado.
- Un gol no desaparece porque la planilla todavía tenga cero minutos: se conserva la aparición
  y el evento acreditado. El tiempo cero registrado se devuelve como tal, sin inventar su duración.
- Los goles se cuentan desde eventos `GOL` del autor. Un autogol no suma goles individuales ni
  asistencias; sí puede acreditar participación.
- La asistencia es `participacion_rel_id` de un gol normal. El asistente debe pertenecer al
  mismo partido y club del autor. No hace falta una tabla nueva de asistencias.
- Los eventos de otro partido o con un autor ajeno al equipo del evento no se mezclan en el
  resumen. Cada evento cuenta una sola vez, aunque ambos jugadores intervengan en el mismo gol.
- Los minutos provienen primero de `participaciones_partido.minutos_jugados`. Si ese campo es
  nulo y existen entrada y salida coherentes, se usa su diferencia. No se usan la duración del
  partido ni la del video para inventar tiempo jugado.
- Si falta tiempo de alguna aparición, `minutosJugados` es `null`, `minutosCompletos` es `false`
  y `minutosRegistrados` indica únicamente la suma conocida. Sin apariciones, el total es cero.
- El dorsal es el último positivo de las participaciones jugadas que cumplen los filtros.
  Se ordenan por fecha de inicio y, en empate, por ID del partido. Su partido y club se incluyen
  para identificar su procedencia. Si no hay uno válido, los tres campos son `null`; no se
  presenta un dorsal inventado como `#0`.
- La calificación no tiene una fuente ni fórmula acordada en este esquema: `calificacionMedia`
  es `null`. No se genera una puntuación ficticia.

Un jugador o torneo inexistente responde `404`. Parámetros inválidos responden `400` y la falta
de sesión responde `401`. Un jugador existente sin participaciones elegibles recibe `200` con
contadores cero y dorsal/calificación no disponibles.

## Integración de Flutter

La pantalla debe pedir la ruta `/comparacion` al elegir ambos jugadores y cambiar temporada.
Usar `jugadorA` y `jugadorB` para las dos columnas:

| Campo de la pantalla | Campo del resumen |
| --- | --- |
| Goles de temporada | `goles`, enviando la temporada seleccionada |
| Asistencias | `asistencias` |
| Partidos | `partidosJugados` |
| Minutos jugados | `minutosJugados` |
| Dorsal | `dorsal` |
| Calificación media | `calificacionMedia` |

Mostrar `—` o «Sin datos» para los valores nulos. Las barras solo deben comparar valores
conocidos; evitar convertir `null`, errores HTTP o una respuesta vacía de otro endpoint a cero.
Si faltan minutos, se puede mostrar «25 min registrados; total incompleto» usando el campo
`minutosRegistrados`, pero no presentarlo como el total completo.

El backend de este repositorio no contiene el código Flutter. El repositorio indicado en
`CLAUDE.md` (`AdriBc16/furbo`) no fue accesible con el acceso disponible durante esta entrega.
La adaptación visual y el cambio de llamadas deben aplicarse en ese repositorio con este contrato.

## Participaciones y compatibilidad

El CRUD conserva sus rutas y campos anteriores; agrega `minutosJugados` a las respuestas, sin
permitir su escritura desde el CRUD compartido. Los filtros del listado ahora sí se aplican:

```http
GET /api/v1/participaciones-partido?jugadorId=72001&partidoId=73001&clubId=71001&page=0&size=20
```

Los filtros son opcionales y se combinan con AND. El listado sigue siendo paginado: sus
contadores corresponden al filtro aplicado. Para el comparador, usar el nuevo resumen en lugar
de intentar agregar únicamente la primera página de participaciones.

No hay cambios de esquema ni migraciones nuevas para esta solución. Los cálculos leen las
columnas ya existentes y no alteran resultados, planillas o mediciones del analizador.

## Validación

`EstadisticasJugadorApiTest` prueba los dos jugadores con resultados diferentes, los filtros,
minutos conocidos/desconocidos, autogoles, asistencias, suplentes, W.O., autenticación y errores.
`DatabaseMigrationsPostgresTest` verifica las mismas consultas sobre PostgreSQL real, con y sin
filtros opcionales. La prueba de PostgreSQL se habilita mediante `MIGRATIONS_PG_TEST_URL` según
[`BASE_DATOS_DOCKER.md`](BASE_DATOS_DOCKER.md#persistencia-y-pruebas).
