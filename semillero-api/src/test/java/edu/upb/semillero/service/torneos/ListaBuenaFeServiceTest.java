package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.dto.request.torneos.ListaBuenaFeRequestDto;
import edu.upb.semillero.repository.entity.BaseEntity;
import edu.upb.semillero.repository.entity.Club;
import edu.upb.semillero.repository.entity.Jugador;
import edu.upb.semillero.repository.entity.torneos.InscripcionEquipo;
import edu.upb.semillero.repository.entity.torneos.ListaBuenaFe;
import edu.upb.semillero.repository.entity.torneos.Reglamento;
import edu.upb.semillero.repository.entity.torneos.Sancion;
import edu.upb.semillero.repository.entity.torneos.Torneo;
import edu.upb.semillero.repository.entity.torneos.TorneoCategoria;
import edu.upb.semillero.repository.enums.EstadoJugador;
import edu.upb.semillero.repository.enums.torneos.EstadoInscripcion;
import edu.upb.semillero.repository.enums.torneos.EstadoSancion;
import edu.upb.semillero.repository.repos.torneos.InscripcionEquipoRepository;
import edu.upb.semillero.repository.repos.torneos.ListaBuenaFeRepository;
import edu.upb.semillero.repository.repos.torneos.ReglamentoRepository;
import edu.upb.semillero.repository.repos.torneos.SancionRepository;
import edu.upb.semillero.service.EntityRelations;
import edu.upb.semillero.service.exception.BusinessException;
import edu.upb.semillero.service.exception.ConflictException;
import edu.upb.semillero.service.exception.NotFoundException;
import edu.upb.semillero.service.fichatecnica.FichaTecnicaService;
import edu.upb.semillero.service.fichatecnica.JugadorParaTorneosDto;
import edu.upb.semillero.service.seguridad.AccesoTorneos;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
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
 * HU 5 - Lista de buena fe: tareas S2-06 (alta), S2-07 (elegibilidad) y S2-08
 * (mínimo y máximo).
 *
 * <p>Cada validación de la regla 2 del contexto tiene su caso, y todos
 * comprueban el motivo que se devuelve: la HU pide el motivo exacto, no un
 * error genérico.
 */
@ExtendWith(MockitoExtension.class)
class ListaBuenaFeServiceTest {
    private static final Long INSCRIPCION = 7L;
    private static final Long TORNEO = 1L;
    private static final Long CLUB = 3L;
    private static final Long JUGADOR = 55L;
    private static final LocalDate CORTE = LocalDate.of(2026, 12, 31);

    @Mock ListaBuenaFeRepository repository;
    @Mock InscripcionEquipoRepository inscripciones;
    @Mock ReglamentoRepository reglamentos;
    @Mock SancionRepository sanciones;
    @Mock FichaTecnicaService fichaTecnica;
    @Mock AccesoTorneos acceso;
    @Mock EntityRelations relations;
    @InjectMocks ListaBuenaFeService service;

    private InscripcionEquipo inscripcion;

    @BeforeEach
    void inscripcionAprobada() {
        inscripcion = inscripcion(EstadoInscripcion.APROBADA, false);
    }

