package edu.upb.semillero.controller.torneos;

import edu.upb.semillero.controller.dto.PageResponse;
import edu.upb.semillero.repository.dto.request.torneos.ListaBuenaFeRequestDto;
import edu.upb.semillero.repository.dto.response.torneos.ListaBuenaFeConfirmacionResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.ListaBuenaFeResponseDto;
import edu.upb.semillero.service.torneos.ListaBuenaFeService;
import edu.upb.semillero.service.torneos.PdfTorneosService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * HU 5 - Lista de buena fe (tareas S2-06, S2-07 y S2-08). Cuelga de la
 * inscripción porque el equipo es el club inscrito en una categoría.
 */
@RestController
@RequestMapping("/api/v1/torneos/inscripciones/{inscripcionEquipoId}/lista-buena-fe")
public class ListaBuenaFeController {
    private final ListaBuenaFeService service;
    private final PdfTorneosService pdfs;

    public ListaBuenaFeController(ListaBuenaFeService service, PdfTorneosService pdfs) {
        this.service = service;
        this.pdfs = pdfs;
    }

    @GetMapping
    public PageResponse<ListaBuenaFeResponseDto> findAll(
            @PathVariable @Positive Long inscripcionEquipoId,
            @PageableDefault(size = 30, sort = "dorsal") Pageable pageable) {
        return PageResponse.from(service.findByInscripcion(inscripcionEquipoId, pageable));
    }

    @GetMapping("/{id}")
    public ListaBuenaFeResponseDto findById(@PathVariable @Positive Long inscripcionEquipoId,
            @PathVariable @Positive Long id) {
        return service.findById(inscripcionEquipoId, id);
    }

    @PostMapping
    public ResponseEntity<ListaBuenaFeResponseDto> create(
            @PathVariable @Positive Long inscripcionEquipoId,
            @Valid @RequestBody ListaBuenaFeRequestDto request) {
        ListaBuenaFeResponseDto response = service.create(inscripcionEquipoId, request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}").buildAndExpand(response.id()).toUri())
                .body(response);
    }

    @PutMapping("/{id}")
    public ListaBuenaFeResponseDto update(@PathVariable @Positive Long inscripcionEquipoId,
            @PathVariable @Positive Long id, @Valid @RequestBody ListaBuenaFeRequestDto request) {
        return service.update(inscripcionEquipoId, id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long inscripcionEquipoId,
            @PathVariable @Positive Long id) {
        service.delete(inscripcionEquipoId, id);
        return ResponseEntity.noContent().build();
    }

    /** Tarea S2-08: confirma la lista si cumple el mínimo y el máximo del reglamento. */
    @PostMapping("/confirmar")
    public ListaBuenaFeConfirmacionResponseDto confirmar(
            @PathVariable @Positive Long inscripcionEquipoId) {
        return service.confirmar(inscripcionEquipoId);
    }

    /** Tarea S2-08: la lista en PDF, para presentarla en la mesa de control. */
    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable @Positive Long inscripcionEquipoId) {
        byte[] pdf = pdfs.listaBuenaFe(inscripcionEquipoId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename("lista-buena-fe-" + inscripcionEquipoId + ".pdf").toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
