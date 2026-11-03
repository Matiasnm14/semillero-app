package edu.upb.semillero.controller.scouting;

import edu.upb.semillero.controller.dto.PageResponse;
import edu.upb.semillero.repository.dto.request.scouting.*;
import edu.upb.semillero.repository.dto.response.scouting.*;
import edu.upb.semillero.service.scouting.ConvocatoriaPruebaService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/scouting/pruebas")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Scouting - pruebas deportivas")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "scoutingBearer")
public class ConvocatoriaPruebaController {
    private final ConvocatoriaPruebaService service;

    @GetMapping
    public PageResponse<ConvocatoriaPruebaVista> listar(
            @RequestParam(required = false) @Positive Long clubId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(service.listar(clubId, page, size));
    }

    @GetMapping("/{id}")
    public ConvocatoriaPruebaVista obtener(@PathVariable @Positive Long id) {
        return service.obtener(id);
    }

    @PostMapping
    public ResponseEntity<ConvocatoriaPruebaVista> crear(
            @Valid @RequestBody ConvocatoriaPruebaDto d) {
        var c = service.crear(d);
        return ResponseEntity.created(URI.create("/api/v1/scouting/pruebas/" + c.id())).body(c);
    }

    @PutMapping("/{id}")
    public ConvocatoriaPruebaVista actualizar(
            @PathVariable @Positive Long id, @Valid @RequestBody ConvocatoriaPruebaDto d) {
        return service.actualizar(id, d);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelar(
            @PathVariable @Positive Long id, @RequestParam @PositiveOrZero Long version) {
        service.cancelar(id, version);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/inscripciones")
    public InscripcionPruebaVista inscribir(
            @PathVariable @Positive Long id,
            @Valid @RequestBody(required = false) InscripcionPruebaDto d) {
        return service.inscribir(id, d);
    }

    @GetMapping("/{id}/inscripciones")
    public PageResponse<InscripcionPruebaVista> inscritos(
            @PathVariable @Positive Long id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(service.inscritos(id, page, size));
    }

    @DeleteMapping("/{id}/inscripciones/{inscripcion}")
    public ResponseEntity<Void> retirar(
            @PathVariable @Positive Long id,
            @PathVariable UUID inscripcion,
            @RequestParam @PositiveOrZero Long version) {
        service.retirar(id, inscripcion, version);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/inscripciones/{inscripcion}")
    public InscripcionPruebaVista decision(
            @PathVariable @Positive Long id,
            @PathVariable UUID inscripcion,
            @Valid @RequestBody DecisionPruebaDto d) {
        return service.decision(id, inscripcion, d);
    }

    @PostMapping("/{id}/inscripciones/{inscripcion}/evaluaciones")
    public InscripcionPruebaVista evaluar(
            @PathVariable @Positive Long id,
            @PathVariable UUID inscripcion,
            @Valid @RequestBody EvaluacionPruebaDto d) {
        return service.evaluar(id, inscripcion, d);
    }

    @GetMapping("/{id}/inscripciones/{inscripcion}/evaluaciones")
    public PageResponse<ConvocatoriaPruebaService.EvaluacionVista> historial(
            @PathVariable @Positive Long id,
            @PathVariable UUID inscripcion,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(service.historial(id, inscripcion, page, size));
    }
}
