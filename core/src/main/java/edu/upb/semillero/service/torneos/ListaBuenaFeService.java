package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.dto.request.torneos.ListaBuenaFeRequestDto;
import edu.upb.semillero.repository.dto.response.torneos.ListaBuenaFeConfirmacionResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.ListaBuenaFeResponseDto;
import edu.upb.semillero.repository.entity.Jugador;
import edu.upb.semillero.repository.entity.Posicion;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU 5 - Lista de buena fe (tareas S2-06, S2-07 y S2-08). El equipo es la
 * inscripción del club en una categoría; la lista cuelga de ahí.
 *
 * <p>Las validaciones son las de la regla 2 del contexto, y cada una devuelve
 * su motivo exacto:
 *
 * <ol>
 *   <li>un jugador en un solo equipo por torneo;</li>
 *   <li>dorsal único dentro del equipo;</li>
 *   <li>edad dentro del rango de la categoría, calculada <strong>a la fecha de
 *       corte del reglamento</strong> y nunca a la fecha del partido;</li>
 *   <li>documentos y apto médico vigentes;</li>
 *   <li>jugador ACTIVO;</li>
 *   <li>fichado en el club;</li>
 *   <li>sin sanción vigente.</li>
 * </ol>
 *
 * <p>La sanción vigente es el único caso que no siempre rechaza: si es de este
 * torneo, no se puede inscribir al jugador; si es de otro, entra a la lista
 * pero con {@code habilitado = false} y el motivo, y el bloqueo lo aplica la
 * planilla (tarea S4-03).
 *
 * <p>Los datos del jugador salen de {@code FichaTecnicaService}: acá solo se
 * guarda su ID, nunca datos personales.
 */
@Service
@Transactional(readOnly = true)
public class ListaBuenaFeService {

    private final ListaBuenaFeRepository repository;
    private final InscripcionEquipoRepository inscripciones;
    private final ReglamentoRepository reglamentos;
    private final SancionRepository sanciones;
    private final FichaTecnicaService fichaTecnica;
    private final AccesoTorneos acceso;
    private final EntityRelations relations;

    public ListaBuenaFeService(ListaBuenaFeRepository repository,
            InscripcionEquipoRepository inscripciones, ReglamentoRepository reglamentos,
            SancionRepository sanciones, FichaTecnicaService fichaTecnica, AccesoTorneos acceso,
            EntityRelations relations) {
        this.repository = repository;
        this.inscripciones = inscripciones;
        this.reglamentos = reglamentos;
        this.sanciones = sanciones;
        this.fichaTecnica = fichaTecnica;
        this.acceso = acceso;
        this.relations = relations;
    }

    public Page<ListaBuenaFeResponseDto> findByInscripcion(Long inscripcionEquipoId, Pageable pageable) {
        requireInscripcion(inscripcionEquipoId);
        return repository.findByInscripcionEquipoId(inscripcionEquipoId, pageable).map(this::toResponse);
    }

    public ListaBuenaFeResponseDto findById(Long inscripcionEquipoId, Long id) {
        return toResponse(require(inscripcionEquipoId, id));
    }

