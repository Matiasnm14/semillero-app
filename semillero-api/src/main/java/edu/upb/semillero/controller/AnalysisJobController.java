package edu.upb.semillero.controller;

import edu.upb.semillero.repository.dto.response.AnalysisJobResponseDto;
import edu.upb.semillero.service.AnalysisJobService;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analisis")
public class AnalysisJobController {
    private final AnalysisJobService service;

    public AnalysisJobController(AnalysisJobService service) { this.service = service; }

    @GetMapping("/{id}")
    public AnalysisJobResponseDto findById(@PathVariable @Positive Long id) {
        return service.findById(id);
    }
}
