# Pruebas de aceptación del módulo de Torneos

Tarea S4-19. Grupo 4. Actualizado: 28/10/2026.

Cada criterio de aceptación de las 16 HU (`CONTEXTO_TORNEOS.md`, sección 7) con la prueba
automática que lo comprueba. Todas corren en `./mvnw clean verify` contra H2 en modo PostgreSQL y
pasan por la API REST de verdad (MockMvc), salvo las marcadas como unitarias. La colección de
Postman (`docs/torneos/postman`) repite el flujo principal contra PostgreSQL.

Las pruebas de API están en `semillero-api/src/test/java/edu/upb/semillero/controller/torneos/`
y las unitarias en `.../service/torneos/`.

Estado: **Cumple** · **Parcial** (el backend lo hace pero depende de algo de otro grupo) ·
**Fuera del backend** (pantallas, que van en Flutter).

## HU 1 · Crear torneo

| Criterio | Prueba | Estado |
| --- | --- | --- |
| Nombre, temporada, tipo, ámbito, formato y fechas; organizador si es externo | `TorneoApiTest.creaElTorneoEnBorradorConSusCategorias`, `elTorneoExternoExigeOrganizador` | Cumple |
| Una o varias categorías con nombre y rango de edad | `TorneoApiTest.validaLasCategoriasDelTorneo`, `TorneoCategoriaServiceTest` | Cumple |
| No se guarda si la fecha de fin es anterior a la de inicio | `TorneoApiTest.noGuardaFechasIncoherentes` | Cumple |
| Nace en Borrador y no es visible para los equipos hasta abrir inscripciones | `TorneoApiTest.abreLasInscripcionesUnaSolaVez`, `InscripcionEquipoApiTest.noInscribeSiElTorneoSigueEnBorrador`, `ContratoTorneosConsultaTest.unTorneoEnBorradorNoSeVe` | Cumple |

## HU 2 · Configurar el reglamento

| Criterio | Prueba | Estado |
| --- | --- | --- |
| Puntos por victoria, empate y derrota | `ReglamentoApiTest.creaElReglamentoConSuOrdenDeDesempates`, `TablaPosicionesServiceTest` | Cumple |
| Lista ordenada de desempates | `ReglamentoApiTest.reemplazaElOrdenDeLosDesempates`, `TablaPosicionesServiceTest.elEnfrentamientoDirectoSeCalculaEntreLosEmpatados` | Cumple |
| Umbral de amarillas y fechas por roja | `RegimenDisciplinarioApiTest.lasAmarillasSeAcumulanEnElTorneoHastaElUmbral`, `laRojaSuspendeLaFechaSiguienteYSeDescuentaAlJugarla` | Cumple |
| Fecha de corte, por defecto 31/12 de la temporada | `ReglamentoApiTest.laFechaDeCorteEsEl31DeDiciembreDeLaTemporada` | Cumple |
| Si cambia en un torneo en curso, la tabla se recalcula y queda quién lo cambió | La tabla no se guarda (`TablaPosicionesApiTest`); historial: `ReglamentoApiTest.elHistorialRegistraCadaCambioDelReglamento` | Cumple |

## HU 3 · Registrar canchas

| Criterio | Prueba | Estado |
| --- | --- | --- |
| Nombre, sede, dirección y superficie | `SedeCanchaApiTest.crudCompletoDeSedeYCancha` | Cumple |
| Descripción con dimensiones, techada, iluminación y vestuarios | `CanchaServiceTest.guardaLasDimensionesYLaDescripcion` | Cumple |
| Superficie y descripción en el fixture y en el partido | `FixtureApiTest.generaLaLigaPorRotacionConTodosContraTodos`, `ConsultaAppApiTest.elPartidoYLosCerradosSeConsultanPorLaApi`, `FlujoCompletoPartidoApiTest` (planilla) | Cumple |
| No se elimina una cancha con partidos programados | `CanchaServiceTest.noBorraUnaCanchaConPartidosProgramados` | Cumple |

## HU 4 · Inscribir un equipo

| Criterio | Prueba | Estado |
| --- | --- | --- |
| Solo con inscripciones abiertas y antes de la fecha límite | `InscripcionEquipoApiTest.noInscribeSiElTorneoSigueEnBorrador`, `noInscribeDespuesDeLaFechaLimite` | Cumple |
| Queda Pendiente hasta que el organizador la revise | `InscripcionEquipoApiTest.inscribeUnEquipoYQuedaPendiente` | Cumple |
| El mismo equipo no se inscribe dos veces en la categoría | `InscripcionEquipoApiTest.elMismoClubNoSeInscribeDosVecesEnLaCategoria` | Cumple |
| Con el cupo lleno avisa y no deja continuar | `InscripcionEquipoApiTest.respetaElCupoMaximoDeLaCategoria` | Cumple |

