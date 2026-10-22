package edu.upb.semillero.controller;

import edu.upb.semillero.controller.dto.PageResponse;
import edu.upb.semillero.repository.dto.response.AnalysisJobResponseDto;
import edu.upb.semillero.service.AnalysisJobService;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/partidos/{partidoId}/videos/{videoId}/analisis")
public class VideoAnalysisController {
    private final AnalysisJobService service;

    public VideoAnalysisController(AnalysisJobService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<AnalysisJobResponseDto> create(@PathVariable @Positive Long partidoId,
            @PathVariable @Positive Long videoId) {
        var response = service.create(partidoId, videoId);
        var location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/analisis/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.accepted().location(location).body(response);
    }

    @GetMapping
    public PageResponse<AnalysisJobResponseDto> findAll(@PathVariable @Positive Long partidoId,
            @PathVariable @Positive Long videoId,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(service.findAll(partidoId, videoId, pageable));
    }
}
