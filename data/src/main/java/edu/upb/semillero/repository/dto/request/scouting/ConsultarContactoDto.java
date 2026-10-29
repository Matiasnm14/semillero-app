package edu.upb.semillero.repository.dto.request.scouting;

import jakarta.validation.constraints.*;
public record ConsultarContactoDto(@NotBlank @Size(max = 300) String proposito) {}
