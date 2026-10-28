package edu.upb.semillero.controller.torneos;

import edu.upb.semillero.repository.dto.request.torneos.CierrePartidoRequestDto;
import edu.upb.semillero.repository.dto.request.torneos.EventoPartidoRequestDto;
import edu.upb.semillero.repository.dto.request.torneos.ReaperturaPartidoRequestDto;
import edu.upb.semillero.repository.dto.response.torneos.AuditoriaResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.EventoPartidoResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.ResultadoPartidoResponseDto;
import edu.upb.semillero.service.torneos.CierrePartidoService;
import edu.upb.semillero.service.torneos.EventoPartidoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * HU 10 - El partido en juego: eventos (tareas S3-06 y S3-07), cierre (S3-08 y
 * S3-09) y reapertura (S3-10). Los eventos y el cierre los carga la mesa de
 * control asignada; reabrir es solo del organizador.
 */
@RestController
@RequestMapping("/api/v1/torneos/partidos/{partidoId}")
public class PartidoTorneoController {
    private final EventoPartidoService eventos;
    private final CierrePartidoService cierre;

    public PartidoTorneoController(EventoPartidoService eventos, CierrePartidoService cierre) {
        this.eventos = eventos;
        this.cierre = cierre;
    }

    @GetMapping("/eventos")
    public List<EventoPartidoResponseDto> eventos(@PathVariable @Positive Long partidoId) {
        return eventos.findByPartido(partidoId);
    }

    /** Tareas S3-06 y S3-07: gol, tarjeta o cambio. */
    @PostMapping("/eventos")
    public ResponseEntity<EventoPartidoResponseDto> registrar(@PathVariable @Positive Long partidoId,
            @Valid @RequestBody EventoPartidoRequestDto request) {
        EventoPartidoResponseDto response = eventos.registrar(partidoId, request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}").buildAndExpand(response.id()).toUri())
                .body(response);
    }

    /** Corregir un evento mal cargado, mientras el partido no se cerró. */
    @DeleteMapping("/eventos/{id}")
    public ResponseEntity<Void> borrarEvento(@PathVariable @Positive Long partidoId,
            @PathVariable @Positive Long id) {
        eventos.delete(partidoId, id);
        return ResponseEntity.noContent().build();
    }

    /** El resultado y los minutos de cada jugador. */
    @GetMapping("/resultado")
    public ResultadoPartidoResponseDto resultado(@PathVariable @Positive Long partidoId) {
        return cierre.resultado(partidoId);
    }

    /** Tareas S3-08 y S3-09: cerrar el partido en una sola transacción. */
    @PostMapping("/cerrar")
    public ResultadoPartidoResponseDto cerrar(@PathVariable @Positive Long partidoId,
            @Valid @RequestBody CierrePartidoRequestDto request) {
        return cierre.cerrar(partidoId, request);
    }

    /** Tarea S3-10: reabrir un partido cerrado; solo el organizador. */
    @PostMapping("/reabrir")
    public ResultadoPartidoResponseDto reabrir(@PathVariable @Positive Long partidoId,
            @Valid @RequestBody ReaperturaPartidoRequestDto request) {
        return cierre.reabrir(partidoId, request);
    }

    /** Tarea S3-10: los cierres y reaperturas del partido. */
    @GetMapping("/historial")
    public List<AuditoriaResponseDto> historial(@PathVariable @Positive Long partidoId) {
        return cierre.historial(partidoId);
    }
}
