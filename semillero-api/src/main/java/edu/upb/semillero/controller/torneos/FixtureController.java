package edu.upb.semillero.controller.torneos;

import edu.upb.semillero.repository.dto.request.torneos.AjustePartidoRequestDto;
import edu.upb.semillero.repository.dto.request.torneos.GenerarFixtureRequestDto;
import edu.upb.semillero.repository.dto.request.torneos.PartidoManualRequestDto;
import edu.upb.semillero.repository.dto.response.torneos.FixturePublicadoResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.PartidoFixtureResponseDto;
import edu.upb.semillero.service.torneos.FixtureService;
import edu.upb.semillero.service.torneos.PdfTorneosService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HU 7 - Fixture (tareas S2-14 a S2-18). El fixture es por categoría, porque
 * cada categoría tiene el suyo; publicar es por torneo.
 */
@Tag(name = "Torneos - HU 7", description = "Generar, ajustar, exportar y publicar el fixture")
@RestController
public class FixtureController {
    private static final String FIXTURE = "/api/v1/torneos/{torneoId}/categorias/{categoriaId}/fixture";

    private final FixtureService service;
    private final PdfTorneosService pdfs;

    public FixtureController(FixtureService service, PdfTorneosService pdfs) {
        this.service = service;
        this.pdfs = pdfs;
    }

    /** Tarea S2-18: el fixture en PDF, para publicarlo y repartirlo. */
    @GetMapping(value = FIXTURE + "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable @Positive Long torneoId,
            @PathVariable @Positive Long categoriaId) {
        byte[] pdf = pdfs.fixture(torneoId, categoriaId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename("fixture-" + torneoId + "-" + categoriaId + ".pdf").toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping(FIXTURE)
    public List<PartidoFixtureResponseDto> findByCategoria(@PathVariable @Positive Long torneoId,
            @PathVariable @Positive Long categoriaId) {
        return service.findByCategoria(torneoId, categoriaId);
    }

    /** Tarea S2-14: liga por rotación, o una rotación por grupo. */
    @PostMapping(FIXTURE + "/liga")
    public ResponseEntity<List<PartidoFixtureResponseDto>> generarLiga(
            @PathVariable @Positive Long torneoId, @PathVariable @Positive Long categoriaId,
            @Valid @RequestBody GenerarFixtureRequestDto request) {
        return ResponseEntity.status(201).body(service.generarLiga(torneoId, categoriaId, request));
    }

    /** Tarea S2-15: llaves de eliminación directa. */
    @PostMapping(FIXTURE + "/eliminacion")
    public ResponseEntity<List<PartidoFixtureResponseDto>> generarEliminacion(
            @PathVariable @Positive Long torneoId, @PathVariable @Positive Long categoriaId,
            @Valid @RequestBody GenerarFixtureRequestDto request) {
        return ResponseEntity.status(201)
                .body(service.generarEliminacion(torneoId, categoriaId, request));
    }

    /** Tarea S2-15: la ronda siguiente, con los ganadores de la anterior. */
    @PostMapping(FIXTURE + "/avanzar")
    public ResponseEntity<List<PartidoFixtureResponseDto>> avanzarRonda(
            @PathVariable @Positive Long torneoId, @PathVariable @Positive Long categoriaId,
            @Valid @RequestBody GenerarFixtureRequestDto request) {
        return ResponseEntity.status(201).body(service.avanzarRonda(torneoId, categoriaId, request));
    }

    /** Tarea S2-17: carga manual, la única forma de cargar un campeonato externo. */
    @PostMapping(FIXTURE + "/partidos")
    public ResponseEntity<PartidoFixtureResponseDto> cargarManual(
            @PathVariable @Positive Long torneoId, @PathVariable @Positive Long categoriaId,
            @Valid @RequestBody PartidoManualRequestDto request) {
        return ResponseEntity.status(201).body(service.cargarManual(torneoId, categoriaId, request));
    }

    @DeleteMapping(FIXTURE)
    public ResponseEntity<Void> borrar(@PathVariable @Positive Long torneoId,
            @PathVariable @Positive Long categoriaId, @RequestParam @Positive Long usuarioId) {
        service.borrar(torneoId, categoriaId, usuarioId);
        return ResponseEntity.noContent().build();
    }

    /** Tarea S2-17: ajuste manual de un partido ya generado. */
    @PutMapping("/api/v1/torneos/partidos/{partidoId}")
    public PartidoFixtureResponseDto ajustar(@PathVariable @Positive Long partidoId,
            @Valid @RequestBody AjustePartidoRequestDto request) {
        return service.ajustar(partidoId, request);
    }

    /** Tarea S2-18: publicar el fixture del torneo. */
    @PostMapping("/api/v1/torneos/{torneoId}/fixture/publicar")
    public FixturePublicadoResponseDto publicar(@PathVariable @Positive Long torneoId,
            @RequestParam @Positive Long usuarioId) {
        return service.publicar(torneoId, usuarioId);
    }
}
