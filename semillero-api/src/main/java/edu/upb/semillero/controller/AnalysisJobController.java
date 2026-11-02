package edu.upb.semillero.controller;

import edu.upb.semillero.repository.dto.response.AnalysisJobResponseDto;
import edu.upb.semillero.service.AnalysisJobService;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/v1/analisis")
public class AnalysisJobController {
    private final AnalysisJobService service;

    public AnalysisJobController(AnalysisJobService service) { this.service = service; }

    @GetMapping("/{id}")
    public AnalysisJobResponseDto findById(@PathVariable @Positive Long id) {
        return service.findById(id);
    }

    @GetMapping(value = "/{id}/resultados", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> results(@PathVariable @Positive Long id) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.results(id));
    }

    @GetMapping(value = "/{id}/heatmap", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<Resource> heatmap(@PathVariable @Positive Long id) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"analysis-" + id + "-heatmap.png\"")
                .body(new FileSystemResource(service.heatmap(id)));
    }
}
