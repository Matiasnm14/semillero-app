package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.dto.request.torneos.GenerarFixtureRequestDto;
import edu.upb.semillero.repository.entity.torneos.InscripcionEquipo;
import edu.upb.semillero.repository.entity.torneos.Torneo;
import edu.upb.semillero.repository.entity.torneos.TorneoCategoria;
import edu.upb.semillero.repository.enums.torneos.AmbitoTorneo;
import edu.upb.semillero.repository.enums.torneos.EstadoInscripcion;
import edu.upb.semillero.repository.enums.torneos.EstadoTorneo;
import edu.upb.semillero.repository.enums.torneos.FormatoTorneo;
import edu.upb.semillero.repository.repos.AuditoriaRepository;
import edu.upb.semillero.repository.repos.torneos.CanchaRepository;
import edu.upb.semillero.repository.repos.torneos.InscripcionEquipoRepository;
import edu.upb.semillero.repository.repos.torneos.PartidoFixtureRepository;
import edu.upb.semillero.repository.repos.torneos.ReglamentoRepository;
import edu.upb.semillero.repository.repos.torneos.TorneoCategoriaRepository;
import edu.upb.semillero.repository.repos.torneos.TorneoRepository;
import edu.upb.semillero.service.EntityRelations;
import edu.upb.semillero.service.exception.BusinessException;
import edu.upb.semillero.service.exception.ConflictException;
import edu.upb.semillero.service.exception.NotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HU 7 - Lo que el fixture no debe dejar pasar: tareas S2-14, S2-15 y S2-18.
 * El camino feliz, con la rotación y los horarios de verdad, está en
 * {@code FixtureApiTest}.
 */
@ExtendWith(MockitoExtension.class)
class FixtureServiceTest {
    private static final Long TORNEO = 1L;
    private static final Long CATEGORIA = 2L;

    @Mock PartidoFixtureRepository partidos;
    @Mock TorneoRepository torneos;
    @Mock TorneoCategoriaRepository categorias;
    @Mock InscripcionEquipoRepository inscripciones;
    @Mock ReglamentoRepository reglamentos;
    @Mock CanchaRepository canchas;
    @Mock AuditoriaRepository auditoria;
    @Mock EntityRelations relations;
    @InjectMocks FixtureService service;

    @Test
    void enUnCampeonatoExternoNoSeGeneraFixture() {
        prepararCategoria(AmbitoTorneo.EXTERNO, FormatoTorneo.LIGA);

        assertThatThrownBy(() -> service.generarLiga(TORNEO, CATEGORIA, request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("campeonato externo");
        verify(partidos, never()).save(any());
    }

    @Test
    void noGeneraDosVecesElFixtureDeLaMismaCategoria() {
        prepararCategoria(AmbitoTorneo.INTERNO, FormatoTorneo.LIGA);
        when(partidos.countByTorneoCategoriaId(CATEGORIA)).thenReturn(6L);

        assertThatThrownBy(() -> service.generarLiga(TORNEO, CATEGORIA, request()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ya tiene fixture");
        verify(partidos, never()).save(any());
    }

    @Test
    void noGeneraConMenosDeDosEquiposAprobados() {
        prepararCategoria(AmbitoTorneo.INTERNO, FormatoTorneo.LIGA);
        when(partidos.countByTorneoCategoriaId(CATEGORIA)).thenReturn(0L);
        when(inscripciones.findByTorneoCategoriaIdAndEstadoOrderByIdAsc(CATEGORIA,
                EstadoInscripcion.APROBADA)).thenReturn(List.of(new InscripcionEquipo()));

        assertThatThrownBy(() -> service.generarLiga(TORNEO, CATEGORIA, request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("al menos 2");
        verify(partidos, never()).save(any());
    }

    @Test
    void laLigaYLaEliminacionNoSeMezclan() {
        prepararCategoria(AmbitoTorneo.INTERNO, FormatoTorneo.ELIMINACION_DIRECTA);

        assertThatThrownBy(() -> service.generarLiga(TORNEO, CATEGORIA, request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("/fixture/eliminacion");
    }

    @Test
    void laEliminacionNoSeUsaEnUnTorneoDeLiga() {
        prepararCategoria(AmbitoTorneo.INTERNO, FormatoTorneo.LIGA);
        when(partidos.countByTorneoCategoriaId(CATEGORIA)).thenReturn(0L);

        assertThatThrownBy(() -> service.generarEliminacion(TORNEO, CATEGORIA, request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("/fixture/liga");
    }

    @Test
    void noPublicaUnTorneoSinPartidos() {
        Torneo torneo = torneo(AmbitoTorneo.INTERNO, FormatoTorneo.LIGA);
        when(torneos.findById(TORNEO)).thenReturn(Optional.of(torneo));
        when(categorias.findByTorneoIdOrderByIdAsc(TORNEO)).thenReturn(List.of(categoria(torneo)));
        when(partidos.countByTorneoCategoriaId(CATEGORIA)).thenReturn(0L);

        assertThatThrownBy(() -> service.publicar(TORNEO, 4L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no hay fixture para publicar");
        verify(torneos, never()).saveAndFlush(any());
    }

    @Test
    void noPublicaDosVecesElMismoFixture() {
        Torneo torneo = torneo(AmbitoTorneo.INTERNO, FormatoTorneo.LIGA);
        torneo.setEstado(EstadoTorneo.EN_CURSO);
        when(torneos.findById(TORNEO)).thenReturn(Optional.of(torneo));

        assertThatThrownBy(() -> service.publicar(TORNEO, 4L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ya está publicado");
    }

    @Test
    void laCategoriaDeOtroTorneoEs404() {
        when(torneos.findById(TORNEO)).thenReturn(Optional.of(
                torneo(AmbitoTorneo.INTERNO, FormatoTorneo.LIGA)));
        when(categorias.findByIdAndTorneoId(CATEGORIA, TORNEO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generarLiga(TORNEO, CATEGORIA, request()))
                .isInstanceOf(NotFoundException.class);
    }

    private void prepararCategoria(AmbitoTorneo ambito, FormatoTorneo formato) {
        Torneo torneo = torneo(ambito, formato);
        when(torneos.findById(TORNEO)).thenReturn(Optional.of(torneo));
        when(categorias.findByIdAndTorneoId(CATEGORIA, TORNEO))
                .thenReturn(Optional.of(categoria(torneo)));
    }

    private Torneo torneo(AmbitoTorneo ambito, FormatoTorneo formato) {
        Torneo torneo = new Torneo();
        torneo.setId(TORNEO);
        torneo.setNombre("Copa Semillero");
        torneo.setAmbito(ambito);
        torneo.setFormato(formato);
        torneo.setEstado(EstadoTorneo.INSCRIPCIONES_ABIERTAS);
        torneo.setIdaVuelta(false);
        return torneo;
    }

    private TorneoCategoria categoria(Torneo torneo) {
        TorneoCategoria categoria = new TorneoCategoria();
        categoria.setId(CATEGORIA);
        categoria.setNombre("Sub-14");
        categoria.setDuracionTiempoMin((short) 35);
        categoria.setTorneo(torneo);
        return categoria;
    }

    private GenerarFixtureRequestDto request() {
        return new GenerarFixtureRequestDto(LocalDate.now().plusDays(10), null, null, null, null,
                null, List.of(9L), null, null, null, 4L);
    }
}
