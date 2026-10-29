package edu.upb.semillero.repository.dto.request.scouting;

import edu.upb.semillero.repository.enums.scouting.EstadoContacto;
import jakarta.validation.constraints.*;
public record ResolverContactoDto(@NotNull EstadoContacto estado, @NotNull @PositiveOrZero Long version,
        @NotBlank @Size(max = 500) String motivo) {}
