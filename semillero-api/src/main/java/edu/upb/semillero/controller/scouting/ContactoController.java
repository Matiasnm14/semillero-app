package edu.upb.semillero.controller.scouting;

import edu.upb.semillero.controller.dto.PageResponse;
import edu.upb.semillero.repository.dto.request.scouting.*;
import edu.upb.semillero.repository.dto.response.scouting.*;
import edu.upb.semillero.service.scouting.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

@RestController @RequestMapping("/api/v1/scouting") @RequiredArgsConstructor
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "scoutingBearer")
@Tag(name = "Scouting - contactos", description = "Solicitudes privadas, preferencias e historial")
public class ContactoController {
    private final DatosContactoService datos;
    private final SolicitudContactoService solicitudes;
    private final PreferenciaContactoService preferencias;
    private final JugadorGuardadoService guardados;
    @GetMapping("/mi-perfil/preferencias")
    public PreferenciaActualDto preferencias() { return preferencias.obtener(); }
    @PutMapping("/mi-perfil/preferencias")
    public PreferenciaActualDto preferencias(@Valid @RequestBody PreferenciaContactoDto input) { return preferencias.actualizar(input); }
    @PostMapping("/solicitudes")
    public ResponseEntity<SolicitudContactoDto> crear(@RequestHeader("Idempotency-Key") UUID clave,
            @Valid @RequestBody CrearContactoDto input) {
        var creada = solicitudes.crear(clave, input);
        return creada.nueva() ? ResponseEntity.created(URI.create("/api/v1/scouting/solicitudes/" + creada.solicitud().id()))
                .body(creada.solicitud()) : ResponseEntity.ok(creada.solicitud());
    }
    @GetMapping("/solicitudes")
    public PageResponse<SolicitudContactoDto> bandeja(@RequestParam(required = false) edu.upb.semillero.repository.enums.scouting.EstadoContacto estado,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) { return PageResponse.from(solicitudes.bandeja(estado, page, size)); }
    @GetMapping("/solicitudes/{id}")
    public SolicitudContactoDto obtener(@PathVariable UUID id) { return solicitudes.obtener(id); }
    @PostMapping("/solicitudes/{id}/respuesta")
    public SolicitudContactoDto responder(@PathVariable UUID id, @Valid @RequestBody ResolverContactoDto input) { return solicitudes.resolver(id, input); }
    @PostMapping("/solicitudes/{id}/datos-contacto")
    public ResponseEntity<DatosContactoDto> datos(@PathVariable UUID id, @Valid @RequestBody ConsultarContactoDto input) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(datos.consultar(id, input.proposito()));
    }
    @GetMapping("/solicitudes/{id}/historial")
    public PageResponse<EventoContactoDto> historial(@PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(solicitudes.historial(id, page, size));
    }
    @PutMapping("/clubes/{clubId}/guardados/{jugadorId}")
    public GuardadoDto guardar(@PathVariable @Positive Long clubId, @PathVariable @Positive Long jugadorId) { return guardados.guardar(clubId, jugadorId); }
    @GetMapping("/clubes/{clubId}/guardados")
    public PageResponse<GuardadoDto> guardados(@PathVariable @Positive Long clubId,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(guardados.listar(clubId, page, size));
    }
    @DeleteMapping("/clubes/{clubId}/guardados/{jugadorId}")
    public ResponseEntity<Void> quitar(@PathVariable @Positive Long clubId, @PathVariable @Positive Long jugadorId) {
        guardados.quitar(clubId, jugadorId); return ResponseEntity.noContent().build();
    }
}
