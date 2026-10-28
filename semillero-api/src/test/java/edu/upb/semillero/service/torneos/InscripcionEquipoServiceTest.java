package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.dto.request.torneos.CambioEstadoInscripcionRequestDto;
import edu.upb.semillero.repository.dto.request.torneos.InscripcionEquipoRequestDto;
import edu.upb.semillero.repository.entity.Auditoria;
import edu.upb.semillero.repository.entity.Club;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.entity.torneos.Grupo;
import edu.upb.semillero.repository.entity.torneos.InscripcionEquipo;
import edu.upb.semillero.repository.entity.torneos.Torneo;
import edu.upb.semillero.repository.entity.torneos.TorneoCategoria;
import edu.upb.semillero.repository.enums.torneos.EstadoInscripcion;
import edu.upb.semillero.repository.enums.torneos.EstadoTorneo;
import edu.upb.semillero.repository.repos.AuditoriaRepository;
import edu.upb.semillero.repository.repos.torneos.InscripcionEquipoRepository;
import edu.upb.semillero.repository.repos.torneos.PartidoTorneoRepository;
import edu.upb.semillero.repository.repos.torneos.TorneoCategoriaRepository;
import edu.upb.semillero.service.EntityRelations;
import edu.upb.semillero.service.exception.BusinessException;
import edu.upb.semillero.service.exception.ConflictException;
import edu.upb.semillero.service.exception.NotFoundException;
import java.time.LocalDate;
import java.util.List;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HU 4 - Inscribir un equipo: tareas S1-20 y S1-21.
 */
@ExtendWith(MockitoExtension.class)
class InscripcionEquipoServiceTest {
    @Mock InscripcionEquipoRepository repository;
    @Mock TorneoCategoriaRepository categorias;
    @Mock AuditoriaRepository auditoria;
    @Mock PartidoTorneoRepository partidos;
    @Mock EntityRelations relations;
    @InjectMocks InscripcionEquipoService service;