    @Transactional
    public ListaBuenaFeResponseDto create(Long inscripcionEquipoId, ListaBuenaFeRequestDto request) {
        InscripcionEquipo inscripcion = requireInscripcion(inscripcionEquipoId);
        requireListaEditable(inscripcion);
        Reglamento reglamento = requireReglamento(inscripcion);
        validarMaximoDeJugadores(inscripcion, reglamento);

        ListaBuenaFe entity = new ListaBuenaFe();
        entity.setInscripcionEquipo(inscripcion);
        entity.setTorneo(torneoDe(inscripcion));
        entity.setFechaAlta(LocalDateTime.now());
        apply(entity, request);
        validate(entity, inscripcion, reglamento);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public ListaBuenaFeResponseDto update(Long inscripcionEquipoId, Long id, ListaBuenaFeRequestDto request) {
        ListaBuenaFe entity = require(inscripcionEquipoId, id);
        InscripcionEquipo inscripcion = entity.getInscripcionEquipo();
        requireListaEditable(inscripcion);
        Reglamento reglamento = requireReglamento(inscripcion);

        apply(entity, request);
        validate(entity, inscripcion, reglamento);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public void delete(Long inscripcionEquipoId, Long id) {
        ListaBuenaFe entity = require(inscripcionEquipoId, id);
        requireListaEditable(entity.getInscripcionEquipo());
        repository.delete(entity);
        repository.flush();
    }

    /**
     * Tarea S2-08: la lista se confirma solo con la cantidad de jugadores que
     * pide el reglamento. Una vez confirmada no se agrega ni se saca a nadie.
     */
    @Transactional
    public ListaBuenaFeConfirmacionResponseDto confirmar(Long inscripcionEquipoId) {
        InscripcionEquipo inscripcion = requireInscripcion(inscripcionEquipoId);
        requireListaEditable(inscripcion);
        Reglamento reglamento = requireReglamento(inscripcion);

        long total = repository.countByInscripcionEquipoId(inscripcionEquipoId);
        if (total < reglamento.getMinJugadores()) {
            throw new BusinessException("La lista de buena fe tiene " + total
                    + " jugadores y el reglamento del torneo exige al menos "
                    + reglamento.getMinJugadores());
        }
        if (total > reglamento.getMaxJugadores()) {
            throw new BusinessException("La lista de buena fe tiene " + total
                    + " jugadores y el reglamento del torneo permite hasta "
                    + reglamento.getMaxJugadores());
        }
        inscripcion.setListaConfirmada(true);
        inscripciones.saveAndFlush(inscripcion);
        return new ListaBuenaFeConfirmacionResponseDto(inscripcion.getId(),
                torneoDe(inscripcion).getId(), total, reglamento.getMinJugadores(),
                reglamento.getMaxJugadores(), inscripcion.getListaConfirmada());
    }

    private ListaBuenaFe require(Long inscripcionEquipoId, Long id) {
        ListaBuenaFe entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "El jugador con id " + id + " no está en ninguna lista de buena fe"));
        if (!Objects.equals(entity.getInscripcionEquipo().getId(), inscripcionEquipoId)) {
            throw new NotFoundException("El jugador con id " + id
                    + " no está en la lista de buena fe del equipo " + inscripcionEquipoId);
        }
        return entity;
    }

    private InscripcionEquipo requireInscripcion(Long inscripcionEquipoId) {
        return inscripciones.findById(inscripcionEquipoId)
                .orElseThrow(() -> new NotFoundException(
                        "La inscripción con id " + inscripcionEquipoId + " no existe"));
    }

    /** La fecha de corte y el mínimo y máximo de jugadores salen del reglamento (HU 2). */
    private Reglamento requireReglamento(InscripcionEquipo inscripcion) {
        Torneo torneo = torneoDe(inscripcion);
        return reglamentos.findByTorneoId(torneo.getId())
                .orElseThrow(() -> new BusinessException("El torneo " + torneo.getNombre()
                        + " todavía no tiene reglamento: sin fecha de corte ni mínimo y máximo de"
                        + " jugadores no se puede validar la lista de buena fe"));
    }

    private void requireListaEditable(InscripcionEquipo inscripcion) {
        // Tarea S2-03: el entrenador solo toca la lista de un club donde trabaja.
        acceso.requireClubPropio(inscripcion.getClub().getId(),
                "/api/v1/torneos/inscripciones/" + inscripcion.getId() + "/lista-buena-fe");
        if (Boolean.TRUE.equals(inscripcion.getListaConfirmada())) {
            throw new ConflictException("La lista de buena fe del equipo " + inscripcion.getId()
                    + " ya está confirmada y no se puede modificar");
        }
        if (inscripcion.getEstado() == EstadoInscripcion.RECHAZADA) {
            throw new BusinessException("La inscripción " + inscripcion.getId()
                    + " está RECHAZADA: no se puede cargar su lista de buena fe");
        }
    }