    @Test
    void noCargaLaListaSiElTorneoNoTieneReglamento() {
        when(inscripciones.findById(INSCRIPCION)).thenReturn(Optional.of(inscripcion));
        when(reglamentos.findByTorneoId(TORNEO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(INSCRIPCION, request(10)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("todavía no tiene reglamento");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void noRepiteElDorsalDentroDelEquipo() {
        prepararAlta();
        ListaBuenaFe otro = new ListaBuenaFe();
        otro.setId(90L);
        otro.setJugador(jugador(77L));
        when(repository.findByInscripcionEquipoIdAndDorsal(INSCRIPCION, (short) 10))
                .thenReturn(Optional.of(otro));

        assertThatThrownBy(() -> service.create(INSCRIPCION, request(10)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("El dorsal 10 ya lo tiene el jugador 77");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void unJugadorEstaEnUnSoloEquipoPorTorneo() {
        prepararAlta();
        InscripcionEquipo otroEquipo = new InscripcionEquipo();
        otroEquipo.setId(8L);
        ListaBuenaFe enOtroEquipo = new ListaBuenaFe();
        enOtroEquipo.setId(91L);
        enOtroEquipo.setInscripcionEquipo(otroEquipo);
        when(repository.findByTorneoIdAndJugadorId(TORNEO, JUGADOR))
                .thenReturn(Optional.of(enOtroEquipo));

        assertThatThrownBy(() -> service.create(INSCRIPCION, request(10)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ya está en la lista de buena fe del equipo 8");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void noRegistraUnJugadorFueraDelRangoDeEdadALaFechaDeCorte() {
        prepararAlta();
        when(fichaTecnica.datosParaTorneos(JUGADOR, CORTE))
                .thenReturn(Optional.of(datos(16, EstadoJugador.ACTIVO, true, Set.of(CLUB))));

        assertThatThrownBy(() -> service.create(INSCRIPCION, request(10)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("tiene 16 años al 2026-12-31")
                .hasMessageContaining("Sub-14 admite de 12 a 14 años");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void noRegistraUnJugadorSinFechaDeNacimiento() {
        prepararAlta();
        when(fichaTecnica.datosParaTorneos(JUGADOR, CORTE))
                .thenReturn(Optional.of(datos(0, EstadoJugador.ACTIVO, true, Set.of(CLUB))));

        assertThatThrownBy(() -> service.create(INSCRIPCION, request(10)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no tiene fecha de nacimiento cargada");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void soloRegistraJugadoresActivos() {
        prepararAlta();
        when(fichaTecnica.datosParaTorneos(JUGADOR, CORTE))
                .thenReturn(Optional.of(datos(13, EstadoJugador.BAJA, true, Set.of(CLUB))));

        assertThatThrownBy(() -> service.create(INSCRIPCION, request(10)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("está BAJA")
                .hasMessageContaining("ACTIVO");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void exigeDocumentosYAptoMedicoVigentes() {
        prepararAlta();
        when(fichaTecnica.datosParaTorneos(JUGADOR, CORTE))
                .thenReturn(Optional.of(datos(13, EstadoJugador.ACTIVO, false, Set.of(CLUB))));

        assertThatThrownBy(() -> service.create(INSCRIPCION, request(10)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("documentos vigentes");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void exigeQueElJugadorEsteFichadoEnElClub() {
        prepararAlta();
        when(fichaTecnica.datosParaTorneos(JUGADOR, CORTE))
                .thenReturn(Optional.of(datos(13, EstadoJugador.ACTIVO, true, Set.of(99L))));

        assertThatThrownBy(() -> service.create(INSCRIPCION, request(10)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no está fichado en el club 3");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void elJugadorQueNoExisteEnFichaTecnicaEs404() {
        prepararAlta();
        when(fichaTecnica.datosParaTorneos(JUGADOR, CORTE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(INSCRIPCION, request(10)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("no existe en ficha técnica");
    }

    @Test
    void rechazaAlJugadorConSancionVigenteEnEsteTorneo() {
        prepararAlta();
        elegible();
        when(sanciones.findByListaBuenaFeJugadorIdAndEstado(JUGADOR, EstadoSancion.VIGENTE))
                .thenReturn(List.of(sancion(TORNEO, 2, 0)));

        assertThatThrownBy(() -> service.create(INSCRIPCION, request(10)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("sanción vigente en este torneo")
                .hasMessageContaining("2 fecha(s)");
        verify(repository, never()).saveAndFlush(any());
    }

    /** Sanción de otro torneo: entra a la lista, pero inhabilitado y con el motivo. */
    @Test
    void registraInhabilitadoAlJugadorConSancionVigenteDeOtroTorneo() {
        prepararAlta();
        elegible();
        when(sanciones.findByListaBuenaFeJugadorIdAndEstado(JUGADOR, EstadoSancion.VIGENTE))
                .thenReturn(List.of(sancion(42L, 3, 1)));
        when(repository.saveAndFlush(any(ListaBuenaFe.class))).thenAnswer(i -> i.getArgument(0));

        var response = service.create(INSCRIPCION, request(10));

        assertThat(response.habilitado()).isFalse();
        assertThat(response.motivoInhabilitacion())
                .contains("Copa de otro torneo").contains("2 fecha(s)");
    }

    @Test
    void registraAlJugadorElegibleConSuDorsalYSuTorneo() {
        prepararAlta();
        elegible();
        mapeaIds();
        when(repository.saveAndFlush(any(ListaBuenaFe.class))).thenAnswer(i -> i.getArgument(0));

        var response = service.create(INSCRIPCION, request(10));

        assertThat(response.jugadorId()).isEqualTo(JUGADOR);
        assertThat(response.dorsal()).isEqualTo((short) 10);
        assertThat(response.torneoId()).isEqualTo(TORNEO);
        assertThat(response.inscripcionEquipoId()).isEqualTo(INSCRIPCION);
        assertThat(response.habilitado()).isTrue();
        assertThat(response.motivoInhabilitacion()).isNull();
        assertThat(response.fechaAlta()).isNotNull();
    }

    @Test
    void noPasaDelMaximoDeJugadoresDelReglamento() {
        when(inscripciones.findById(INSCRIPCION)).thenReturn(Optional.of(inscripcion));
        when(reglamentos.findByTorneoId(TORNEO)).thenReturn(Optional.of(reglamento(7, 18)));
        when(repository.countByInscripcionEquipoId(INSCRIPCION)).thenReturn(18L);

        assertThatThrownBy(() -> service.create(INSCRIPCION, request(10)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("permite hasta 18");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void noConfirmaConMenosJugadoresQueElMinimo() {
        when(inscripciones.findById(INSCRIPCION)).thenReturn(Optional.of(inscripcion));
        when(reglamentos.findByTorneoId(TORNEO)).thenReturn(Optional.of(reglamento(7, 18)));
        when(repository.countByInscripcionEquipoId(INSCRIPCION)).thenReturn(5L);

        assertThatThrownBy(() -> service.confirmar(INSCRIPCION))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("tiene 5 jugadores")
                .hasMessageContaining("al menos 7");
        verify(inscripciones, never()).saveAndFlush(any());
    }

    @Test
    void confirmaLaListaYLaDejaMarcadaEnLaInscripcion() {
        when(inscripciones.findById(INSCRIPCION)).thenReturn(Optional.of(inscripcion));
        when(reglamentos.findByTorneoId(TORNEO)).thenReturn(Optional.of(reglamento(7, 18)));
        when(repository.countByInscripcionEquipoId(INSCRIPCION)).thenReturn(11L);

        var response = service.confirmar(INSCRIPCION);

        assertThat(response.listaConfirmada()).isTrue();
        assertThat(response.totalJugadores()).isEqualTo(11L);
        assertThat(response.minJugadores()).isEqualTo((short) 7);
        assertThat(response.maxJugadores()).isEqualTo((short) 18);
        assertThat(inscripcion.getListaConfirmada()).isTrue();
        verify(inscripciones).saveAndFlush(inscripcion);
    }

    @Test
    void laListaConfirmadaYaNoSeModifica() {
        InscripcionEquipo confirmada = inscripcion(EstadoInscripcion.APROBADA, true);
        when(inscripciones.findById(INSCRIPCION)).thenReturn(Optional.of(confirmada));

        assertThatThrownBy(() -> service.create(INSCRIPCION, request(10)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ya está confirmada");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void noCargaLaListaDeUnaInscripcionRechazada() {
        InscripcionEquipo rechazada = inscripcion(EstadoInscripcion.RECHAZADA, false);
        when(inscripciones.findById(INSCRIPCION)).thenReturn(Optional.of(rechazada));

        assertThatThrownBy(() -> service.create(INSCRIPCION, request(10)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("RECHAZADA");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void laInscripcionQueNoExisteEs404() {
        when(inscripciones.findById(INSCRIPCION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(INSCRIPCION, request(10)))
                .isInstanceOf(NotFoundException.class);
    }

    /** Lo común a un alta: inscripción, reglamento, cupo libre y el jugador resuelto. */
    private void prepararAlta() {
        when(inscripciones.findById(INSCRIPCION)).thenReturn(Optional.of(inscripcion));
        when(reglamentos.findByTorneoId(TORNEO)).thenReturn(Optional.of(reglamento(7, 18)));
        when(repository.countByInscripcionEquipoId(INSCRIPCION)).thenReturn(4L);
        when(relations.find(Jugador.class, JUGADOR)).thenReturn(jugador(JUGADOR));
    }

    /** {@code EntityRelations} está mockeado: sin esto la respuesta sale con los IDs en null. */
    private void mapeaIds() {
        when(relations.id(any())).thenAnswer(i -> {
            BaseEntity entidad = i.getArgument(0);
            return entidad == null ? null : entidad.getId();
        });
    }

    /** El jugador pasa todas las validaciones de ficha técnica. */
    private void elegible() {
        when(fichaTecnica.datosParaTorneos(JUGADOR, CORTE))
                .thenReturn(Optional.of(datos(13, EstadoJugador.ACTIVO, true, Set.of(CLUB))));
    }

    private ListaBuenaFeRequestDto request(int dorsal) {
        return new ListaBuenaFeRequestDto(JUGADOR, null, (short) dorsal);
    }

    private JugadorParaTorneosDto datos(int edad, EstadoJugador estado, boolean documentos,
            Set<Long> clubes) {
        return new JugadorParaTorneosDto(JUGADOR, edad, estado, documentos, documentos, clubes);
    }

    private InscripcionEquipo inscripcion(EstadoInscripcion estado, boolean listaConfirmada) {
        InscripcionEquipo entity = new InscripcionEquipo();
        entity.setId(INSCRIPCION);
        entity.setEstado(estado);
        entity.setListaConfirmada(listaConfirmada);
        entity.setTorneoCategoria(categoria(torneo(TORNEO, "Copa Semillero")));
        Club club = new Club();
        club.setId(CLUB);
        entity.setClub(club);
        return entity;
    }

    private TorneoCategoria categoria(Torneo torneo) {
        TorneoCategoria categoria = new TorneoCategoria();
        categoria.setId(2L);
        categoria.setNombre("Sub-14");
        categoria.setEdadMinima((short) 12);
        categoria.setEdadMaxima((short) 14);
        categoria.setTorneo(torneo);
        return categoria;
    }

    private Torneo torneo(Long id, String nombre) {
        Torneo torneo = new Torneo();
        torneo.setId(id);
        torneo.setNombre(nombre);
        return torneo;
    }

    private Reglamento reglamento(int minimo, int maximo) {
        Reglamento reglamento = new Reglamento();
        reglamento.setFechaCorte(CORTE);
        reglamento.setMinJugadores((short) minimo);
        reglamento.setMaxJugadores((short) maximo);
        return reglamento;
    }

    private Jugador jugador(Long id) {
        Jugador jugador = new Jugador();
        jugador.setId(id);
        return jugador;
    }

    private Sancion sancion(Long torneoId, int suspension, int cumplidas) {
        ListaBuenaFe enEseTorneo = new ListaBuenaFe();
        enEseTorneo.setTorneo(torneo(torneoId, "Copa de otro torneo"));
        Sancion sancion = new Sancion();
        sancion.setListaBuenaFe(enEseTorneo);
        sancion.setEstado(EstadoSancion.VIGENTE);
        sancion.setFechasSuspension((short) suspension);
        sancion.setFechasCumplidas((short) cumplidas);
        return sancion;
    }
}
