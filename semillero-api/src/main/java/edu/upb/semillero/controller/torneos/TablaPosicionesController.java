package edu.upb.semillero.controller.torneos;

import edu.upb.semillero.repository.dto.response.torneos.TablaPosicionesResponseDto;
import edu.upb.semillero.service.torneos.TablaPosicionesService;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HU 11 - Tabla de posiciones (tareas S3-12 a S3-14). La lee cualquier usuario
 * con sesión: entrenador, jugador y familia.
 */
@RestController
public class TablaPosicionesController {
    private final TablaPosicionesService service;

    public TablaPosicionesController(TablaPosicionesService service) {
        this.service = service;
    }

    /** Una tabla en liga, una por grupo en el formato de grupos; {@code grupoId} filtra un grupo. */
    @GetMapping("/api/v1/torneos/{torneoId}/categorias/{categoriaId}/tabla")
    public List<TablaPosicionesResponseDto> tablas(@PathVariable @Positive Long torneoId,
            @PathVariable @Positive Long categoriaId,
            @RequestParam(required = false) @Positive Long grupoId) {
        return service.tablas(torneoId, categoriaId, grupoId);
    }
}
