package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.dto.response.torneos.FilaTablaResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.TablaPosicionesResponseDto;
import edu.upb.semillero.repository.entity.Club;
import edu.upb.semillero.repository.entity.Partido;
import edu.upb.semillero.repository.entity.torneos.CriterioDesempate;
import edu.upb.semillero.repository.entity.torneos.EventoPartido;
import edu.upb.semillero.repository.entity.torneos.InscripcionEquipo;
import edu.upb.semillero.repository.entity.torneos.Reglamento;
import edu.upb.semillero.repository.entity.torneos.ReglamentoDesempate;
import edu.upb.semillero.repository.entity.torneos.Torneo;
import edu.upb.semillero.repository.entity.torneos.TorneoCategoria;
import edu.upb.semillero.repository.enums.EstadoPartido;
import edu.upb.semillero.repository.enums.FaseTorneo;
import edu.upb.semillero.repository.enums.torneos.AmbitoTorneo;
import edu.upb.semillero.repository.enums.torneos.EstadoInscripcion;
import edu.upb.semillero.repository.enums.torneos.FormatoTorneo;
import edu.upb.semillero.repository.enums.torneos.TipoEventoPartido;
import edu.upb.semillero.repository.repos.torneos.EventoPartidoRepository;
import edu.upb.semillero.repository.repos.torneos.GrupoRepository;
import edu.upb.semillero.repository.repos.torneos.InscripcionEquipoRepository;
import edu.upb.semillero.repository.repos.torneos.PartidoFixtureRepository;
import edu.upb.semillero.repository.repos.torneos.ReglamentoDesempateRepository;
import edu.upb.semillero.repository.repos.torneos.ReglamentoRepository;
import edu.upb.semillero.repository.repos.torneos.TorneoCategoriaRepository;
import edu.upb.semillero.service.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * HU 11 - Tarea S3-13: los desempates en el orden del reglamento, cada uno
 * aplicado solo a los que siguen empatados. Los casos están armados para que
 * cada criterio dé un orden distinto.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TablaPosicionesServiceTest {
    private static final Long TORNEO = 1L;
    private static final Long CATEGORIA = 2L;
    private static final Long REGLAMENTO = 5L;

    @Mock TorneoCategoriaRepository categorias;
    @Mock PartidoFixtureRepository partidos;
    @Mock InscripcionEquipoRepository inscripciones;
    @Mock GrupoRepository grupos;
    @Mock ReglamentoRepository reglamentos;
    @Mock ReglamentoDesempateRepository desempates;
    @Mock EventoPartidoRepository eventos;
    @InjectMocks TablaPosicionesService service;

    private final Club a = club(10L, "A");
    private final Club b = club(11L, "B");
    private final Club c = club(12L, "C");
    private final Club d = club(13L, "D");
    private final List<Partido> jugados = new ArrayList<>();
    private final List<EventoPartido> tarjetas = new ArrayList<>();
    private Torneo torneo;
    private long siguientePartido = 100;

    @BeforeEach
    void torneo() {
        torneo = new Torneo();
        torneo.setId(TORNEO);
        torneo.setNombre("Liga de prueba");
        torneo.setAmbito(AmbitoTorneo.INTERNO);
        torneo.setFormato(FormatoTorneo.LIGA);
        TorneoCategoria categoria = new TorneoCategoria();
        categoria.setId(CATEGORIA);
        categoria.setNombre("Sub-14");
        categoria.setTorneo(torneo);
        Reglamento reglamento = new Reglamento();
        reglamento.setId(REGLAMENTO);
        reglamento.setPuntosVictoria((short) 3);
        reglamento.setPuntosEmpate((short) 1);
        reglamento.setPuntosDerrota((short) 0);

        when(categorias.findByIdAndTorneoId(CATEGORIA, TORNEO)).thenReturn(Optional.of(categoria));
        when(reglamentos.findByTorneoId(TORNEO)).thenReturn(Optional.of(reglamento));
        when(inscripciones.findByTorneoCategoriaIdAndEstadoOrderByIdAsc(CATEGORIA,
                EstadoInscripcion.APROBADA)).thenReturn(List.of(aprobada(a), aprobada(b),
                aprobada(c), aprobada(d)));
        when(partidos.findByTorneoCategoriaIdOrderByJornadaAscFechaInicioAsc(CATEGORIA))
                .thenReturn(jugados);
        when(eventos.findByPartidoIdIn(any())).thenReturn(tarjetas);
    }

    @Test
    void primeroLosPuntosDespuesLaDiferenciaDeGoles() {
        criterios("DIF_GOLES", "GOLES_FAVOR");
        partido(a, b, 2, 0);
        partido(c, d, 1, 0);

        TablaPosicionesResponseDto tabla = tabla();

        assertThat(orden(tabla)).containsExactly(a.getId(), c.getId(), d.getId(), b.getId());
        FilaTablaResponseDto primero = tabla.filas().get(0);
        assertThat(primero.puntos()).isEqualTo(3);
        assertThat(primero.partidosJugados()).isEqualTo(1);
        assertThat(primero.golesFavor()).isEqualTo(2);
        assertThat(primero.diferenciaGoles()).isEqualTo(2);
        assertThat(primero.posicion()).isEqualTo(1);
    }

    /**
     * A, B y C empatan en 3 puntos. Por diferencia de goles sale B, C, A; entre
     * ellos, A le ganó a B y C le ganó a A, así que el enfrentamiento directo
     * da C, A, B.
     */
    @Test
    void elEnfrentamientoDirectoSeCalculaEntreLosEmpatados() {
        partido(a, b, 1, 0);
        partido(b, d, 5, 0);
        partido(c, a, 1, 0);

        criterios("ENFRENTAMIENTO");
        assertThat(orden(tabla())).containsExactly(c.getId(), a.getId(), b.getId(), d.getId());

        criterios("DIF_GOLES", "ENFRENTAMIENTO");
        assertThat(orden(tabla())).containsExactly(b.getId(), c.getId(), a.getId(), d.getId());
    }

    @Test
    void elFairPlayPremiaAlQueTieneMenosTarjetas() {
        criterios("DIF_GOLES", "FAIR_PLAY");
        Partido empate = partido(a, b, 1, 1);
        tarjeta(empate, a, TipoEventoPartido.AMARILLA);
        tarjeta(empate, b, TipoEventoPartido.AMARILLA);
        tarjeta(empate, a, TipoEventoPartido.ROJA);

        TablaPosicionesResponseDto tabla = tabla();

        assertThat(orden(tabla).subList(0, 2)).containsExactly(b.getId(), a.getId());
        assertThat(tabla.filas().get(1).fairPlay()).isEqualTo(4);
        assertThat(tabla.filas().get(0).fairPlay()).isEqualTo(1);
    }

    /** El sorteo da siempre el mismo orden: si no, la tabla cambiaría en cada consulta. */
    @Test
    void elSorteoEsReproducible() {
        criterios("SORTEO");

        List<Long> primera = orden(tabla());
        List<Long> segunda = orden(tabla());

        assertThat(primera).containsExactlyInAnyOrder(a.getId(), b.getId(), c.getId(), d.getId());
        assertThat(segunda).isEqualTo(primera);
    }

    @Test
    void sinCriteriosQueLosSepareQuedanPorElIdDelClub() {
        criterios();

        assertThat(orden(tabla())).containsExactly(a.getId(), b.getId(), c.getId(), d.getId());
    }

    /** Solo cuentan los partidos cerrados de la fase de liga; los anulados no. */
    @Test
    void soloCuentanLosPartidosCerrados() {
        criterios("DIF_GOLES");
        partido(a, b, 3, 0).setEstado(EstadoPartido.CANCELADO);
        partido(c, d, 3, 0).setEstado(EstadoPartido.PROGRAMADO);
        partido(d, a, 1, 0);

        TablaPosicionesResponseDto tabla = tabla();

        assertThat(tabla.partidosCerrados()).isEqualTo(1);
        assertThat(orden(tabla).get(0)).isEqualTo(d.getId());
        assertThat(tabla.filas().stream().mapToInt(FilaTablaResponseDto::partidosJugados).sum())
                .isEqualTo(2);
    }

    @Test
    void unCampeonatoExternoNoTieneTabla() {
        torneo.setAmbito(AmbitoTorneo.EXTERNO);

        assertThatThrownBy(this::tabla).isInstanceOf(BusinessException.class)
                .hasMessageContaining("campeonato externo");
    }

    @Test
    void sinReglamentoNoHayTabla() {
        when(reglamentos.findByTorneoId(TORNEO)).thenReturn(Optional.empty());

        assertThatThrownBy(this::tabla).isInstanceOf(BusinessException.class)
                .hasMessageContaining("no tiene reglamento");
    }

    private TablaPosicionesResponseDto tabla() {
        return service.tablas(TORNEO, CATEGORIA, null).get(0);
    }

    private List<Long> orden(TablaPosicionesResponseDto tabla) {
        return tabla.filas().stream().map(FilaTablaResponseDto::clubId).toList();
    }

    private void criterios(String... codigos) {
        List<ReglamentoDesempate> lista = new ArrayList<>();
        short orden = 1;
        for (String codigo : codigos) {
            CriterioDesempate criterio = new CriterioDesempate();
            criterio.setCodigo(codigo);
            ReglamentoDesempate desempate = new ReglamentoDesempate();
            desempate.setCriterioDesempate(criterio);
            desempate.setOrden(orden++);
            lista.add(desempate);
        }
        when(desempates.findByReglamentoIdOrderByOrdenAsc(REGLAMENTO)).thenReturn(lista);
    }

    private Partido partido(Club local, Club visitante, int golesLocal, int golesVisitante) {
        Partido partido = new Partido();
        partido.setId(siguientePartido++);
        partido.setClubLocal(local);
        partido.setClubVisitante(visitante);
        partido.setGolesLocal((short) golesLocal);
        partido.setGolesVisitante((short) golesVisitante);
        partido.setFase(FaseTorneo.LIGA);
        partido.setEstado(EstadoPartido.FINALIZADO);
        jugados.add(partido);
        return partido;
    }

    private void tarjeta(Partido partido, Club club, TipoEventoPartido tipo) {
        EventoPartido evento = new EventoPartido();
        evento.setPartido(partido);
        evento.setClub(club);
        evento.setTipo(tipo);
        tarjetas.add(evento);
    }

    private static InscripcionEquipo aprobada(Club club) {
        InscripcionEquipo inscripcion = new InscripcionEquipo();
        inscripcion.setClub(club);
        inscripcion.setEstado(EstadoInscripcion.APROBADA);
        return inscripcion;
    }

    private static Club club(Long id, String nombre) {
        Club club = new Club();
        club.setId(id);
        club.setNombreClub(nombre);
        return club;
    }
}
