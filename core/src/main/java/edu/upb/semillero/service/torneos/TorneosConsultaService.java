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
 * <p>La implementaci&oacute;n es la tarea S4-07 del sprint 4, porque depende de la
 * tabla de posiciones (HU 11, S3-12) y de las estad&iacute;sticas (HU 12, S4-11). Ac&aacute;
 * est&aacute; el contrato, que es lo que la S1-04 pide acordar: si a ficha t&eacute;cnica o
 * a scouting les falta algo, se agrega ahora y no cuando est&eacute; escrita.
 */
public interface TorneosConsultaService {

    List<TorneoDto> torneosDeTemporada(short temporada);

    List<PartidoTorneoDto> fixtureDeCategoria(Long torneoCategoriaId);

    List<FilaTablaDto> tablaDeCategoria(Long torneoCategoriaId);

    Optional<EstadisticasJugadorDto> estadisticasDeJugador(Long jugadorId, Long torneoId);

    List<SancionDto> sancionesVigentesDeJugador(Long jugadorId);
}