    @Test
    void noCambiaAlMismoEstadoEnQueYaEsta() {
        when(repository.findById(5L)).thenReturn(Optional.of(pendiente()));

        assertThatThrownBy(() -> service.cambiarEstado(5L,
                new CambioEstadoInscripcionRequestDto(EstadoInscripcion.PENDIENTE, null, 4L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ya está en estado PENDIENTE");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void observarYRechazarExigenMotivo() {
        when(repository.findById(5L)).thenReturn(Optional.of(pendiente()));

        assertThatThrownBy(() -> service.cambiarEstado(5L,
                new CambioEstadoInscripcionRequestDto(EstadoInscripcion.RECHAZADA, "  ", 4L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("hay que indicar el motivo");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rechazarGuardaElMotivoYLoDejaEnElHistorial() {
        InscripcionEquipo inscripcion = pendiente();
        when(repository.findById(5L)).thenReturn(Optional.of(inscripcion));
        when(repository.saveAndFlush(inscripcion)).thenReturn(inscripcion);

        var response = service.cambiarEstado(5L, new CambioEstadoInscripcionRequestDto(
                EstadoInscripcion.RECHAZADA, "Faltan los documentos del club", 4L));

        assertThat(response.estado()).isEqualTo(EstadoInscripcion.RECHAZADA);
        assertThat(response.motivo()).isEqualTo("Faltan los documentos del club");
        ArgumentCaptor<Auditoria> fila = ArgumentCaptor.forClass(Auditoria.class);
        verify(auditoria).saveAndFlush(fila.capture());
        assertThat(fila.getValue().getAccion()).isEqualTo("CAMBIO_ESTADO");
        assertThat(fila.getValue().getTabla()).isEqualTo("inscripciones_equipo");
        assertThat(fila.getValue().getValorAnterior()).contains("PENDIENTE");
        assertThat(fila.getValue().getValorNuevo()).contains("RECHAZADA");
        assertThat(fila.getValue().getMotivo()).isEqualTo("Faltan los documentos del club");
    }

    /** Aprobar no pide motivo y borra el que había dejado una observación. */
    @Test
    void aprobarLimpiaElMotivoDeLaObservacion() {
        InscripcionEquipo inscripcion = pendiente();
        inscripcion.setEstado(EstadoInscripcion.OBSERVADA);
        inscripcion.setMotivo("Falta la lista de buena fe");
        when(repository.findById(5L)).thenReturn(Optional.of(inscripcion));
        when(repository.saveAndFlush(inscripcion)).thenReturn(inscripcion);

        var response = service.cambiarEstado(5L,
                new CambioEstadoInscripcionRequestDto(EstadoInscripcion.APROBADA, null, 4L));

        assertThat(response.estado()).isEqualTo(EstadoInscripcion.APROBADA);
        assertThat(response.motivo()).isNull();
    }

    /** Con el fixture ya generado, sacar a un equipo aprobado dejaría partidos sin rival. */
    @Test
    void noSacaAUnEquipoAprobadoSiLaCategoriaYaTieneFixture() {
        InscripcionEquipo inscripcion = pendiente();
        inscripcion.setEstado(EstadoInscripcion.APROBADA);
        when(repository.findById(5L)).thenReturn(Optional.of(inscripcion));
        when(partidos.countByTorneoCategoriaId(2L)).thenReturn(6L);

        assertThatThrownBy(() -> service.cambiarEstado(5L, new CambioEstadoInscripcionRequestDto(
                EstadoInscripcion.RECHAZADA, "Se bajó del torneo", 4L)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ya tiene fixture generado");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void elHistorialDevuelveLosCambiosDeMasNuevoAMasViejo() {
        when(repository.findById(5L)).thenReturn(Optional.of(pendiente()));
        Auditoria fila = new Auditoria();
        fila.setId(30L);
        fila.setAccion("CAMBIO_ESTADO");
        fila.setTabla("inscripciones_equipo");
        when(auditoria.findByTablaAndRegistroIdOrderByFechaDescIdDesc("inscripciones_equipo", 5L))
                .thenReturn(List.of(fila));

        assertThat(service.findHistorial(5L)).singleElement()
                .satisfies(entrada -> assertThat(entrada.accion()).isEqualTo("CAMBIO_ESTADO"));
    }

    private InscripcionEquipo pendiente() {
        InscripcionEquipo inscripcion = new InscripcionEquipo();
        inscripcion.setId(5L);
        inscripcion.setEstado(EstadoInscripcion.PENDIENTE);
        inscripcion.setListaConfirmada(false);
        inscripcion.setTorneoCategoria(abierta(4));
        return inscripcion;
    }

    @Test
    void noInscribeSiElTorneoNoTieneLasInscripcionesAbiertas() {
        when(categorias.findById(2L)).thenReturn(Optional.of(
                categoria(EstadoTorneo.BORRADOR, LocalDate.now().plusDays(5), 4)));
        assertThatThrownBy(() -> service.create(request(null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inscripciones abiertas");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void noInscribeDespuesDeLaFechaLimite() {
        when(categorias.findById(2L)).thenReturn(Optional.of(
                categoria(EstadoTorneo.INSCRIPCIONES_ABIERTAS, LocalDate.now().minusDays(1), 4)));
        assertThatThrownBy(() -> service.create(request(null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("límite de inscripción");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void noInscribeSiLaCategoriaLlegoAlCupo() {
        when(categorias.findById(2L)).thenReturn(Optional.of(abierta(2)));
        when(repository.countByTorneoCategoriaId(2L)).thenReturn(2L);
        assertThatThrownBy(() -> service.create(request(null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cupo");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void noInscribeDosVecesAlMismoClub() {
        when(categorias.findById(2L)).thenReturn(Optional.of(abierta(4)));
        when(repository.countByTorneoCategoriaId(2L)).thenReturn(1L);
        when(relations.find(Club.class, 3L)).thenReturn(club());
        when(relations.find(Usuario.class, 4L)).thenReturn(new Usuario());
        when(repository.findByTorneoCategoriaIdAndClubId(2L, 3L))
                .thenReturn(Optional.of(inscripcionYaPersistida()));
        assertThatThrownBy(() -> service.create(request(null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ya está inscrito");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void noAceptaUnGrupoDeOtraCategoria() {
        when(categorias.findById(2L)).thenReturn(Optional.of(abierta(4)));
        when(repository.countByTorneoCategoriaId(2L)).thenReturn(0L);
        when(relations.find(Club.class, 3L)).thenReturn(club());
        when(relations.find(Usuario.class, 4L)).thenReturn(new Usuario());
        when(relations.find(Grupo.class, 9L)).thenReturn(grupoDeOtraCategoria());
        when(repository.findByTorneoCategoriaIdAndClubId(2L, 3L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(request(9L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no pertenece");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void laInscripcionNacePendienteYSinListaConfirmada() {
        when(categorias.findById(2L)).thenReturn(Optional.of(abierta(4)));
        when(repository.countByTorneoCategoriaId(2L)).thenReturn(0L);
        when(relations.find(Club.class, 3L)).thenReturn(club());
        when(repository.findByTorneoCategoriaIdAndClubId(2L, 3L)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(InscripcionEquipo.class))).thenAnswer(i -> i.getArgument(0));

        var response = service.create(request(null));
        assertThat(response.estado()).isEqualTo(EstadoInscripcion.PENDIENTE);
        assertThat(response.listaConfirmada()).isFalse();
        assertThat(response.fechaInscripcion()).isNotNull();
        assertThat(response.motivo()).isNull();
    }

    @Test
    void laCategoriaQueNoExisteEs404() {
        when(categorias.findById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(request(null))).isInstanceOf(NotFoundException.class);
    }

    private InscripcionEquipoRequestDto request(Long grupoId) {
        return new InscripcionEquipoRequestDto(2L, 3L, 4L, grupoId, null);
    }

    private TorneoCategoria abierta(int cupo) {
        return categoria(EstadoTorneo.INSCRIPCIONES_ABIERTAS, LocalDate.now().plusDays(5), cupo);
    }

    private TorneoCategoria categoria(EstadoTorneo estado, LocalDate limite, int cupo) {
        Torneo torneo = new Torneo();
        torneo.setId(1L);
        torneo.setNombre("Copa Semillero");
        torneo.setEstado(estado);
        torneo.setFechaLimiteInscripcion(limite);
        TorneoCategoria categoria = new TorneoCategoria();
        categoria.setId(2L);
        categoria.setNombre("Sub-14");
        categoria.setCupoMaximo((short) cupo);
        categoria.setTorneo(torneo);
        return categoria;
    }

    private Club club() {
        Club club = new Club();
        club.setId(3L);
        return club;
    }

    /** Una inscripción ya guardada siempre tiene id: sin él, el filtro que
     * descarta "el duplicado soy yo mismo" la dejaría pasar. */
    private InscripcionEquipo inscripcionYaPersistida() {
        InscripcionEquipo inscripcion = new InscripcionEquipo();
        inscripcion.setId(42L);
        return inscripcion;
    }

    private Grupo grupoDeOtraCategoria() {
        TorneoCategoria otra = new TorneoCategoria();
        otra.setId(99L);
        Grupo grupo = new Grupo();
        grupo.setId(9L);
        grupo.setNombre("A");
        grupo.setTorneoCategoria(otra);
        return grupo;
    }
}