    private void apply(ListaBuenaFe entity, ListaBuenaFeRequestDto request) {
        entity.setJugador(relations.find(Jugador.class, request.jugadorId()));
        entity.setPosicion(relations.find(Posicion.class, request.posicionId()));
        entity.setDorsal(request.dorsal());
        entity.setHabilitado(true);
        entity.setMotivoInhabilitacion(null);
    }

    private void validate(ListaBuenaFe entity, InscripcionEquipo inscripcion, Reglamento reglamento) {
        validarDorsalUnico(entity, inscripcion);
        validarJugadorUnicoEnElTorneo(entity, inscripcion);
        validarElegibilidad(entity, inscripcion, reglamento);
    }

    /** Tarea S2-07: dorsal único dentro del equipo. */
    private void validarDorsalUnico(ListaBuenaFe entity, InscripcionEquipo inscripcion) {
        repository.findByInscripcionEquipoIdAndDorsal(inscripcion.getId(), entity.getDorsal())
                .filter(otro -> !Objects.equals(otro.getId(), entity.getId()))
                .ifPresent(otro -> {
                    throw new ConflictException("El dorsal " + entity.getDorsal()
                            + " ya lo tiene el jugador " + otro.getJugador().getId()
                            + " en este equipo");
                });
    }

    /** Tarea S2-07: un jugador en un solo equipo por torneo. */
    private void validarJugadorUnicoEnElTorneo(ListaBuenaFe entity, InscripcionEquipo inscripcion) {
        Long jugadorId = entity.getJugador().getId();
        repository.findByTorneoIdAndJugadorId(torneoDe(inscripcion).getId(), jugadorId)
                .filter(otro -> !Objects.equals(otro.getId(), entity.getId()))
                .ifPresent(otro -> {
                    boolean mismoEquipo = Objects.equals(
                            otro.getInscripcionEquipo().getId(), inscripcion.getId());
                    throw new ConflictException("El jugador " + jugadorId
                            + (mismoEquipo
                                    ? " ya está en la lista de buena fe de este equipo"
                                    : " ya está en la lista de buena fe del equipo "
                                            + otro.getInscripcionEquipo().getId()
                                            + " en este torneo"));
                });
    }

    /** Tarea S2-07: lo que ficha técnica sabe del jugador, más las sanciones. */
    private void validarElegibilidad(ListaBuenaFe entity, InscripcionEquipo inscripcion,
            Reglamento reglamento) {
        Long jugadorId = entity.getJugador().getId();
        LocalDate corte = reglamento.getFechaCorte();
        JugadorParaTorneosDto datos = fichaTecnica.datosParaTorneos(jugadorId, corte)
                .orElseThrow(() -> new NotFoundException(
                        "El jugador con id " + jugadorId + " no existe en ficha técnica"));

        validarEstado(datos);
        validarDocumentos(datos);
        validarEdad(datos, inscripcion.getTorneoCategoria(), corte);
        validarFichaje(datos, inscripcion, corte);
        aplicarSancionVigente(entity, inscripcion, jugadorId);
    }

    private void validarEstado(JugadorParaTorneosDto datos) {
        if (datos.estado() != EstadoJugador.ACTIVO) {
            throw new BusinessException("El jugador " + datos.jugadorId() + " está "
                    + datos.estado() + " en ficha técnica: solo se puede registrar un jugador ACTIVO");
        }
    }

    private void validarDocumentos(JugadorParaTorneosDto datos) {
        if (!datos.documentosVigentes()) {
            throw new BusinessException("El jugador " + datos.jugadorId()
                    + " no tiene los documentos vigentes");
        }
        if (!datos.aptoMedicoVigente()) {
            throw new BusinessException("El jugador " + datos.jugadorId()
                    + " no tiene el apto médico vigente");
        }
    }