## HU 5 · Lista de buena fe

| Criterio | Prueba | Estado |
| --- | --- | --- |
| Jugadores de ficha técnica, sin cargar datos personales | `ListaBuenaFeApiTest.agregaUnJugadorHabilitadoYLoListaConSuDorsal` | Cumple |
| Dorsal y posición, sin repetir dorsal | `ListaBuenaFeApiTest.noRepiteElDorsalDentroDelEquipo` | Cumple |
| Edad a la fecha de corte | `ListaBuenaFeApiTest.rechazaAlJugadorFueraDelRangoDeEdadALaFechaDeCorte` | Cumple |
| Documentos y apto médico vigentes | `ListaBuenaFeServiceTest.exigeDocumentosYAptoMedicoVigentes` (unitaria) | **Parcial**: `documentos_jugador` es de ficha técnica y no existe; hoy responde siempre vigente |
| Sin sanción y no inscrito en otro equipo del torneo | `ListaBuenaFeServiceTest.rechazaAlJugadorConSancionVigenteEnEsteTorneo`, `ListaBuenaFeApiTest.unJugadorEstaEnUnSoloEquipoDelTorneo` | Cumple |
| Motivo exacto del rechazo | Todas las anteriores comprueban el `detail` | Cumple |
| No se confirma con menos del mínimo ni más del máximo | `ListaBuenaFeApiTest.confirmaLaListaSoloConElMinimoYDespuesNoLaDejaModificar`, `noPasaDelMaximoDeJugadoresDelReglamento` | Cumple |
| La lista confirmada se exporta en PDF | `ListaBuenaFeApiTest.exportaLaListaEnPdf` | Cumple |

## HU 6 · Aprobar inscripciones

| Criterio | Prueba | Estado |
| --- | --- | --- |
| Lista con estado pendiente, observada, aprobada o rechazada | `InscripcionEquipoApiTest.filtraElListadoPorTorneoYPorEstado` | Cumple |
| Observar o rechazar pide motivo y el entrenador lo ve | `InscripcionEquipoApiTest.elOrganizadorObservaRechazaYApruebaConMotivo` | Cumple |
| Solo las aprobadas entran al fixture | `FixtureApiTest.noGeneraDosVecesNiSinEquiposAprobados` | Cumple |
| Cada cambio de estado queda con usuario y fecha | `InscripcionEquipoServiceTest.elHistorialDevuelveLosCambiosDeMasNuevoAMasViejo` | Cumple |

## HU 7 · Generar el fixture

| Criterio | Prueba | Estado |
| --- | --- | --- |
| Liga por rotación, n − 1 fechas, un libre si n es impar | `FixtureApiTest.generaLaLigaPorRotacionConTodosContraTodos`, `conUnNumeroImparDeEquiposUnoQuedaLibreCadaFecha` | Cumple |
| Eliminación directa con siembra o sorteo | `FixtureApiTest.generaLasLlavesYAvanzaDeRondaConLosGanadores`, `conTresEquiposUnoPasaDirectoALaFinal` | Cumple |
| Cancha y horario sin choques y con descanso mínimo | `FixtureApiTest.nuncaPoneDosPartidosEnLaMismaCanchaYHorario`, `avisaCuandoNoHayCanchaNiHorarioLibre` | Cumple |
| Ajuste manual con nueva validación | `FixtureApiTest.ajustaUnPartidoAManoYRechazaElChoque` | Cumple |
| Exportar a PDF y publicar | `FixtureApiTest.exportaElFixtureEnPdf`, `publicaElFixtureDelTorneo` | Cumple |
| En externo no se genera: partidos a mano con el rival como club | `FixtureApiTest.enUnCampeonatoExternoNoSeGeneraFixtureYSeCarganLosPartidosAMano` | Cumple |

## HU 8 · Reprogramar un partido

| Criterio | Prueba | Estado |
| --- | --- | --- |
| Motivo obligatorio | `ReprogramacionApiTest.validaElMotivoLaFechaYQueCambieAlgo` | Cumple |
| Valida conflictos de fecha, hora y cancha | `ReprogramacionApiTest.rechazaLosChoquesDeCanchaYDeEquipo`, `ReprogramacionServiceTest.respetaElDescansoMinimoDelReglamento` | Cumple |
| Notifica a los dos equipos | `ReprogramacionApiTest.reprogramaGuardaElHistorialYAvisaALosEquipos` | Cumple |
| Guarda historial | El mismo | Cumple |
| No se reprograma un partido cerrado | `ReprogramacionApiTest.noReprogramaUnPartidoCerrado` | Cumple |

## HU 9 · Llenar la planilla

