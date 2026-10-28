package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.dto.request.torneos.TorneoCategoriaRequestDto;
import edu.upb.semillero.repository.dto.request.torneos.TorneoRequestDto;
import edu.upb.semillero.repository.dto.response.torneos.TorneoResponseDto;
import edu.upb.semillero.repository.entity.torneos.Torneo;
import edu.upb.semillero.repository.enums.torneos.AmbitoTorneo;
import edu.upb.semillero.repository.enums.torneos.EstadoTorneo;
import edu.upb.semillero.repository.enums.torneos.FormatoTorneo;
import edu.upb.semillero.repository.enums.torneos.TipoTorneo;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HU 1 - Crear torneo: un caso por criterio de aceptación.
 */
@ExtendWith(MockitoExtension.class)
class TorneoServiceTest {
    private static final LocalDate INICIO = LocalDate.of(2026, 3, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 6, 30);
    private static final LocalDate LIMITE = LocalDate.of(2026, 2, 15);

    @Mock TorneoRepository repository;
    @Mock TorneoCategoriaRepository categorias;
    @Mock TorneoCategoriaService categoriaService;
    @Mock EntityRelations relations;
    @InjectMocks TorneoService service;

    @Test
    void elTorneoNaceEnBorrador() {
        guardaConId(1L);
        TorneoResponseDto response = service.create(interno());
        assertThat(response.estado()).isEqualTo(EstadoTorneo.BORRADOR);
        assertThat(response.fechaCreacion()).isNotNull();
    }

    @Test
    void noGuardaSiLaFechaDeFinEsAnteriorALaDeInicio() {
        TorneoRequestDto request = request(AmbitoTorneo.INTERNO, null, FIN, INICIO, LIMITE, List.of());
        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("fecha de fin");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void noGuardaSiElLimiteDeInscripcionEsPosteriorAlInicio() {
        TorneoRequestDto request = request(AmbitoTorneo.INTERNO, null, INICIO, FIN,
                LocalDate.of(2026, 4, 1), List.of());
        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("límite de inscripción");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void noGuardaUnTorneoExternoSinOrganizador() {
        TorneoRequestDto request = request(AmbitoTorneo.EXTERNO, "  ", INICIO, FIN, LIMITE, List.of());
        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("EXTERNO");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void elTorneoExternoGuardaSuOrganizador() {
        guardaConId(1L);
        TorneoResponseDto response = service.create(
                request(AmbitoTorneo.EXTERNO, "Liga Cruceña", INICIO, FIN, LIMITE, List.of()));
        assertThat(response.organizadorExterno()).isEqualTo("Liga Cruceña");
    }

    @Test
    void elTorneoInternoNoGuardaOrganizadorExterno() {
        guardaConId(1L);
        TorneoResponseDto response = service.create(
                request(AmbitoTorneo.INTERNO, "Liga Cruceña", INICIO, FIN, LIMITE, List.of()));
        assertThat(response.organizadorExterno()).isNull();
    }

    @Test
    void guardaLasCategoriasQueVienenAlCrearElTorneo() {
        guardaConId(5L);
        service.create(request(AmbitoTorneo.INTERNO, null, INICIO, FIN, LIMITE,
                List.of(categoria("Sub-12"), categoria("Sub-14"))));
        verify(categoriaService, times(2)).create(eq(5L), any(TorneoCategoriaRequestDto.class));
    }

    @Test
    void abreInscripcionesDesdeBorrador() {
        Torneo torneo = enEstado(EstadoTorneo.BORRADOR);
        when(repository.findById(1L)).thenReturn(Optional.of(torneo));
        when(categorias.existsByTorneoId(1L)).thenReturn(true);
        when(repository.saveAndFlush(torneo)).thenReturn(torneo);
        assertThat(service.abrirInscripciones(1L).estado()).isEqualTo(EstadoTorneo.INSCRIPCIONES_ABIERTAS);
    }

    @Test
    void noAbreInscripcionesDeUnTorneoQueNoEstaEnBorrador() {
        when(repository.findById(1L)).thenReturn(Optional.of(enEstado(EstadoTorneo.INSCRIPCIONES_ABIERTAS)));
        assertThatThrownBy(() -> service.abrirInscripciones(1L)).isInstanceOf(ConflictException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void noAbreInscripcionesSinNingunaCategoria() {
        when(repository.findById(1L)).thenReturn(Optional.of(enEstado(EstadoTorneo.BORRADOR)));
        when(categorias.existsByTorneoId(1L)).thenReturn(false);
        assertThatThrownBy(() -> service.abrirInscripciones(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("al menos una categoría");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void elTorneoQueNoExisteNoSeEncuentra() {
        when(repository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(9L)).isInstanceOf(NotFoundException.class);
    }

    private TorneoRequestDto interno() {
        return request(AmbitoTorneo.INTERNO, null, INICIO, FIN, LIMITE, List.of());
    }

    private TorneoRequestDto request(AmbitoTorneo ambito, String organizadorExterno, LocalDate inicio,
            LocalDate fin, LocalDate limite, List<TorneoCategoriaRequestDto> categorias) {
        return new TorneoRequestDto("Copa Semillero", (short) 2026, TipoTorneo.OFICIAL, ambito,
                organizadorExterno, FormatoTorneo.LIGA, false, inicio, fin, limite, 7L, categorias);
    }

    private TorneoCategoriaRequestDto categoria(String nombre) {
        return new TorneoCategoriaRequestDto(nombre, (short) 10, (short) 12, (short) 8,
                (short) 35, (short) 11, (short) 5);
    }

    private void guardaConId(long id) {
        when(repository.saveAndFlush(any(Torneo.class))).thenAnswer(invocation -> {
            Torneo torneo = invocation.getArgument(0);
            torneo.setId(id);
            return torneo;
        });
    }

    private Torneo enEstado(EstadoTorneo estado) {
        Torneo torneo = new Torneo();
        torneo.setId(1L);
        torneo.setNombre("Copa Semillero");
        torneo.setAmbito(AmbitoTorneo.INTERNO);
        torneo.setFechaInicio(INICIO);
        torneo.setFechaFin(FIN);
        torneo.setFechaLimiteInscripcion(LIMITE);
        torneo.setEstado(estado);
        return torneo;
    }
}