    /** Regla 1: la edad va a la fecha de corte del reglamento, nunca a la del partido. */
    private void validarEdad(JugadorParaTorneosDto datos, TorneoCategoria categoria, LocalDate corte) {
        int edad = datos.edadAFechaDeCorte();
        if (edad == MocksExternosConfiguration.EDAD_DESCONOCIDA) {
            throw new BusinessException("El jugador " + datos.jugadorId()
                    + " no tiene fecha de nacimiento cargada en ficha técnica: no se puede validar"
                    + " su edad al " + corte);
        }
        Short minima = categoria.getEdadMinima();
        if (edad > categoria.getEdadMaxima() || (minima != null && edad < minima)) {
            throw new BusinessException("El jugador " + datos.jugadorId() + " tiene " + edad
                    + " años al " + corte + " y la categoría " + categoria.getNombre() + " admite "
                    + (minima == null ? "hasta " + categoria.getEdadMaxima()
                            : "de " + minima + " a " + categoria.getEdadMaxima()) + " años");
        }
    }

    private void validarFichaje(JugadorParaTorneosDto datos, InscripcionEquipo inscripcion,
            LocalDate corte) {
        Long clubId = inscripcion.getClub().getId();
        if (!datos.clubesDondeEstaFichado().contains(clubId)) {
            throw new BusinessException("El jugador " + datos.jugadorId()
                    + " no está fichado en el club " + clubId + " al " + corte);
        }
    }

    /**
     * Sanción vigente del mismo torneo: no entra. De otro torneo: entra
     * inhabilitado, con el motivo, y la planilla lo bloquea (tarea S4-03).
     */
    private void aplicarSancionVigente(ListaBuenaFe entity, InscripcionEquipo inscripcion,
            Long jugadorId) {
        List<Sancion> vigentes = sanciones.findByListaBuenaFeJugadorIdAndEstado(
                jugadorId, EstadoSancion.VIGENTE);
        if (vigentes.isEmpty()) {
            return;
        }
        Long torneoId = torneoDe(inscripcion).getId();
        Optional<Sancion> deEsteTorneo = vigentes.stream()
                .filter(sancion -> Objects.equals(
                        sancion.getListaBuenaFe().getTorneo().getId(), torneoId))
                .findFirst();
        if (deEsteTorneo.isPresent()) {
            throw new BusinessException("El jugador " + jugadorId
                    + " tiene una sanción vigente en este torneo: le quedan "
                    + fechasPendientes(deEsteTorneo.get()) + " fecha(s) por cumplir");
        }
        Sancion otra = vigentes.get(0);
        entity.setHabilitado(false);
        entity.setMotivoInhabilitacion("Sanción vigente en el torneo "
                + otra.getListaBuenaFe().getTorneo().getNombre() + ": quedan "
                + fechasPendientes(otra) + " fecha(s) por cumplir");
    }

    private int fechasPendientes(Sancion sancion) {
        return sancion.getFechasSuspension() - sancion.getFechasCumplidas();
    }

    /** Tarea S2-08: el máximo también se respeta al ir cargando, no solo al confirmar. */
    private void validarMaximoDeJugadores(InscripcionEquipo inscripcion, Reglamento reglamento) {
        long total = repository.countByInscripcionEquipoId(inscripcion.getId());
        if (total >= reglamento.getMaxJugadores()) {
            throw new BusinessException("La lista de buena fe ya tiene " + total
                    + " jugadores y el reglamento del torneo permite hasta "
                    + reglamento.getMaxJugadores());
        }
    }

    private Torneo torneoDe(InscripcionEquipo inscripcion) {
        return inscripcion.getTorneoCategoria().getTorneo();
    }

    private ListaBuenaFeResponseDto toResponse(ListaBuenaFe entity) {
        return new ListaBuenaFeResponseDto(
                entity.getId(),
                relations.id(entity.getInscripcionEquipo()),
                relations.id(entity.getTorneo()),
                relations.id(entity.getJugador()),
                relations.id(entity.getPosicion()),
                entity.getDorsal(),
                entity.getHabilitado(),
                entity.getMotivoInhabilitacion(),
                entity.getFechaAlta());
    }
}
