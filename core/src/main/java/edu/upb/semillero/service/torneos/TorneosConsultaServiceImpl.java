package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.dto.response.torneos.FilaTablaResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.SancionResponseDto;
import edu.upb.semillero.repository.entity.Partido;
import edu.upb.semillero.repository.entity.torneos.Torneo;
import edu.upb.semillero.repository.entity.torneos.TorneoCategoria;
import edu.upb.semillero.repository.enums.torneos.AmbitoTorneo;
import edu.upb.semillero.repository.enums.torneos.EstadoTorneo;
import edu.upb.semillero.repository.enums.torneos.FormatoTorneo;
import edu.upb.semillero.repository.repos.torneos.PartidoFixtureRepository;
import edu.upb.semillero.repository.repos.torneos.ReglamentoRepository;
import edu.upb.semillero.repository.repos.torneos.TorneoCategoriaRepository;
import edu.upb.semillero.repository.repos.torneos.TorneoRepository;
import edu.upb.semillero.service.EntityRelations;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tarea S4-07: lo que torneos le ofrece por Java a ficha técnica y a scouting
 * (punto 12 del acuerdo de la S1-04). Usa los mismos servicios que la API
 * REST, así que las dos dan siempre lo mismo.
 *
 * <p>Solo IDs, sin datos personales. Nunca tira excepciones de negocio a quien
 * llama: lo que no existe vuelve vacío.
 *
 * <ul>
 *   <li>Un torneo en BORRADOR no aparece: la HU 1 dice que no es visible hasta
 *       abrir inscripciones.</li>
 *   <li>La tabla de un torneo por grupos trae las filas de todos los grupos,
 *       uno detrás del otro, cada una con su posición dentro del grupo. En un
 *       campeonato externo o de eliminación directa no hay tabla y vuelve
 *       vacía.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class TorneosConsultaServiceImpl implements TorneosConsultaService {

    private final TorneoRepository torneos;
    private final TorneoCategoriaRepository categorias;
    private final PartidoFixtureRepository partidos;
    private final TablaPosicionesService tablas;
    private final EstadisticasService estadisticas;
    private final SancionService sanciones;
    private final ReglamentoRepository reglamentos;
    private final EntityRelations relations;

    public TorneosConsultaServiceImpl(TorneoRepository torneos, TorneoCategoriaRepository categorias,
            PartidoFixtureRepository partidos, TablaPosicionesService tablas,
            EstadisticasService estadisticas, SancionService sanciones,
            ReglamentoRepository reglamentos, EntityRelations relations) {
        this.torneos = torneos;
        this.categorias = categorias;
        this.partidos = partidos;
        this.reglamentos = reglamentos;
        this.tablas = tablas;
        this.estadisticas = estadisticas;
        this.sanciones = sanciones;
        this.relations = relations;
    }

    @Override
    public List<TorneoDto> torneosDeTemporada(short temporada) {
        return torneos.findByTemporadaOrderByIdAsc(temporada).stream()
                .filter(torneo -> torneo.getEstado() != EstadoTorneo.BORRADOR)
                .map(this::toDto)
                .toList();
    }

    @Override
    public List<PartidoTorneoDto> fixtureDeCategoria(Long torneoCategoriaId) {
        if (torneoCategoriaId == null) {
            return List.of();
        }
        return partidos.findByTorneoCategoriaIdOrderByJornadaAscFechaInicioAsc(torneoCategoriaId).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public List<FilaTablaDto> tablaDeCategoria(Long torneoCategoriaId) {
        Optional<TorneoCategoria> categoria = torneoCategoriaId == null
                ? Optional.empty() : categorias.findById(torneoCategoriaId);
        if (categoria.isEmpty()) {
            return List.of();
        }
        Torneo torneo = categoria.get().getTorneo();
        // Los mismos casos en que TablaPosicionesService rechaza, pero sin
        // excepción: dentro de la transacción del que llama la dejaría para rollback.
        if (torneo.getAmbito() == AmbitoTorneo.EXTERNO
                || torneo.getFormato() == FormatoTorneo.ELIMINACION_DIRECTA
                || reglamentos.findByTorneoId(torneo.getId()).isEmpty()) {
            return List.of();
        }
        return tablas.tablas(torneo.getId(), torneoCategoriaId, null).stream()
                .flatMap(tabla -> tabla.filas().stream())
                .map(this::toDto)
                .toList();
    }

    @Override
    public Optional<EstadisticasJugadorDto> estadisticasDeJugador(Long jugadorId, Long torneoId) {
        return estadisticas.buscarDeJugador(torneoId, jugadorId)
                .map(resumen -> new EstadisticasJugadorDto(resumen.jugadorId(), resumen.torneoId(),
                        resumen.partidosJugados(), resumen.minutosJugados(), resumen.goles(),
                        resumen.asistencias(), resumen.amarillas(), resumen.rojas()));
    }

    @Override
    public List<SancionDto> sancionesVigentesDeJugador(Long jugadorId) {
        if (jugadorId == null) {
            return List.of();
        }
        return sanciones.delJugador(jugadorId, true).stream()
                .map(this::toDto)
                .toList();
    }

    private TorneoDto toDto(Torneo torneo) {
        return new TorneoDto(torneo.getId(), torneo.getNombre(), torneo.getTemporada(),
                torneo.getAmbito(), torneo.getEstado(), torneo.getFechaInicio(), torneo.getFechaFin());
    }

    private PartidoTorneoDto toDto(Partido partido) {
        return new PartidoTorneoDto(partido.getId(), relations.id(partido.getTorneoCategoria()),
                relations.id(partido.getGrupo()), partido.getFase(), partido.getJornada(),
                relations.id(partido.getClubLocal()), relations.id(partido.getClubVisitante()),
                partido.getFechaInicio(), relations.id(partido.getCancha()), partido.getEstado(),
                partido.getGolesLocal(), partido.getGolesVisitante());
    }

    private FilaTablaDto toDto(FilaTablaResponseDto fila) {
        return new FilaTablaDto(fila.clubId(), fila.posicion(), fila.puntos(), fila.partidosJugados(),
                fila.ganados(), fila.empatados(), fila.perdidos(), fila.golesFavor(), fila.golesContra(),
                fila.diferenciaGoles());
    }

    private SancionDto toDto(SancionResponseDto sancion) {
        return new SancionDto(sancion.id(), sancion.jugadorId(), sancion.torneoId(), sancion.origen(),
                sancion.fechasSuspension(), sancion.fechasCumplidas(), sancion.estado());
    }
}
