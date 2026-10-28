package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.dto.request.torneos.CanchaRequestDto;
import edu.upb.semillero.repository.entity.torneos.Cancha;
import edu.upb.semillero.repository.entity.torneos.Sede;
import edu.upb.semillero.repository.enums.torneos.TipoSuperficie;
import edu.upb.semillero.repository.enums.EstadoPartido;
import edu.upb.semillero.repository.repos.torneos.CanchaRepository;
import edu.upb.semillero.repository.repos.torneos.PartidoTorneoRepository;
import edu.upb.semillero.repository.repos.torneos.ReprogramacionRepository;
import edu.upb.semillero.service.EntityRelations;
import edu.upb.semillero.service.exception.ConflictException;
import edu.upb.semillero.service.exception.NotFoundException;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HU 3 - Canchas: tareas S1-16, S1-17 y lo que hoy se puede de la S1-18.
 */
@ExtendWith(MockitoExtension.class)
class CanchaServiceTest {
    @Mock CanchaRepository repository;
    @Mock ReprogramacionRepository reprogramaciones;
    @Mock PartidoTorneoRepository partidos;
    @Mock EntityRelations relations;
    @InjectMocks CanchaService service;

    @Test
    void noAceptaDosCanchasConElMismoNombreEnLaSede() {
        when(relations.find(Sede.class, 1L)).thenReturn(sede());
        Cancha existente = new Cancha();
        existente.setId(99L);
        when(repository.findBySedeIdAndNombre(1L, "Principal")).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> service.create(request("Principal", null, null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ya tiene una cancha");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void guardaLasDimensionesYLaDescripcion() {
        when(relations.find(Sede.class, 1L)).thenReturn(sede());
        when(repository.findBySedeIdAndNombre(1L, "Cancha 1")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(Cancha.class))).thenAnswer(i -> i.getArgument(0));

        var response = service.create(request("Cancha 1",
                new BigDecimal("100.0"), new BigDecimal("64.5"), "Con tribuna"));
        assertThat(response.largoM()).isEqualByComparingTo("100.0");
        assertThat(response.anchoM()).isEqualByComparingTo("64.5");
        assertThat(response.descripcion()).isEqualTo("Con tribuna");
        assertThat(response.tipoSuperficie()).isEqualTo(TipoSuperficie.CESPED_SINTETICO);
    }

    @Test
    void aplicaLosValoresPorDefectoDeLosBooleanos() {
        when(relations.find(Sede.class, 1L)).thenReturn(sede());
        when(repository.findBySedeIdAndNombre(1L, "Cancha 2")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(Cancha.class))).thenAnswer(i -> i.getArgument(0));

        var response = service.create(new CanchaRequestDto(1L, "Cancha 2",
                TipoSuperficie.TIERRA, null, null, null, null, null, null, null));
        assertThat(response.techada()).isFalse();
        assertThat(response.iluminacion()).isFalse();
        assertThat(response.vestuarios()).isFalse();
        assertThat(response.activa()).isTrue();
    }

    /** Tarea S1-18: un partido PROGRAMADO o EN_CURSO bloquea el borrado. */
    @Test
    void noBorraUnaCanchaConPartidosProgramados() {
        when(repository.findById(5L)).thenReturn(Optional.of(cancha()));
        when(partidos.existsByCanchaIdAndEstadoIn(eq(5L), anyCollection())).thenReturn(true);

        assertThatThrownBy(() -> service.delete(5L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("partidos programados");
        verify(repository, never()).delete(any());
    }

    @Test
    void losPartidosQueBloquearSonSoloProgramadoYEnCurso() {
        when(repository.findById(5L)).thenReturn(Optional.of(cancha()));
        when(partidos.existsByCanchaIdAndEstadoIn(eq(5L), anyCollection())).thenReturn(false);
        when(reprogramaciones.existsByCanchaAnteriorIdOrCanchaNuevaId(5L, 5L)).thenReturn(false);

        service.delete(5L);

        ArgumentCaptor<Collection<EstadoPartido>> estados = ArgumentCaptor.forClass(Collection.class);
        verify(partidos).existsByCanchaIdAndEstadoIn(eq(5L), estados.capture());
        assertThat(estados.getValue())
                .containsExactlyInAnyOrder(EstadoPartido.PROGRAMADO, EstadoPartido.EN_CURSO);
    }

    @Test
    void noBorraUnaCanchaConReprogramaciones() {
        when(repository.findById(5L)).thenReturn(Optional.of(cancha()));
        when(partidos.existsByCanchaIdAndEstadoIn(eq(5L), anyCollection())).thenReturn(false);
        when(reprogramaciones.existsByCanchaAnteriorIdOrCanchaNuevaId(5L, 5L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(5L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("reprogramaciones");
        verify(repository, never()).delete(any());
    }

    @Test
    void borraLaCanchaQueNadieUsa() {
        Cancha cancha = cancha();
        when(repository.findById(5L)).thenReturn(Optional.of(cancha));
        when(partidos.existsByCanchaIdAndEstadoIn(eq(5L), anyCollection())).thenReturn(false);
        when(reprogramaciones.existsByCanchaAnteriorIdOrCanchaNuevaId(5L, 5L)).thenReturn(false);

        service.delete(5L);
        verify(repository).delete(cancha);
    }

    @Test
    void laCanchaQueNoExisteEs404() {
        when(repository.findById(5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(5L)).isInstanceOf(NotFoundException.class);
    }

    private CanchaRequestDto request(String nombre, BigDecimal largo, BigDecimal ancho, String descripcion) {
        return new CanchaRequestDto(1L, nombre, TipoSuperficie.CESPED_SINTETICO,
                largo, ancho, false, true, true, descripcion, true);
    }

    private Sede sede() {
        Sede sede = new Sede();
        sede.setId(1L);
        sede.setNombre("Complejo Norte");
        return sede;
    }

    private Cancha cancha() {
        Cancha cancha = new Cancha();
        cancha.setId(5L);
        cancha.setNombre("Principal");
        cancha.setSede(sede());
        return cancha;
    }
}
