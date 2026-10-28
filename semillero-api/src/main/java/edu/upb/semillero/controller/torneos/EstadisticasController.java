package edu.upb.semillero.controller.torneos;

import edu.upb.semillero.repository.dto.response.torneos.EstadisticasCategoriaResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.EstadisticasJugadorResponseDto;
import edu.upb.semillero.service.torneos.EstadisticasService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HU 12 - Goleadores y estadísticas (tareas S4-11 y S4-12). Las lee cualquiera con sesión. */
@Tag(name = "Torneos - HU 12", description = "Goleadores, asistencias, vallas, fair play y resumen por jugador")
@RestController
public class EstadisticasController {
    private final EstadisticasService service;

    public EstadisticasController(EstadisticasService service) {
        this.service = service;
    }

    /** Tarea S4-11: goleadores, asistencias, vallas menos vencidas y fair play de la categoría. */
    @GetMapping("/api/v1/torneos/{torneoId}/categorias/{categoriaId}/estadisticas")
    public EstadisticasCategoriaResponseDto deCategoria(@PathVariable @Positive Long torneoId,
            @PathVariable @Positive Long categoriaId,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limite) {
        return service.deCategoria(torneoId, categoriaId, limite);
    }

    /** Tarea S4-12: el resumen de un jugador en el torneo. */
    @GetMapping("/api/v1/torneos/{torneoId}/jugadores/{jugadorId}/estadisticas")
    public EstadisticasJugadorResponseDto deJugador(@PathVariable @Positive Long torneoId,
            @PathVariable @Positive Long jugadorId) {
        return service.deJugador(torneoId, jugadorId);
    }
}
