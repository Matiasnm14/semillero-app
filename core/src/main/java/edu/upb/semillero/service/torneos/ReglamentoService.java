package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.dto.request.torneos.ReglamentoRequestDto;
import edu.upb.semillero.repository.dto.response.torneos.ReglamentoDesempateResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.ReglamentoResponseDto;
import edu.upb.semillero.repository.entity.torneos.CriterioDesempate;
import edu.upb.semillero.repository.entity.torneos.Reglamento;
import edu.upb.semillero.repository.entity.torneos.ReglamentoDesempate;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU 2 - Reglamento del torneo (tareas S1-12 y S1-13). Uno por torneo.
 *
 * <p>La tarea S1-14 (historial de cambios del reglamento) no está: necesita la
 * tabla compartida {@code auditoria}, que todavía no tiene dueño. Ver
 * docs/torneos/PENDIENTES.md.
 */
@Service
@Transactional(readOnly = true)
public class ReglamentoService {
    private final ReglamentoRepository repository;
    private final ReglamentoDesempateRepository desempates;
    private final CriterioDesempateRepository criterios;
    private final TorneoRepository torneos;
    private final EntityRelations relations;

    public ReglamentoService(ReglamentoRepository repository, ReglamentoDesempateRepository desempates,
            CriterioDesempateRepository criterios, TorneoRepository torneos, EntityRelations relations) {
        this.repository = repository;
        this.desempates = desempates;
        this.criterios = criterios;
        this.torneos = torneos;
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
        return toResponse(creado);
    }

    @Transactional
    public ReglamentoResponseDto update(Long torneoId, ReglamentoRequestDto request) {
        Reglamento entity = require(torneoId);
        apply(entity, request, entity.getTorneo());
        validate(entity);
        Reglamento guardado = repository.saveAndFlush(entity);
        aplicarDesempates(guardado, request.desempates());
        return toResponse(guardado);
    }

    @Transactional
    public void delete(Long torneoId) {
        Reglamento entity = require(torneoId);
        desempates.deleteByReglamentoId(entity.getId());
        desempates.flush();
        repository.delete(entity);
        repository.flush();
    }

    public List<ReglamentoDesempateResponseDto> findDesempates(Long torneoId) {
        return desempates.findByReglamentoIdOrderByOrdenAsc(require(torneoId).getId()).stream()
                .map(this::toDesempateResponse)
                .toList();
    }

    /** Tarea S1-13: reemplaza el orden completo de los criterios de desempate. */
    @Transactional
    public List<ReglamentoDesempateResponseDto> reemplazarDesempates(Long torneoId, List<String> codigos) {
        Reglamento reglamento = require(torneoId);
        aplicarDesempates(reglamento, codigos);
        return desempates.findByReglamentoIdOrderByOrdenAsc(reglamento.getId()).stream()
                .map(this::toDesempateResponse)
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

    private Short orDefault(Short value, int fallback) {
        return value != null ? value : (short) fallback;
    }
}
