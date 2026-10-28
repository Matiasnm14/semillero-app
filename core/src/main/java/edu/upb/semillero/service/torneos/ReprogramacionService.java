package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.dto.request.torneos.ReprogramacionRequestDto;
import edu.upb.semillero.repository.dto.response.torneos.ReprogramacionResponseDto;
import edu.upb.semillero.repository.entity.Partido;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.entity.torneos.Cancha;
import edu.upb.semillero.repository.entity.torneos.Reglamento;
import edu.upb.semillero.repository.entity.torneos.Reprogramacion;
import edu.upb.semillero.repository.entity.torneos.TorneoCategoria;
import edu.upb.semillero.repository.enums.EstadoPartido;
import edu.upb.semillero.repository.repos.torneos.CanchaRepository;
import edu.upb.semillero.repository.repos.torneos.PartidoFixtureRepository;
import edu.upb.semillero.repository.repos.torneos.ReglamentoRepository;
import edu.upb.semillero.repository.repos.torneos.ReprogramacionRepository;
import edu.upb.semillero.service.EntityRelations;
import edu.upb.semillero.service.exception.BusinessException;
import edu.upb.semillero.service.exception.ConflictException;
import edu.upb.semillero.service.exception.NotFoundException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU 8 - Reprogramar un partido (tareas S3-16 a S3-19).
 *
 * <ul>
 *   <li><strong>S3-16</strong>: nueva fecha, hora y cancha, con motivo
 *       obligatorio. Un partido cerrado (FINALIZADO) o anulado (CANCELADO) no se
 *       reprograma.</li>
 *   <li><strong>S3-17</strong>: los mismos choques que el fixture: cancha,
 *       partidos simultáneos del mismo equipo y descanso mínimo del reglamento.
 *       Si choca, el error dice con qué partido.</li>
 *   <li><strong>S3-18</strong>: cada reprogramación queda en
 *       {@code reprogramaciones} con lo anterior, lo nuevo, el motivo y quién
 *       la hizo.</li>
 *   <li><strong>S3-19</strong>: se avisa a los dos equipos por
 *       {@code notificaciones}.</li>
 * </ul>
 *
 * <p>Un partido EN_CURSO también se reprograma: es el caso del partido
 * suspendido que se termina otro día. Vuelve a PROGRAMADO y conserva su
 * planilla y sus eventos.
 */
@Service
@Transactional(readOnly = true)
public class ReprogramacionService {
    private static final DateTimeFormatter FECHA_Y_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int ENTRETIEMPO_MIN = 15;

    private final PartidoFixtureRepository partidos;
    private final ReprogramacionRepository repository;
    private final CanchaRepository canchas;
    private final ReglamentoRepository reglamentos;
    private final AvisosTorneos avisos;
    private final EntityRelations relations;

    public ReprogramacionService(PartidoFixtureRepository partidos,
            ReprogramacionRepository repository, CanchaRepository canchas,
            ReglamentoRepository reglamentos, AvisosTorneos avisos, EntityRelations relations) {
        this.partidos = partidos;
        this.repository = repository;
        this.canchas = canchas;
        this.reglamentos = reglamentos;
        this.avisos = avisos;
        this.relations = relations;
    }

    /** Tarea S3-18: el historial del partido, de la primera reprogramación a la última. */
    public List<ReprogramacionResponseDto> historial(Long partidoId) {
        requirePartido(partidoId);
        return repository.findByPartidoIdOrderByFechaAsc(partidoId).stream()
                .map(entity -> toResponse(entity, null))
                .toList();
    }

    @Transactional
    public ReprogramacionResponseDto reprogramar(Long partidoId, ReprogramacionRequestDto request) {
        Partido partido = requirePartido(partidoId);
        requireReprogramable(partido);
        Cancha nueva = requireCancha(request.canchaId());
        if (request.fechaInicio().isBefore(LocalDateTime.now())) {
            throw new BusinessException("La nueva fecha " + request.fechaInicio().format(FECHA_Y_HORA)
                    + " ya pasó: un partido se reprograma hacia adelante");
        }
        LocalDateTime inicioAnterior = partido.getFechaInicio();
        Cancha canchaAnterior = partido.getCancha();
        if (request.fechaInicio().equals(inicioAnterior) && canchaAnterior != null
                && Objects.equals(canchaAnterior.getId(), nueva.getId())) {
            throw new BusinessException("El partido " + partidoId
                    + " ya está programado a esa hora y en esa cancha: no hay nada que reprogramar");
        }

        int duracion = duracion(partido, request.duracionPartidoMin());
        partido.setFechaInicio(request.fechaInicio());
        partido.setFechaFin(request.fechaInicio().plusMinutes(duracion));
        partido.setCancha(nueva);
        validarSinChoques(partido);
        if (partido.getEstado() == EstadoPartido.EN_CURSO) {
            partido.setEstado(EstadoPartido.PROGRAMADO);
        }
        partidos.saveAndFlush(partido);

        Reprogramacion entity = new Reprogramacion();
        entity.setPartido(partido);
        entity.setFechaInicioAnterior(inicioAnterior);
        entity.setCanchaAnterior(canchaAnterior);
        entity.setFechaInicioNueva(partido.getFechaInicio());
        entity.setCanchaNueva(nueva);
        entity.setMotivo(request.motivo().trim());
        entity.setUsuario(relations.find(Usuario.class, request.usuarioId()));
        entity.setFecha(LocalDateTime.now());
        Reprogramacion guardada = repository.saveAndFlush(entity);

        int avisados = avisos.avisarALosEquipos(partido, "Partido reprogramado",
                mensaje(partido, inicioAnterior, canchaAnterior, guardada.getMotivo()));
        return toResponse(guardada, avisados);
    }

