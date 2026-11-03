package edu.upb.semillero.controller.torneos;

import edu.upb.semillero.repository.dto.request.torneos.ApelacionRequestDto;
import edu.upb.semillero.repository.dto.request.torneos.ResolucionApelacionRequestDto;
import edu.upb.semillero.repository.dto.request.torneos.SancionManualRequestDto;
import edu.upb.semillero.repository.dto.response.torneos.ApelacionResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.AuditoriaResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.SancionResponseDto;
import edu.upb.semillero.repository.enums.torneos.EstadoSancion;
import edu.upb.semillero.service.torneos.SancionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * HU 13 - Régimen disciplinario (tareas S4-04 y S4-05). Las sanciones
 * automáticas no tienen endpoint propio: las genera el cierre del partido.
 */
@Tag(name = "Torneos - HU 13", description = "Sanciones, sancionados por fecha, sanción manual y apelaciones")
@RestController
public class SancionController {
    private static final String TORNEOS = "/api/v1/torneos";

    private final SancionService service;

    public SancionController(SancionService service) {
        this.service = service;
    }

    /** Tarea S4-04: las sanciones del torneo, filtradas por estado y por categoría. */
    @GetMapping(TORNEOS + "/{torneoId}/sanciones")
    public List<SancionResponseDto> delTorneo(@PathVariable @Positive Long torneoId,
            @RequestParam(required = false) EstadoSancion estado,
            @RequestParam(required = false) @Positive Long categoriaId) {
        return service.delTorneo(torneoId, estado, categoriaId);
    }

    /** Tarea S4-04: los que no pueden jugar la fecha {@code jornada} de la categoría. */
    @GetMapping(TORNEOS + "/{torneoId}/categorias/{categoriaId}/sancionados")
    public List<SancionResponseDto> sancionadosDeLaFecha(@PathVariable @Positive Long torneoId,
            @PathVariable @Positive Long categoriaId, @RequestParam @Positive Short jornada) {
        return service.sancionadosDeLaFecha(torneoId, categoriaId, jornada);
    }

    /** Tarea S4-05: sanción manual, con motivo; solo el organizador. */
    @PostMapping(TORNEOS + "/{torneoId}/sanciones")
    public ResponseEntity<SancionResponseDto> crearManual(@PathVariable @Positive Long torneoId,
            @Valid @RequestBody SancionManualRequestDto request) {
        SancionResponseDto response = service.crearManual(torneoId, request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path(TORNEOS + "/sanciones/{id}").buildAndExpand(response.id()).toUri())
                .body(response);
    }

    @GetMapping(TORNEOS + "/sanciones/{id}")
    public SancionResponseDto findById(@PathVariable @Positive Long id) {
        return service.findById(id);
    }

    /** Alta, fechas descontadas, apelaciones y anulación de la sanción. */
    @GetMapping(TORNEOS + "/sanciones/{id}/historial")
    public List<AuditoriaResponseDto> historial(@PathVariable @Positive Long id) {
        return service.historial(id);
    }

    @GetMapping(TORNEOS + "/sanciones/{id}/apelaciones")
    public List<ApelacionResponseDto> apelaciones(@PathVariable @Positive Long id) {
        return service.apelacionesDe(id);
    }

    /** Tarea S4-05: el entrenador apela una sanción de su equipo. */
    @PostMapping(TORNEOS + "/sanciones/{id}/apelaciones")
    public ResponseEntity<ApelacionResponseDto> apelar(@PathVariable @Positive Long id,
            @Valid @RequestBody ApelacionRequestDto request) {
        ApelacionResponseDto response = service.apelar(id, request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path(TORNEOS + "/apelaciones/{id}").buildAndExpand(response.id()).toUri())
                .body(response);
    }

    /** Tarea S4-05: el organizador acepta, rechaza o reduce. */
    @PutMapping(TORNEOS + "/apelaciones/{id}/resolucion")
    public ApelacionResponseDto resolver(@PathVariable @Positive Long id,
            @Valid @RequestBody ResolucionApelacionRequestDto request) {
        return service.resolver(id, request);
    }

    /** HU 14 y 15: las sanciones de un jugador; {@code vigentes=true} es el aviso de la app. */
    @GetMapping(TORNEOS + "/jugadores/{jugadorId}/sanciones")
    public List<SancionResponseDto> delJugador(@PathVariable @Positive Long jugadorId,
            @RequestParam(defaultValue = "false") boolean vigentes) {
        return service.delJugador(jugadorId, vigentes);
    }
}
