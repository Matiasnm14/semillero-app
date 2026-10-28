package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.dto.request.torneos.ReprogramacionRequestDto;
import edu.upb.semillero.repository.entity.Club;
import edu.upb.semillero.repository.entity.Partido;
import edu.upb.semillero.repository.entity.torneos.Cancha;
import edu.upb.semillero.repository.entity.torneos.Reglamento;
import edu.upb.semillero.repository.entity.torneos.Torneo;
import edu.upb.semillero.repository.entity.torneos.TorneoCategoria;
import edu.upb.semillero.repository.enums.EstadoPartido;
import edu.upb.semillero.repository.repos.torneos.CanchaRepository;
import edu.upb.semillero.repository.repos.torneos.PartidoFixtureRepository;
import edu.upb.semillero.repository.repos.torneos.ReglamentoRepository;
import edu.upb.semillero.repository.repos.torneos.ReprogramacionRepository;
import edu.upb.semillero.service.EntityRelations;
import edu.upb.semillero.service.exception.BusinessException;
import edu.upb.semillero.service.exception.ConflictException;
import java.time.LocalDateTime;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HU 8 - Lo que la reprogramación no deja pasar: tareas S3-16 y S3-17. El
 * camino feliz, con el historial y el aviso, está en
 * {@code ReprogramacionApiTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReprogramacionServiceTest {
    private static final Long PARTIDO = 7L;
    private static final Long CANCHA = 3L;

    @Mock PartidoFixtureRepository partidos;
    @Mock ReprogramacionRepository repository;
    @Mock CanchaRepository canchas;
    @Mock ReglamentoRepository reglamentos;
    @Mock AvisosTorneos avisos;
    @Mock EntityRelations relations;
    @InjectMocks ReprogramacionService service;

    private Partido partido;
    private Cancha cancha;
    private final LocalDateTime manana = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0)
            .withSecond(0).withNano(0);

    @BeforeEach
    void partido() {
        Torneo torneo = new Torneo();
        torneo.setId(1L);
        torneo.setNombre("Copa");
        TorneoCategoria categoria = new TorneoCategoria();
        categoria.setId(2L);
        categoria.setNombre("Sub-14");
        categoria.setTorneo(torneo);
        categoria.setDuracionTiempoMin((short) 35);
        cancha = new Cancha();
        cancha.setId(CANCHA);
        cancha.setNombre("Cancha A");
        cancha.setActiva(true);
        partido = new Partido();
        partido.setId(PARTIDO);
        partido.setTorneoCategoria(categoria);
        partido.setClubLocal(club(10L));
        partido.setClubVisitante(club(11L));
        partido.setCancha(cancha);
        partido.setEstado(EstadoPartido.PROGRAMADO);
        partido.setFechaInicio(manana.plusDays(5));
        partido.setFechaFin(manana.plusDays(5).plusMinutes(85));

        when(partidos.findById(PARTIDO)).thenReturn(Optional.of(partido));
        when(canchas.findById(CANCHA)).thenReturn(Optional.of(cancha));
        when(reglamentos.findByTorneoId(1L)).thenReturn(Optional.empty());
        when(partidos.findByFechaInicioBetween(any(), any())).thenReturn(List.of());
    }

    @Test
    void unPartidoCerradoNoSeReprograma() {
        partido.setEstado(EstadoPartido.FINALIZADO);

        assertThatThrownBy(() -> service.reprogramar(PARTIDO, request(manana)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cerrado");
        verify(repository, never()).saveAndFlush(any());
        verify(avisos, never()).avisarALosEquipos(any(), anyString(), anyString());
    }

    @Test
    void unPartidoAnuladoNoSeReprograma() {
        partido.setEstado(EstadoPartido.CANCELADO);

        assertThatThrownBy(() -> service.reprogramar(PARTIDO, request(manana)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("anulado");
    }

    @Test
    void noSeReprogramaAUnaCanchaInactiva() {
        cancha.setActiva(false);

        assertThatThrownBy(() -> service.reprogramar(PARTIDO, request(manana)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inactiva");
    }

    @Test
    void unPartidoFueraDeTorneoNoSeReprogramaDesdeTorneos() {
        partido.setTorneoCategoria(null);

        assertThatThrownBy(() -> service.reprogramar(PARTIDO, request(manana)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no es de un torneo");
    }

    /** Tarea S3-17: el descanso mínimo del reglamento también cuenta. */
    @Test
    void respetaElDescansoMinimoDelReglamento() {
        Reglamento reglamento = new Reglamento();
        reglamento.setDescansoMinimoMin((short) 60);
        when(reglamentos.findByTorneoId(1L)).thenReturn(Optional.of(reglamento));
        Partido anterior = new Partido();
        anterior.setId(8L);
        anterior.setClubLocal(club(10L));
        anterior.setClubVisitante(club(12L));
        anterior.setEstado(EstadoPartido.PROGRAMADO);
        anterior.setFechaInicio(manana.minusMinutes(115));
        anterior.setFechaFin(manana.minusMinutes(30));
        when(partidos.findByFechaInicioBetween(any(), any())).thenReturn(List.of(anterior));

        assertThatThrownBy(() -> service.reprogramar(PARTIDO, request(manana)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("partido 8")
                .hasMessageContaining("60 minutos de descanso");
        verify(repository, never()).saveAndFlush(any());
    }

    private ReprogramacionRequestDto request(LocalDateTime fecha) {
        return new ReprogramacionRequestDto(fecha, CANCHA, null, "Lluvia", 1L);
    }

    private static Club club(Long id) {
        Club club = new Club();
        club.setId(id);
        club.setNombreClub("Club " + id);
        return club;
    }
}
