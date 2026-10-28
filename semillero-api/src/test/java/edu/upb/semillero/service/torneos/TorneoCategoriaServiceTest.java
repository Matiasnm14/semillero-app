package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.dto.request.torneos.TorneoCategoriaRequestDto;
import edu.upb.semillero.repository.entity.torneos.Torneo;
import edu.upb.semillero.repository.entity.torneos.TorneoCategoria;
import edu.upb.semillero.repository.repos.torneos.TorneoCategoriaRepository;
import edu.upb.semillero.repository.repos.torneos.TorneoRepository;
import edu.upb.semillero.service.EntityRelations;
import edu.upb.semillero.service.exception.BusinessException;
import edu.upb.semillero.service.exception.ConflictException;
import edu.upb.semillero.service.exception.NotFoundException;
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
 * Tarea S1-09: categorías del torneo con rango de edad.
 */
@ExtendWith(MockitoExtension.class)
class TorneoCategoriaServiceTest {
    @Mock TorneoCategoriaRepository repository;
    @Mock TorneoRepository torneos;
    @Mock EntityRelations relations;
    @InjectMocks TorneoCategoriaService service;

    @Test
    void creaLaCategoriaDelTorneo() {
        when(torneos.findById(1L)).thenReturn(Optional.of(torneo()));
        when(repository.findByTorneoIdAndNombre(1L, "Sub-12")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(TorneoCategoria.class))).thenAnswer(i -> i.getArgument(0));
        assertThat(service.create(1L, categoria("Sub-12", (short) 10, (short) 12, (short) 8)).nombre())
                .isEqualTo("Sub-12");
    }

    @Test
    void rechazaLaEdadMinimaMayorQueLaMaxima() {
        when(torneos.findById(1L)).thenReturn(Optional.of(torneo()));
        TorneoCategoriaRequestDto request = categoria("Sub-12", (short) 14, (short) 12, (short) 8);
        assertThatThrownBy(() -> service.create(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("edad mínima");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rechazaUnCupoMenorADos() {
        when(torneos.findById(1L)).thenReturn(Optional.of(torneo()));
        TorneoCategoriaRequestDto request = categoria("Sub-12", (short) 10, (short) 12, (short) 1);
        assertThatThrownBy(() -> service.create(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cupo máximo");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rechazaDosCategoriasConElMismoNombreEnElTorneo() {
        TorneoCategoria existente = new TorneoCategoria();
        existente.setId(9L);
        when(torneos.findById(1L)).thenReturn(Optional.of(torneo()));
        when(repository.findByTorneoIdAndNombre(1L, "Sub-12")).thenReturn(Optional.of(existente));
        TorneoCategoriaRequestDto request = categoria("Sub-12", (short) 10, (short) 12, (short) 8);
        assertThatThrownBy(() -> service.create(1L, request)).isInstanceOf(ConflictException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void noEncuentraLaCategoriaDeOtroTorneo() {
        when(repository.findByIdAndTorneoId(5L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(1L, 5L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void noAgregaCategoriasAUnTorneoQueNoExiste() {
        when(torneos.findById(9L)).thenReturn(Optional.empty());
        TorneoCategoriaRequestDto request = categoria("Sub-12", (short) 10, (short) 12, (short) 8);
        assertThatThrownBy(() -> service.create(9L, request)).isInstanceOf(NotFoundException.class);
    }

    private Torneo torneo() {
        Torneo torneo = new Torneo();
        torneo.setId(1L);
        return torneo;
    }

    private TorneoCategoriaRequestDto categoria(String nombre, Short edadMinima, Short edadMaxima, Short cupo) {
        return new TorneoCategoriaRequestDto(nombre, edadMinima, edadMaxima, cupo,
                (short) 35, (short) 11, (short) 5);
    }
}
