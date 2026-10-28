package edu.upb.semillero.controller.torneos;

import edu.upb.semillero.repository.dto.request.torneos.PlanillaRequestDto;
import edu.upb.semillero.repository.dto.response.torneos.PlanillaResponseDto;
import edu.upb.semillero.service.torneos.PdfTorneosService;
import edu.upb.semillero.service.torneos.PlanillaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HU 9 - Planilla del partido (tareas S3-01 a S3-04). La llena la mesa de
 * control asignada al partido, y también el organizador.
 */
@RestController
@RequestMapping("/api/v1/torneos/partidos/{partidoId}/planilla")
public class PlanillaController {
    private final PlanillaService service;
    private final PdfTorneosService pdfs;

    public PlanillaController(PlanillaService service, PdfTorneosService pdfs) {
        this.service = service;
        this.pdfs = pdfs;
    }

    /** Tarea S3-01: la planilla precargada con el partido y las dos listas de buena fe. */
    @GetMapping
    public PlanillaResponseDto precarga(@PathVariable @Positive Long partidoId) {
        return service.precarga(partidoId);
    }

    /** Tareas S3-02 y S3-03: guardar, aunque esté incompleta. */
    @PutMapping
    public PlanillaResponseDto guardar(@PathVariable @Positive Long partidoId,
            @Valid @RequestBody PlanillaRequestDto request) {
        return service.guardar(partidoId, request);
    }

    /** Tarea S3-03: confirmar, con titulares y capitán en cada equipo. */
    @PostMapping("/confirmar")
    public PlanillaResponseDto confirmar(@PathVariable @Positive Long partidoId,
            @RequestParam @Positive Long usuarioId) {
        return service.confirmar(partidoId, usuarioId);
    }

    /** Tarea S3-04: la planilla en PDF. */
    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable @Positive Long partidoId) {
        byte[] pdf = pdfs.planilla(service.precarga(partidoId));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename("planilla-" + partidoId + ".pdf").toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
