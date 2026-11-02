package edu.upb.semillero.service.torneos;

import java.util.List;
import java.util.Optional;

/**
 * Lo que torneos le ofrece a ficha t&eacute;cnica y a scouting. Punto 12 del acuerdo
 * de la S1-04 y HU 15.
 *
 * <p>Solo IDs, sin datos personales. Entre m&oacute;dulos de la aplicaci&oacute;n no se usa
 * REST: los otros m&oacute;dulos inyectan esta interfaz y nunca los repositorios ni
 * las entidades de torneos.
 *
 * <p>La implementaci&oacute;n es {@link TorneosConsultaServiceImpl} (tarea S4-07),
 * que usa los mismos servicios que la API REST de torneos.
 */
public interface TorneosConsultaService {

    List<TorneoDto> torneosDeTemporada(short temporada);

    List<PartidoTorneoDto> fixtureDeCategoria(Long torneoCategoriaId);

    List<FilaTablaDto> tablaDeCategoria(Long torneoCategoriaId);

    Optional<EstadisticasJugadorDto> estadisticasDeJugador(Long jugadorId, Long torneoId);

    List<SancionDto> sancionesVigentesDeJugador(Long jugadorId);
}