    private Partido requirePartido(Long partidoId) {
        Partido partido = partidos.findById(partidoId)
                .orElseThrow(() -> new NotFoundException("Partido con id " + partidoId + " no existe"));
        if (partido.getTorneoCategoria() == null) {
            throw new BusinessException("El partido " + partidoId
                    + " no es de un torneo: se reprograma desde el CRUD de partidos");
        }
        return partido;
    }

    /** HU 8: un partido cerrado no se reprograma, y uno anulado tampoco. */
    private void requireReprogramable(Partido partido) {
        if (partido.getEstado() == EstadoPartido.FINALIZADO) {
            throw new ConflictException("El partido " + partido.getId()
                    + " está cerrado (FINALIZADO): no se reprograma");
        }
        if (partido.getEstado() == EstadoPartido.CANCELADO) {
            throw new ConflictException("El partido " + partido.getId()
                    + " está anulado (CANCELADO): no se reprograma");
        }
    }

    private Cancha requireCancha(Long canchaId) {
        Cancha cancha = canchas.findById(canchaId)
                .orElseThrow(() -> new NotFoundException("La cancha con id " + canchaId + " no existe"));
        if (!Boolean.TRUE.equals(cancha.getActiva())) {
            throw new BusinessException("La cancha " + cancha.getNombre() + " está inactiva");
        }
        return cancha;
    }

    /** Si no se pide otra, el partido conserva la duración que ya tenía. */
    private int duracion(Partido partido, Integer pedida) {
        if (pedida != null) {
            return pedida;
        }
        if (partido.getFechaFin() != null) {
            return (int) Duration.between(partido.getFechaInicio(), partido.getFechaFin()).toMinutes();
        }
        TorneoCategoria categoria = partido.getTorneoCategoria();
        return categoria.getDuracionTiempoMin() * 2 + ENTRETIEMPO_MIN;
    }

    /** Tarea S3-17: los mismos choques que valida el fixture, con el motivo exacto. */
    private void validarSinChoques(Partido partido) {
        LocalDate dia = partido.getFechaInicio().toLocalDate();
        List<Partido> ocupados = partidos.findByFechaInicioBetween(dia.atStartOfDay(),
                        dia.atTime(LocalTime.MAX)).stream()
                .filter(otro -> otro.getEstado() != EstadoPartido.CANCELADO)
                .toList();
        String choque = ChoquesDeAgenda.primerChoque(partido, ocupados, descansoMinimo(partido));
        if (choque != null) {
            throw new ConflictException("No se puede reprogramar el partido " + partido.getId()
                    + " al " + partido.getFechaInicio().format(FECHA_Y_HORA) + ": " + choque);
        }
    }

    private int descansoMinimo(Partido partido) {
        return reglamentos.findByTorneoId(partido.getTorneoCategoria().getTorneo().getId())
                .map(Reglamento::getDescansoMinimoMin)
                .map(Short::intValue)
                .orElse(0);
    }

    /** Tarea S3-19: el aviso lleva los clubes y la cancha, nunca datos personales. */
    private String mensaje(Partido partido, LocalDateTime inicioAnterior, Cancha canchaAnterior,
            String motivo) {
        TorneoCategoria categoria = partido.getTorneoCategoria();
        return "El partido " + partido.getClubLocal().getNombreClub() + " vs "
                + partido.getClubVisitante().getNombreClub() + " (" + categoria.getTorneo().getNombre()
                + ", " + categoria.getNombre() + ") pasa del " + inicioAnterior.format(FECHA_Y_HORA)
                + (canchaAnterior == null ? "" : " en " + canchaAnterior.getNombre())
                + " al " + partido.getFechaInicio().format(FECHA_Y_HORA) + " en "
                + partido.getCancha().getNombre() + " (" + partido.getCancha().getTipoSuperficie()
                + "). Motivo: " + motivo;
    }

    private ReprogramacionResponseDto toResponse(Reprogramacion entity, Integer avisados) {
        return new ReprogramacionResponseDto(
                entity.getId(),
                relations.id(entity.getPartido()),
                entity.getFechaInicioAnterior(),
                relations.id(entity.getCanchaAnterior()),
                entity.getFechaInicioNueva(),
                relations.id(entity.getCanchaNueva()),
                entity.getMotivo(),
                relations.id(entity.getUsuario()),
                entity.getFecha(),
                avisados);
    }
}
