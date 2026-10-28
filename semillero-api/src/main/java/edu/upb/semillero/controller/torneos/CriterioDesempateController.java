package edu.upb.semillero.controller.torneos;

import edu.upb.semillero.controller.dto.PageResponse;
import edu.upb.semillero.repository.dto.response.torneos.CriterioDesempateResponseDto;
import edu.upb.semillero.service.torneos.CriterioDesempateService;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catálogo de criterios de desempate, de solo lectura (tarea S1-13).
 */
@RestController
@RequestMapping("/api/v1/torneos/criterios-desempate")
public class CriterioDesempateController {
    private final CriterioDesempateService service;

    public CriterioDesempateController(CriterioDesempateService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<CriterioDesempateResponseDto> findAll(
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return PageResponse.from(service.findAll(pageable));
    }

    @GetMapping("/{id}")
    public CriterioDesempateResponseDto findById(@PathVariable @Positive Long id) {
        return service.findById(id);
    }
}