| Criterio | Prueba | Estado |
| --- | --- | --- |
| Precarga torneo, categoría, fecha, hora, cancha con superficie, equipos y árbitro | `FlujoCompletoPartidoApiTest.unPartidoDeLaPlanillaALaTabla` | Cumple |
| Titulares y suplentes de la lista con dorsal, y capitán, en `participaciones_partido` | `PlanillaApiTest.seGuardaIncompletaYSeConfirmaCuandoEstaCompleta`, `validaLosJugadoresDeLaPlanilla` | Cumple |
| Suspendidos y no habilitados aparecen bloqueados | `PlanillaApiTest.losSuspendidosYLosNoHabilitadosAparecenBloqueados`, `RegimenDisciplinarioApiTest.laRojaSuspendeLaFechaSiguienteYSeDescuentaAlJugarla` | Cumple |
| Cuerpo técnico y observaciones del árbitro | `PlanillaApiTest.seGuardaIncompletaYSeConfirmaCuandoEstaCompleta`, `EventosYCierreApiTest.cierraConLosGolesDeLosEventos` | Cumple |
| Se guarda incompleta y se retoma | `PlanillaApiTest.seGuardaIncompletaYSeConfirmaCuandoEstaCompleta` | Cumple |
| Se exporta a PDF | `PlanillaApiTest.exportaLaPlanillaEnPdf` | Cumple |
| Solo la mesa de control de su partido | `SeguridadPartidoApiTest.laMesaDeControlSoloManejaElPartidoQueTieneAsignado` | Cumple |

## HU 10 · Registrar los eventos del partido

| Criterio | Prueba | Estado |
| --- | --- | --- |
| Goles con autor, minuto, tipo y asistencia opcional | `EventosYCierreApiTest.validaLosGoles` | Cumple |
| Tarjetas con motivo; cambios con minuto, sale y entra | `EventosYCierreApiTest.validaLasTarjetas`, `validaLosCambios` | Cumple |
| Solo jugadores de la planilla | `EventosYCierreApiTest.losEventosPidenLaPlanillaConfirmada` | Cumple |
| Calcula minutos jugados y los guarda | `MinutosJugadosTest` (unitaria), `FlujoCompletoPartidoApiTest` | Cumple |
| Al cerrar se actualizan tabla, estadísticas y sanciones | `FlujoCompletoPartidoApiTest`, `EstadisticasApiTest`, `RegimenDisciplinarioApiTest` | Cumple |
| Solo el organizador reabre, y queda registrado | `EventosYCierreApiTest.reabreSoloUnPartidoCerradoYConMotivo`, `SeguridadPartidoApiTest` | Cumple |

## HU 11 · Tabla de posiciones

| Criterio | Prueba | Estado |
| --- | --- | --- |
| PJ, PG, PE, PP, GF, GC, DG y puntos | `TablaPosicionesApiTest.seCalculaDesdeLosPartidosCerrados` | Cumple |
| Una por categoría y una por grupo; en externos no hay | `TablaPosicionesApiTest.enElFormatoDeGruposHayUnaTablaPorGrupo`, `noHayTablaEnUnCampeonatoExternoNiEnEliminacionDirecta` | Cumple |
| Desempates del reglamento | `TablaPosicionesServiceTest` (unitaria, un caso por criterio) | Cumple |
| Desde partidos cerrados; anulados y W.O. según el reglamento | `TablaPosicionesServiceTest.soloCuentanLosPartidosCerrados`, `TablaPosicionesApiTest.seCalculaDesdeLosPartidosCerrados` | Cumple |

## HU 12 · Goleadores y estadísticas

| Criterio | Prueba | Estado |
| --- | --- | --- |
| Goleadores, asistencias, vallas menos vencidas y fair play | `EstadisticasApiTest.lasTablasDelTorneoSalenDeLosPartidosCerrados` | Cumple |
| Resumen por jugador: partidos, minutos, goles, asistencias y tarjetas | `EstadisticasApiTest.elResumenDelJugadorSumaSusPartidosCerrados` | Cumple |
| No cuentan los partidos anulados | Solo se leen partidos FINALIZADO (las dos anteriores); `TablaPosicionesServiceTest.soloCuentanLosPartidosCerrados` | Cumple |
| Se actualizan al cerrar cada partido | `EstadisticasApiTest.lasTablasDelTorneoSalenDeLosPartidosCerrados` (antes y después del cierre) | Cumple |

## HU 13 · Suspensión automática por tarjetas

