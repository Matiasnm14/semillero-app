package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.dto.request.torneos.ReglamentoRequestDto;
import edu.upb.semillero.repository.entity.torneos.CriterioDesempate;
import edu.upb.semillero.repository.entity.torneos.Reglamento;
import edu.upb.semillero.repository.entity.torneos.Torneo;
import edu.upb.semillero.repository.repos.torneos.CriterioDesempateRepository;
import edu.upb.semillero.repository.repos.torneos.ReglamentoDesempateRepository;
import edu.upb.semillero.repository.repos.torneos.ReglamentoRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HU 2 - Reglamento: tareas S1-12 y S1-13.
 */
@ExtendWith(MockitoExtension.class)
class ReglamentoServiceTest {
    @Mock ReglamentoRepository repository;
    @Mock ReglamentoDesempateRepository desempates;
    @Mock CriterioDesempateRepository criterios;
    @Mock TorneoRepository torneos;
    @Mock EntityRelations relations;
    @InjectMocks ReglamentoService service;

    @Test
    void rechazaElMinimoMayorQueElMaximo() {
        when(torneos.findById(1L)).thenReturn(Optional.of(torneo()));
        when(repository.findByTorneoId(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(1L, request((short) 20, (short) 11, null, List.of("DIF_GOLES"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("mínimo de jugadores");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void laFechaDeCortePorDefectoEsFinDeLaTemporada() {
        when(torneos.findById(1L)).thenReturn(Optional.of(torneo()));
        when(repository.findByTorneoId(1L)).thenReturn(Optional.empty());
        guardaConId(5L);
        when(criterios.findByCodigo("DIF_GOLES")).thenReturn(Optional.of(criterio()));
        when(desempates.findByReglamentoIdOrderByOrdenAsc(5L)).thenReturn(List.of());

        var response = service.create(1L, request((short) 11, (short) 18, null, List.of("DIF_GOLES")));
        assertThat(response.fechaCorte()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(response.puntosVictoria()).isEqualTo((short) 3);
        assertThat(response.descansoMinimoMin()).isEqualTo((short) 60);
    }

    @Test
    void noPermiteUnSegundoReglamentoEnElMismoTorneo() {
        when(torneos.findById(1L)).thenReturn(Optional.of(torneo()));
        when(repository.findByTorneoId(1L)).thenReturn(Optional.of(new Reglamento()));
        assertThatThrownBy(() -> service.create(1L, request((short) 11, (short) 18, null, List.of("DIF_GOLES"))))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ya tiene un reglamento");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rechazaUnCriterioDeDesempateRepetido() {
        when(torneos.findById(1L)).thenReturn(Optional.of(torneo()));
        when(repository.findByTorneoId(1L)).thenReturn(Optional.empty());
        guardaConId(5L);
        when(criterios.findByCodigo("DIF_GOLES")).thenReturn(Optional.of(criterio()));

        assertThatThrownBy(() -> service.create(1L,
                request((short) 11, (short) 18, null, List.of("DIF_GOLES", "DIF_GOLES"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("repetido");
        verify(desempates, never()).save(any());
    }

    @Test
    void rechazaUnCriterioDeDesempateInexistente() {
        when(torneos.findById(1L)).thenReturn(Optional.of(torneo()));
        when(repository.findByTorneoId(1L)).thenReturn(Optional.empty());
        guardaConId(5L);
        when(criterios.findByCodigo("NO_EXISTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(1L,
                request((short) 11, (short) 18, null, List.of("NO_EXISTE"))))
                .isInstanceOf(NotFoundException.class);
        verify(desempates, never()).save(any());
    }

    @Test
    void elTorneoSinReglamentoEs404() {
        when(torneos.findById(1L)).thenReturn(Optional.of(torneo()));
        when(repository.findByTorneoId(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findByTorneo(1L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("no tiene reglamento");
    }

    @Test
    void elTorneoInexistenteEs404() {
        when(torneos.findById(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findByTorneo(7L)).isInstanceOf(NotFoundException.class);
    }

    private void guardaConId(long id) {
        when(repository.saveAndFlush(any(Reglamento.class))).thenAnswer(invocation -> {
            Reglamento reglamento = invocation.getArgument(0);
            reglamento.setId(id);
            return reglamento;
        });
    }

    private Torneo torneo() {
        Torneo torneo = new Torneo();
        torneo.setId(1L);
        torneo.setNombre("Copa Semillero");
        torneo.setTemporada((short) 2026);
        return torneo;
    }

    private CriterioDesempate criterio() {
        CriterioDesempate criterio = new CriterioDesempate();
        criterio.setId(10L);
        criterio.setCodigo("DIF_GOLES");
        criterio.setNombre("Diferencia de goles");
        return criterio;
    }

    private ReglamentoRequestDto request(Short min, Short max, LocalDate fechaCorte, List<String> codigos) {
        return new ReglamentoRequestDto(null, null, null, null, null, null, null,
                fechaCorte, min, max, null, codigos);
    }
}
