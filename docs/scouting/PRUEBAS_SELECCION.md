# Pruebas de selección: contrato del backend

La convocatoria representa una prueba deportiva de un club, con varios inscritos.
El DT organiza los ejercicios presencialmente y registra una evaluación individual
por jugador. Aceptar al participante no crea automáticamente un fichaje.

Implementación propia de `scouting/HU-13-integracion-segura`. No necesita código
de `scouting/backend-final` ni modifica Flutter.

## Flujo y permisos

1. Un usuario con vínculo vigente `DT` o `EN` publica la convocatoria de su club.
2. El jugador autenticado inscribe su propia ficha, sin enviar un `jugadorId`.
3. Solo el DT/entrenador del club consulta los participantes y decide su estado.
4. Desde el inicio de la prueba registra asistencia, observaciones y resultado.
5. El jugador consulta únicamente su inscripción y su historial de evaluaciones.

El vínculo con el club se comprueba en la DB en cada operación. El rol ADMIN
por sí solo no habilita gestionar convocatorias de un club. Las listas públicas
para usuarios autenticados muestran el número de inscritos y la inscripción
propia; no incluyen la lista de participantes, correos, teléfonos ni documentos.

Por ahora solo se inscriben jugadores activos de 18 años o más y fecha de
nacimiento conocida. La participación de menores requiere un flujo de tutor
verificado que todavía no está implementado. Las edades se calculan en servidor.

## Rutas

Todas requieren `Authorization: Bearer <token>`; base `/api/v1/scouting/pruebas`.
Listados e historiales usan `page` desde 0 y `size` entre 1 y 100 (por defecto 20),
con el formato `PageResponse` del backend.

| Método y ruta relativa | Uso |
| --- | --- |
| `GET /?clubId=...` | Listar convocatorias; club opcional |
| `POST /` | Publicar convocatoria |
| `GET /{id}` | Detalle y, si corresponde, inscripción propia |
| `PUT /{id}` | Actualizar datos, cupos o estado con `version` |
| `DELETE /{id}?version=...` | Cancelación lógica; conserva inscripciones e historial |
| `POST /{id}/inscripciones` | Inscribir al jugador autenticado |
| `GET /{id}/inscripciones` | Lista privada del DT/entrenador del club |
| `DELETE /{id}/inscripciones/{uuid}?version=...` | Retiro propio antes del inicio |
| `PATCH /{id}/inscripciones/{uuid}` | Decisión del DT/entrenador con `version` |
| `POST /{id}/inscripciones/{uuid}/evaluaciones` | Registrar evaluación individual |
| `GET /{id}/inscripciones/{uuid}/evaluaciones` | Historial propio o del DT/entrenador |

Ejemplo de publicación; las fechas deben reemplazarse por fechas futuras:

```json
{
  "clubId": 1,
  "titulo": "Prueba de selección de delanteros",
  "fechaPrueba": "2027-04-02",
  "horaPrueba": "09:00:00",
  "lugar": "Cancha central",
  "categoria": "Adultos",
  "descripcion": "Ejercicios técnicos y partido de evaluación",
  "cupos": 30,
  "limiteInscripcion": "2027-04-02T12:00:00Z",
  "edadMin": 18,
  "edadMax": 25,
  "posiciones": ["Delantero"],
  "requisitos": ["Presentarse 15 minutos antes"]
}
```

`fechaPrueba` y `horaPrueba` se interpretan en `America/La_Paz`, configurable con
`scouting.pruebas.zona`. La respuesta incluye `zona` e `inicio` como instante UTC;
el cliente debe usar esos datos al mostrar la hora. El cierre de inscripción es
un instante y no puede superar el inicio de la prueba.

Inscripción: cuerpo opcional, o `{"nota":"Soy delantero"}` (máximo 500 caracteres).
No acepta identidades ajenas. Repetir la petición devuelve la misma inscripción;
una inscripción retirada no se reactiva. La respuesta contiene el UUID y `version`.

Decisión: `{"estado":"PRESELECCIONADO","version":0}`.
Evaluación:

```json
{
  "asistencia": "PRESENTE",
  "resultado": "ACEPTADO",
  "observaciones": "Buen control y pase. Continuar evaluación con el club.",
  "version": 0
}
```

La asistencia admite `PRESENTE` o `AUSENTE`; las observaciones son obligatorias
y admiten hasta 1000 caracteres. Cada evaluación conserva una entrada histórica
con fecha y evaluador. El jugador puede ver estas observaciones: no son notas
internas del club.

## Estados y conflictos

- Convocatoria: `ABIERTA`, `EN_CURSO`, `CERRADA`, `CANCELADA`. La inscripción
  requiere `ABIERTA` y que no hayan pasado el plazo ni el inicio.
- Participante: `PENDIENTE`, `PRESELECCIONADO`, `ACEPTADO`, `RECHAZADO`, `RETIRADO`.
  Los tres primeros ocupan cupo; rechazo o retiro lo liberan.
- Las decisiones no permiten reactivar una inscripción retirada o actuar sobre
  una convocatoria cancelada. Reactivar un rechazo exige cupo disponible.
- No se cambia el club, la fecha/hora ni las edades de una convocatoria publicada.
  Para reprogramar, cancelar y publicar otra. Reducir cupos por debajo de los
  inscritos activos produce conflicto.
- La inscripción y los cambios de cupo se serializan mediante un bloqueo de la
  convocatoria. Una restricción única impide duplicar jugador/convocatoria.
- Las modificaciones requieren la última `version` del registro afectado.
  Un `409` exige recargar; no mostrar éxito antes de recibir la respuesta.

## Persistencia y validación

`V018__scouting_pruebas_de_seleccion.sql` crea cinco tablas propias. La tabla de
inscripciones es `scouting_inscripcion_prueba`, con UUID; no es
`inscripciones_prueba` de la otra rama, que usa IDs numéricos y otro contrato.
Ambas ramas tienen migraciones distintas con números 16, 17 y 18: no combinarlas
ni intercambiar sus JAR sobre la misma DB sin una reconciliación acordada.
El historial y la numeración del VPS siguen pendientes de comprobación.

Validación local: suite completa de 571 casos sin fallos, errores ni omitidos en
Docker; además, los ocho casos de `ConvocatoriaPruebaApiTest` pasaron directamente
en PostgreSQL 17, con Flyway y Hibernate `validate`. Cubren permisos vigentes,
privacidad, menores, cupo concurrente, plazos, retiro, versiones e historial.
Estos resultados corresponden al backend; no prueban integración con Flutter
ni con la DB compartida del VPS.
