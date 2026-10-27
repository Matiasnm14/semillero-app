package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.dto.request.torneos.ReglamentoRequestDto;
import edu.upb.semillero.repository.dto.response.torneos.AuditoriaResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.ReglamentoDesempateResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.ReglamentoResponseDto;
import edu.upb.semillero.repository.entity.Auditoria;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.entity.torneos.CriterioDesempate;
import edu.upb.semillero.repository.entity.torneos.Reglamento;
import edu.upb.semillero.repository.entity.torneos.ReglamentoDesempate;
import edu.upb.semillero.repository.entity.torneos.Torneo;
import edu.upb.semillero.repository.repos.AuditoriaRepository;
import edu.upb.semillero.repository.repos.torneos.CriterioDesempateRepository;
import edu.upb.semillero.repository.repos.torneos.ReglamentoDesempateRepository;
import edu.upb.semillero.repository.repos.torneos.ReglamentoRepository;
import edu.upb.semillero.repository.repos.torneos.TorneoRepository;
import edu.upb.semillero.service.EntityRelations;
import edu.upb.semillero.service.exception.BusinessException;
import edu.upb.semillero.service.exception.ConflictException;
import edu.upb.semillero.service.exception.NotFoundException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU 2 - Reglamento del torneo (tareas S1-12, S1-13 y S1-14). Uno por torneo.
 *
 * <p>Tarea S1-14: cada cambio queda en {@code auditoria} con quién lo hizo, con
 * el reglamento completo antes y después en JSON, para poder comparar. Se
 * consulta en {@code GET /api/v1/torneos/&#123;torneoId&#125;/reglamento/historial}.
 */
@Service
@Transactional(readOnly = true)
public class ReglamentoService {
    private static final String MODULO = "TORNEOS";
    private static final String TABLA = "reglamentos";

    private final ReglamentoRepository repository;
    private final ReglamentoDesempateRepository desempates;
    private final CriterioDesempateRepository criterios;
    private final TorneoRepository torneos;
    private final AuditoriaRepository auditoria;
    private final EntityRelations relations;

    public ReglamentoService(ReglamentoRepository repository, ReglamentoDesempateRepository desempates,
            CriterioDesempateRepository criterios, TorneoRepository torneos,
            AuditoriaRepository auditoria, EntityRelations relations) {
        this.repository = repository;
        this.desempates = desempates;
        this.criterios = criterios;
        this.torneos = torneos;
        this.auditoria = auditoria;
        this.relations = relations;
    }

    public ReglamentoResponseDto findByTorneo(Long torneoId) {
        return toResponse(require(torneoId));
    }

    @Transactional
    public ReglamentoResponseDto create(Long torneoId, ReglamentoRequestDto request) {
        Torneo torneo = requireTorneo(torneoId);
        repository.findByTorneoId(torneoId).ifPresent(existing -> {
            throw new ConflictException("El torneo " + torneoId + " ya tiene un reglamento");
        });
        Reglamento entity = new Reglamento();
        entity.setTorneo(torneo);
        apply(entity, request, torneo);
        validate(entity);
        Reglamento creado = repository.saveAndFlush(entity);
        aplicarDesempates(creado, request.desempates());
        registrar("CREAR", creado.getId(), null, snapshot(creado), request.usuarioId());
        return toResponse(creado);
    }

    @Transactional
    public ReglamentoResponseDto update(Long torneoId, ReglamentoRequestDto request) {
        Reglamento entity = require(torneoId);
        String anterior = snapshot(entity);
        apply(entity, request, entity.getTorneo());
        validate(entity);
        Reglamento guardado = repository.saveAndFlush(entity);
        aplicarDesempates(guardado, request.desempates());
        registrar("MODIFICAR", guardado.getId(), anterior, snapshot(guardado), request.usuarioId());
        return toResponse(guardado);
    }

    @Transactional
    public void delete(Long torneoId, Long usuarioId) {
        Reglamento entity = require(torneoId);
        Long reglamentoId = entity.getId();
        String anterior = snapshot(entity);
        desempates.deleteByReglamentoId(reglamentoId);
        desempates.flush();
        repository.delete(entity);
        repository.flush();
        registrar("ELIMINAR", reglamentoId, anterior, null, usuarioId);
    }

    public List<ReglamentoDesempateResponseDto> findDesempates(Long torneoId) {
        return desempates.findByReglamentoIdOrderByOrdenAsc(require(torneoId).getId()).stream()
                .map(this::toDesempateResponse)
                .toList();
    }

    /** Tarea S1-13: reemplaza el orden completo de los criterios de desempate. */
    @Transactional
    public List<ReglamentoDesempateResponseDto> reemplazarDesempates(Long torneoId,
            List<String> codigos, Long usuarioId) {
        Reglamento reglamento = require(torneoId);
        String anterior = snapshot(reglamento);
        aplicarDesempates(reglamento, codigos);
        registrar("MODIFICAR_DESEMPATES", reglamento.getId(), anterior, snapshot(reglamento), usuarioId);
        return desempates.findByReglamentoIdOrderByOrdenAsc(reglamento.getId()).stream()
                .map(this::toDesempateResponse)
                .toList();
    }

    /**
     * Tarea S1-14: el historial de cambios, del más reciente al más antiguo.
     *
     * <p>Si el reglamento se borró, el historial deja de ser alcanzable por esta
     * ruta aunque las filas sigan en {@code auditoria}: hace falta el reglamento
     * para saber de qué torneo es.
     */
    public List<AuditoriaResponseDto> findHistorial(Long torneoId) {
        Reglamento reglamento = require(torneoId);
        return auditoria.findByTablaAndRegistroIdOrderByFechaDesc(TABLA, reglamento.getId()).stream()
                .map(this::toAuditoriaResponse)
                .toList();
    }

    private Reglamento require(Long torneoId) {
        requireTorneo(torneoId);
        return repository.findByTorneoId(torneoId)
                .orElseThrow(() -> new NotFoundException("El torneo " + torneoId + " no tiene reglamento"));
    }

    private Torneo requireTorneo(Long torneoId) {
        return torneos.findById(torneoId)
                .orElseThrow(() -> new NotFoundException("Torneo con id " + torneoId + " no existe"));
    }

    private void apply(Reglamento entity, ReglamentoRequestDto request, Torneo torneo) {
        entity.setPuntosVictoria(orDefault(request.puntosVictoria(), 3));
        entity.setPuntosEmpate(orDefault(request.puntosEmpate(), 1));
        entity.setPuntosDerrota(orDefault(request.puntosDerrota(), 0));
        entity.setUmbralAmarillas(orDefault(request.umbralAmarillas(), 3));
        entity.setFechasSuspensionAmarillas(orDefault(request.fechasSuspensionAmarillas(), 1));
        entity.setFechasSuspensionRoja(orDefault(request.fechasSuspensionRoja(), 1));
        entity.setGolesWalkover(orDefault(request.golesWalkover(), 3));
        entity.setFechaCorte(request.fechaCorte() != null
                ? request.fechaCorte()
                : LocalDate.of(torneo.getTemporada(), 12, 31));
        entity.setMinJugadores(request.minJugadores());
        entity.setMaxJugadores(request.maxJugadores());
        entity.setDescansoMinimoMin(orDefault(request.descansoMinimoMin(), 60));
    }

    private void validate(Reglamento entity) {
        if (entity.getMinJugadores() > entity.getMaxJugadores()) {
            throw new BusinessException("El mínimo de jugadores no puede ser mayor que el máximo");
        }
    }

    private void aplicarDesempates(Reglamento reglamento, List<String> codigos) {
        if (codigos == null) {
            return;
        }
        Set<String> vistos = new HashSet<>();
        List<CriterioDesempate> ordenados = new ArrayList<>();
        for (String codigo : codigos) {
            if (!vistos.add(codigo)) {
                throw new BusinessException("El criterio de desempate " + codigo + " está repetido");
            }
            ordenados.add(criterios.findByCodigo(codigo)
                    .orElseThrow(() -> new NotFoundException(
                            "El criterio de desempate " + codigo + " no existe")));
        }
        desempates.deleteByReglamentoId(reglamento.getId());
        desempates.flush();
        short orden = 1;
        for (CriterioDesempate criterio : ordenados) {
            ReglamentoDesempate fila = new ReglamentoDesempate();
            fila.setReglamento(reglamento);
            fila.setCriterioDesempate(criterio);
            fila.setOrden(orden++);
            desempates.save(fila);
        }
        desempates.flush();
    }

    /** El reglamento completo en JSON, para poder comparar dos entradas del historial. */
    private String snapshot(Reglamento entity) {
        String codigos = desempates.findByReglamentoIdOrderByOrdenAsc(entity.getId()).stream()
                .map(fila -> "\"" + fila.getCriterioDesempate().getCodigo() + "\"")
                .collect(Collectors.joining(","));
        return ("{\"puntosVictoria\":%d,\"puntosEmpate\":%d,\"puntosDerrota\":%d,"
                + "\"umbralAmarillas\":%d,\"fechasSuspensionAmarillas\":%d,"
                + "\"fechasSuspensionRoja\":%d,\"golesWalkover\":%d,\"fechaCorte\":\"%s\","
                + "\"minJugadores\":%d,\"maxJugadores\":%d,\"descansoMinimoMin\":%d,"
                + "\"desempates\":[%s]}")
                .formatted(entity.getPuntosVictoria(), entity.getPuntosEmpate(),
                        entity.getPuntosDerrota(), entity.getUmbralAmarillas(),
                        entity.getFechasSuspensionAmarillas(), entity.getFechasSuspensionRoja(),
                        entity.getGolesWalkover(), entity.getFechaCorte(),
                        entity.getMinJugadores(), entity.getMaxJugadores(),
                        entity.getDescansoMinimoMin(), codigos);
    }

    private void registrar(String accion, Long reglamentoId, String anterior, String nuevo, Long usuarioId) {
        Auditoria fila = new Auditoria();
        fila.setModulo(MODULO);
        fila.setTabla(TABLA);
        fila.setRegistroId(reglamentoId);
        fila.setAccion(accion);
        fila.setValorAnterior(anterior);
        fila.setValorNuevo(nuevo);
        fila.setUsuario(relations.find(Usuario.class, usuarioId));
        fila.setFecha(LocalDateTime.now());
        auditoria.saveAndFlush(fila);
    }

    private ReglamentoResponseDto toResponse(Reglamento entity) {
        List<String> codigos = desempates.findByReglamentoIdOrderByOrdenAsc(entity.getId()).stream()
                .map(fila -> fila.getCriterioDesempate().getCodigo())
                .toList();
        return new ReglamentoResponseDto(
                entity.getId(),
                relations.id(entity.getTorneo()),
                entity.getPuntosVictoria(),
                entity.getPuntosEmpate(),
                entity.getPuntosDerrota(),
                entity.getUmbralAmarillas(),
                entity.getFechasSuspensionAmarillas(),
                entity.getFechasSuspensionRoja(),
                entity.getGolesWalkover(),
                entity.getFechaCorte(),
                entity.getMinJugadores(),
                entity.getMaxJugadores(),
                entity.getDescansoMinimoMin(),
                codigos);
    }

    private ReglamentoDesempateResponseDto toDesempateResponse(ReglamentoDesempate entity) {
        CriterioDesempate criterio = entity.getCriterioDesempate();
        return new ReglamentoDesempateResponseDto(
                entity.getId(),
                relations.id(criterio),
                criterio.getCodigo(),
                criterio.getNombre(),
                entity.getOrden());
    }

    private AuditoriaResponseDto toAuditoriaResponse(Auditoria entity) {
        return new AuditoriaResponseDto(
                entity.getId(),
                entity.getModulo(),
                entity.getTabla(),
                entity.getRegistroId(),
                entity.getAccion(),
                entity.getValorAnterior(),
                entity.getValorNuevo(),
                entity.getMotivo(),
                relations.id(entity.getUsuario()),
                entity.getFecha());
    }

    private Short orDefault(Short value, int fallback) {
        return value != null ? value : (short) fallback;
    }
}
