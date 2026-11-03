package edu.upb.semillero.service.scouting;

import edu.upb.semillero.repository.dto.request.PruebaRequestDto;
import edu.upb.semillero.repository.dto.request.scouting.*;
import edu.upb.semillero.repository.dto.response.scouting.*;
import edu.upb.semillero.repository.entity.*;
import edu.upb.semillero.repository.entity.scouting.*;
import edu.upb.semillero.repository.enums.EstadoJugador;
import edu.upb.semillero.repository.repos.*;
import edu.upb.semillero.repository.repos.scouting.*;
import edu.upb.semillero.service.PruebaService;
import edu.upb.semillero.service.exception.*;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class ConvocatoriaPruebaService {
    private static final Set<String> ACTIVOS = Set.of("PENDIENTE", "PRESELECCIONADO", "ACEPTADO");
    private static final Set<String> DECISIONES =
            Set.of("PENDIENTE", "PRESELECCIONADO", "ACEPTADO", "RECHAZADO");
    private final ConvocatoriaPruebaRepository convocatorias;
    private final InscripcionPruebaScoutingRepository inscripciones;
    private final EvaluacionPruebaScoutingRepository evaluaciones;
    private final PruebaService pruebas;
    private final PruebaRepository basePruebas;
    private final JugadorRepository jugadores;
    private final UsuarioTrabajaRepository trabajos;
    private final AccesoScouting acceso;
    private final Clock clock;
    private final ZoneId zona;

    public ConvocatoriaPruebaService(
            ConvocatoriaPruebaRepository convocatorias,
            InscripcionPruebaScoutingRepository inscripciones,
            EvaluacionPruebaScoutingRepository evaluaciones,
            PruebaService pruebas,
            PruebaRepository basePruebas,
            JugadorRepository jugadores,
            UsuarioTrabajaRepository trabajos,
            AccesoScouting acceso,
            @Qualifier("scoutingClock") Clock clock,
            @Value("${scouting.pruebas.zona:America/La_Paz}") String zona) {
        this.convocatorias = convocatorias;
        this.inscripciones = inscripciones;
        this.evaluaciones = evaluaciones;
        this.pruebas = pruebas;
        this.basePruebas = basePruebas;
        this.jugadores = jugadores;
        this.trabajos = trabajos;
        this.acceso = acceso;
        this.clock = clock;
        this.zona = ZoneId.of(zona);
    }

    private UsuarioClub exigirDT(Long clubId) {
        return trabajos.findByUsuarioClubUsuarioId(acceso.usuario()).stream()
                .filter(t -> t.getClub() != null && clubId.equals(t.getClub().getId()))
                .filter(
                        t ->
                                t.getTipoUsuarioClub() != null
                                        && Set.of("DT", "EN")
                                                .contains(
                                                        t.getTipoUsuarioClub()
                                                                .trim()
                                                                .toUpperCase(Locale.ROOT)))
                .map(UsuarioTrabaja::getUsuarioClub)
                .findFirst()
                .orElseThrow(
                        () ->
                                new ForbiddenException(
                                        "Solo un DT o entrenador vinculado al club puede gestionar"
                                            + " sus pruebas"));
    }

    private boolean puedeGestionar(Long clubId) {
        return trabajos.findByUsuarioClubUsuarioId(acceso.usuario()).stream()
                .anyMatch(
                        t ->
                                t.getClub() != null
                                        && clubId.equals(t.getClub().getId())
                                        && t.getTipoUsuarioClub() != null
                                        && Set.of("DT", "EN")
                                                .contains(
                                                        t.getTipoUsuarioClub()
                                                                .trim()
                                                                .toUpperCase(Locale.ROOT)));
    }

    private ConvocatoriaPrueba require(Long id) {
        acceso.usuario();
        return convocatorias
                .findById(id)
                .orElseThrow(() -> new NotFoundException("Convocatoria no disponible"));
    }

    private ConvocatoriaPrueba bloquear(Long id) {
        return convocatorias
                .bloquear(id)
                .orElseThrow(() -> new NotFoundException("Convocatoria no disponible"));
    }

    private Instant inicio(ConvocatoriaPrueba c) {
        return c.getPrueba()
                .getFechaPrueba()
                .atTime(c.getPrueba().getHoraPrueba())
                .atZone(zona)
                .toInstant();
    }

    private void version(Long actual, Long esperada) {
        if (!Objects.equals(actual, esperada))
            throw new ConflictException("El registro cambió; recarga antes de continuar");
    }

    public Page<ConvocatoriaPruebaVista> listar(Long clubId, int page, int size) {
        acceso.usuario();
        return convocatorias
                .buscar(clubId, DirectorioScoutingService.pagina(page, size, "creadaEn"))
                .map(this::vista);
    }

    public ConvocatoriaPruebaVista obtener(Long id) {
        return vista(require(id));
    }

    @Transactional
    public ConvocatoriaPruebaVista crear(ConvocatoriaPruebaDto d) {
        UsuarioClub dt = exigirDT(d.clubId());
        validar(d);
        if (d.estado() != null && !"ABIERTA".equals(d.estado()))
            throw new BusinessException("Una convocatoria se publica abierta");
        var base =
                pruebas.create(
                        new PruebaRequestDto(
                                d.fechaPrueba(), d.horaPrueba(), d.clubId(), dt.getId()));
        var c = new ConvocatoriaPrueba();
        c.setPrueba(basePruebas.findById(base.id()).orElseThrow());
        c.setCreadorId(acceso.usuario());
        c.setCreadaEn(clock.instant());
        aplicar(c, d);
        return vista(convocatorias.saveAndFlush(c));
    }

    private void validar(ConvocatoriaPruebaDto d) {
        Instant fecha = d.fechaPrueba().atTime(d.horaPrueba()).atZone(zona).toInstant();
        if (!fecha.isAfter(clock.instant()))
            throw new BusinessException("La prueba debe programarse en el futuro");
        if (!d.limiteInscripcion().isAfter(clock.instant()) || d.limiteInscripcion().isAfter(fecha))
            throw new BusinessException(
                    "El cierre de inscripción debe ser futuro y no posterior al inicio de la"
                        + " prueba");
        if (d.edadMin() != null && d.edadMax() != null && d.edadMin() > d.edadMax())
            throw new BusinessException("El rango de edad no es válido");
    }

    private void aplicar(ConvocatoriaPrueba c, ConvocatoriaPruebaDto d) {
        c.setTitulo(d.titulo().strip());
        c.setLugar(d.lugar().strip());
        c.setCategoria(d.categoria().strip());
        c.setDescripcion(d.descripcion().strip());
        c.setCupos(d.cupos());
        c.setLimiteInscripcion(d.limiteInscripcion());
        c.setEdadMin(d.edadMin());
        c.setEdadMax(d.edadMax());
        c.setPosiciones(limpiar(d.posiciones()));
        c.setRequisitos(limpiar(d.requisitos()));
    }

    private Set<String> limpiar(Set<String> valores) {
        var out = new LinkedHashSet<String>();
        if (valores != null) valores.forEach(v -> out.add(v.strip()));
        return out;
    }

    @Transactional
    public ConvocatoriaPruebaVista actualizar(Long id, ConvocatoriaPruebaDto d) {
        var antes = require(id);
        exigirDT(antes.getPrueba().getClub().getId());
        var c = bloquear(id);
        version(c.getVersion(), d.version());
        if (!d.clubId().equals(c.getPrueba().getClub().getId()))
            throw new ForbiddenException("No puedes transferir una convocatoria de club");
        if ("CANCELADA".equals(c.getEstado()))
            throw new ConflictException("La convocatoria está cancelada");
        if (!d.fechaPrueba().equals(c.getPrueba().getFechaPrueba())
                || !d.horaPrueba().equals(c.getPrueba().getHoraPrueba()))
            throw new BusinessException(
                    "No se cambia la fecha de una convocatoria publicada; cancela y publica otra");
        if (inscripciones.ocupados(id) > d.cupos())
            throw new ConflictException(
                    "El cupo no puede ser menor al número de inscripciones activas");
        if (d.edadMin() != null && d.edadMax() != null && d.edadMin() > d.edadMax())
            throw new BusinessException("Rango de edad inválido");
        if (!Objects.equals(c.getEdadMin(), d.edadMin())
                || !Objects.equals(c.getEdadMax(), d.edadMax()))
            throw new BusinessException("La categoría de edad no cambia después de publicar");
        if (d.limiteInscripcion().isAfter(inicio(c)))
            throw new BusinessException("El cierre no puede superar el inicio");
        if (d.estado() != null) {
            if (!Set.of("ABIERTA", "EN_CURSO", "CERRADA").contains(d.estado()))
                throw new BusinessException("Estado inválido");
            if (!"ABIERTA".equals(d.estado()) && clock.instant().isBefore(inicio(c)))
                throw new BusinessException(
                        "La prueba todavía no comenzó; puedes cancelar la convocatoria");
            if ("ABIERTA".equals(d.estado()) && !clock.instant().isBefore(inicio(c)))
                throw new BusinessException("No se reabre una prueba que ya comenzó");
            c.setEstado(d.estado());
        }
        aplicar(c, d);
        convocatorias.flush();
        return vista(c);
    }

    @Transactional
    public void cancelar(Long id, Long esperada) {
        var antes = require(id);
        exigirDT(antes.getPrueba().getClub().getId());
        var c = bloquear(id);
        version(c.getVersion(), esperada);
        c.setEstado("CANCELADA");
        convocatorias.flush();
    }

    @Transactional
    public InscripcionPruebaVista inscribir(Long id, InscripcionPruebaDto d) {
        acceso.usuario();
        var c = bloquear(id);
        if (!"ABIERTA".equals(c.getEstado())
                || !clock.instant().isBefore(c.getLimiteInscripcion())
                || !clock.instant().isBefore(inicio(c)))
            throw new ConflictException("La inscripción a esta prueba está cerrada");
        var j =
                jugadores
                        .findFirstByUsuarioId(acceso.usuario())
                        .orElseThrow(
                                () ->
                                        new ForbiddenException(
                                                "Debes tener una ficha de jugador propia"));
        if (j.getEstado() != EstadoJugador.ACTIVO
                || j.getFechaNacimiento() == null
                || Period.between(
                                        j.getFechaNacimiento(),
                                        LocalDate.ofInstant(clock.instant(), zona))
                                .getYears()
                        < 18)
            throw new ForbiddenException(
                    "La inscripción de menores o edad desconocida requiere el flujo de tutor"
                        + " verificado");
        int edad =
                Period.between(j.getFechaNacimiento(), c.getPrueba().getFechaPrueba()).getYears();
        if (edad < (c.getEdadMin() == null ? 18 : c.getEdadMin())
                || c.getEdadMax() != null && edad > c.getEdadMax())
            throw new BusinessException("No cumples el rango de edad de la convocatoria");
        var previa = inscripciones.findByConvocatoriaIdAndJugadorId(id, j.getId());
        if (previa.isPresent()) {
            if ("RETIRADO".equals(previa.get().getEstado()))
                throw new ConflictException("La inscripción fue retirada; no se puede reactivar");
            return participante(previa.get());
        } // retries cannot duplicate or re-enrol a withdrawal
        if (inscripciones.ocupados(id) >= c.getCupos())
            throw new ConflictException("No quedan cupos disponibles");
        var i = new InscripcionPruebaScouting();
        i.setConvocatoria(c);
        i.setJugador(j);
        i.setCreadaEn(clock.instant());
        i.setNota(d == null || d.nota() == null ? null : d.nota().strip());
        return participante(inscripciones.saveAndFlush(i));
    }

    public Page<InscripcionPruebaVista> inscritos(Long id, int page, int size) {
        var c = require(id);
        exigirDT(c.getPrueba().getClub().getId());
        return inscripciones
                .findByConvocatoriaId(id, DirectorioScoutingService.pagina(page, size, "creadaEn"))
                .map(this::participante);
    }

    private InscripcionPruebaScouting inscripcion(Long convocatoria, UUID id) {
        var i =
                inscripciones
                        .findById(id)
                        .orElseThrow(() -> new NotFoundException("Inscripción no disponible"));
        if (!convocatoria.equals(i.getConvocatoria().getId()))
            throw new NotFoundException("Inscripción no disponible");
        return i;
    }

    @Transactional
    public void retirar(Long id, UUID inscripcionId, Long esperada) {
        acceso.usuario();
        var c = bloquear(id);
        var i = inscripcion(id, inscripcionId);
        if (!i.getJugador().getUsuario().getId().equals(acceso.usuario()))
            throw new NotFoundException("Inscripción no disponible");
        version(i.getVersion(), esperada);
        if (!"CANCELADA".equals(c.getEstado()) && !clock.instant().isBefore(inicio(c)))
            throw new ConflictException("La prueba ya comenzó; no se retira la inscripción");
        i.setEstado("RETIRADO");
        inscripciones.flush();
    }

    private void decidir(ConvocatoriaPrueba c, InscripcionPruebaScouting i, String estado) {
        if ("CANCELADA".equals(c.getEstado()) || "RETIRADO".equals(i.getEstado()))
            throw new ConflictException("La inscripción ya no permite decisiones");
        if (!DECISIONES.contains(estado)) throw new BusinessException("Resultado inválido");
        if (ACTIVOS.contains(estado)
                && !ACTIVOS.contains(i.getEstado())
                && inscripciones.ocupados(c.getId()) >= c.getCupos())
            throw new ConflictException("No quedan cupos para reactivar esta inscripción");
        i.setEstado(estado);
    }

    @Transactional
    public InscripcionPruebaVista decision(Long id, UUID inscripcionId, DecisionPruebaDto d) {
        exigirDT(require(id).getPrueba().getClub().getId());
        var c = bloquear(id);
        var i = inscripcion(id, inscripcionId);
        version(i.getVersion(), d.version());
        decidir(c, i, d.estado());
        inscripciones.flush();
        return participante(i);
    }

    @Transactional
    public InscripcionPruebaVista evaluar(Long id, UUID inscripcionId, EvaluacionPruebaDto d) {
        exigirDT(require(id).getPrueba().getClub().getId());
        var c = bloquear(id);
        var i = inscripcion(id, inscripcionId);
        version(i.getVersion(), d.version());
        if (clock.instant().isBefore(inicio(c)))
            throw new ConflictException("La asistencia se registra desde el inicio de la prueba");
        if (!Set.of("PRESENTE", "AUSENTE").contains(d.asistencia()))
            throw new BusinessException("Asistencia inválida");
        decidir(c, i, d.resultado());
        i.setAsistencia(d.asistencia());
        i.setObservaciones(d.observaciones().strip());
        i.setEvaluadaEn(clock.instant());
        var e = new EvaluacionPruebaScouting();
        e.setInscripcionId(i.getId());
        e.setEvaluadorId(acceso.usuario());
        e.setFecha(clock.instant());
        e.setAsistencia(d.asistencia());
        e.setResultado(d.resultado());
        e.setObservaciones(d.observaciones().strip());
        evaluaciones.save(e);
        inscripciones.flush();
        return participante(i);
    }

    public record EvaluacionVista(
            UUID id,
            Long evaluadorId,
            Instant fecha,
            String asistencia,
            String resultado,
            String observaciones) {}

    public Page<EvaluacionVista> historial(Long id, UUID inscripcionId, int page, int size) {
        var c = require(id);
        var i = inscripcion(id, inscripcionId);
        if (!i.getJugador().getUsuario().getId().equals(acceso.usuario()))
            exigirDT(c.getPrueba().getClub().getId());
        return evaluaciones
                .findByInscripcionId(
                        inscripcionId, DirectorioScoutingService.pagina(page, size, "fecha"))
                .map(
                        e ->
                                new EvaluacionVista(
                                        e.getId(),
                                        e.getEvaluadorId(),
                                        e.getFecha(),
                                        e.getAsistencia(),
                                        e.getResultado(),
                                        e.getObservaciones()));
    }

    private InscripcionPruebaVista participante(InscripcionPruebaScouting i) {
        var j = i.getJugador();
        var u = j.getUsuario();
        Integer edad =
                j.getFechaNacimiento() == null
                        ? null
                        : Period.between(
                                        j.getFechaNacimiento(),
                                        i.getConvocatoria().getPrueba().getFechaPrueba())
                                .getYears();
        return new InscripcionPruebaVista(
                i.getId(),
                j.getId(),
                u == null
                        ? "Perfil no disponible"
                        : ((u.getNombres() == null ? "" : u.getNombres())
                                        + " "
                                        + (u.getApellidos() == null ? "" : u.getApellidos()))
                                .strip(),
                u == null ? null : u.getFotoPerfil(),
                j.getPiernaHabil(),
                edad,
                i.getEstado(),
                i.getNota(),
                i.getAsistencia(),
                i.getObservaciones(),
                i.getCreadaEn(),
                i.getEvaluadaEn(),
                i.getVersion());
    }

    private ConvocatoriaPruebaVista vista(ConvocatoriaPrueba c) {
        var club = c.getPrueba().getClub();
        var propia =
                jugadores
                        .findFirstByUsuarioId(acceso.usuario())
                        .flatMap(
                                j ->
                                        inscripciones.findByConvocatoriaIdAndJugadorId(
                                                c.getId(), j.getId()))
                        .map(this::participante)
                        .orElse(null);
        return new ConvocatoriaPruebaVista(
                c.getId(),
                club.getId(),
                club.getNombreClub(),
                club.getCiudad() == null ? null : club.getCiudad().getNombreCiudad(),
                club.getFotoClub(),
                c.getTitulo(),
                c.getPrueba().getFechaPrueba(),
                c.getPrueba().getHoraPrueba(),
                zona.getId(),
                inicio(c),
                c.getLugar(),
                c.getCategoria(),
                c.getDescripcion(),
                c.getCupos(),
                inscripciones.ocupados(c.getId()),
                c.getLimiteInscripcion(),
                c.getEdadMin(),
                c.getEdadMax(),
                Set.copyOf(c.getPosiciones()),
                Set.copyOf(c.getRequisitos()),
                c.getEstado(),
                c.getVersion(),
                puedeGestionar(club.getId()),
                propia);
    }
}