| Criterio | Prueba | Estado |
| --- | --- | --- |
| Acumula tarjetas por jugador y torneo | `RegimenDisciplinarioApiTest.lasAmarillasSeAcumulanEnElTorneoHastaElUmbral` | Cumple |
| Al llegar al umbral genera la suspensión con su número de fechas | El mismo, y `laRojaSuspendeLaFechaSiguienteYSeDescuentaAlJugarla` | Cumple |
| Se descuenta con las fechas jugadas del mismo torneo | `RegimenDisciplinarioApiTest.laRojaSuspendeLaFechaSiguienteYSeDescuentaAlJugarla` | Cumple |
| Lista de sancionados por fecha | El mismo (`/sancionados?jornada=`) | Cumple |
| Sanción manual con motivo y apelación con resolución | `RegimenDisciplinarioApiTest.laSancionManualSeApelaYSeReduce`, `laApelacionAceptadaAnulaLaSancion` | Cumple |

## HU 14 · Consulta desde la app móvil

| Criterio | Prueba | Estado |
| --- | --- | --- |
| Próximos partidos con fecha, hora, cancha y superficie | `ConsultaAppApiTest.losProximosPartidosTraenLaCanchaYSuSuperficie` | Cumple |
| Tabla y goleadores de su categoría | `TablaPosicionesApiTest`, `EstadisticasApiTest` (los mismos endpoints) | Cumple |
| Aviso de suspensión vigente | `ConsultaAppApiTest.miCuentaAvisaLaSuspensionYLasNotificacionesSeLeen` | Cumple |
| Notificación al reprogramar o al publicar un resultado | `ReprogramacionApiTest.reprogramaGuardaElHistorialYAvisaALosEquipos`, `ConsultaAppApiTest.miCuentaAvisaLaSuspensionYLasNotificacionesSeLeen` | Cumple |
| Misma API REST y misma cuenta que la web | `SeguridadSprint4ApiTest.cadaUnoVeSoloSusNotificaciones` | Cumple |
| Las pantallas de la app | — | Fuera del backend |

## HU 15 · Exponer datos por API

| Criterio | Prueba | Estado |
| --- | --- | --- |
| Torneos, fixture, tabla, partidos, estadísticas y sanciones por REST | `ConsultaAppApiTest`, `EstadisticasApiTest`, `RegimenDisciplinarioApiTest`, `TablaPosicionesApiTest` | Cumple |
| Los mismos datos por interfaz Java para ficha técnica y scouting | `ContratoTorneosConsultaTest` | Cumple |
| Contratos REST en OpenAPI | `OpenApiDocsTest.documentaLosContratosDeLosSprints3Y4`, `documentaElTokenSoloEnLasRutasDeTorneos` | Cumple |
| `PartidoFinalizado` al cerrar un partido | `PartidoFinalizadoApiTest`, `OpenApiDocsTest.documentaElEventoPartidoFinalizado` | Cumple |
| Solo IDs, sin datos personales | Revisado en cada DTO de respuesta de torneos: jugadores y usuarios van por ID | Cumple |
| Autenticación y error claro si el token no es válido | `SeguridadSprint4ApiTest.elErrorDiceSiFaltaElTokenOSiNoSirve` | Cumple |

## HU 16 · Control de acceso por roles

| Criterio | Prueba | Estado |
| --- | --- | --- |
| Roles administrador, organizador, entrenador, mesa de control y consulta | `SeguridadTorneosApiTest.elLoginDevuelveElTokenYLosRoles` | Cumple |
| Solo el organizador configura, aprueba y reprograma | `SeguridadTorneosApiTest.elRolConsultaLeePeroNoEscribe`, `SeguridadPartidoApiTest` | Cumple |
| El entrenador solo maneja la lista de su equipo | `SeguridadTorneosApiTest.elEntrenadorNoTocaLaListaDeOtroClub` | Cumple |
| La mesa de control solo sus partidos asignados | `SeguridadPartidoApiTest.laMesaDeControlSoloManejaElPartidoQueTieneAsignado` | Cumple |
| Consulta solo lee | `SeguridadTorneosApiTest.elRolConsultaLeePeroNoEscribe`, `SeguridadSprint4ApiTest.laConsultaLeeLasEstadisticasYLasSanciones` | Cumple |
| Acciones no permitidas se rechazan y se registran | `SeguridadTorneosApiTest.elAccesoRechazadoQuedaRegistrado` | Cumple |

## Lo que queda fuera de estas pruebas

- **Las pantallas** de todas las HU (S1-11, S1-15, S1-19, S1-22, S2-09, S2-13, S2-19, S3-05, S3-11,
  S3-15, S3-20, S4-06, S4-13, S4-14) y las pruebas en dispositivos (S4-17): van en Flutter, en
  `AdriBc16/furbo`.
- **Documentos y apto médico** (HU 5): parcial hasta que ficha técnica cree `documentos_jugador`.
- **PostgreSQL de verdad**: estas pruebas usan H2. La colección de Postman cubre los sprints 1 a 3
  contra PostgreSQL; el sprint 4 todavía no está en la colección.
