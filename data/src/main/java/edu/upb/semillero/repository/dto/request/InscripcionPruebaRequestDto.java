package edu.upb.semillero.repository.dto.request;

import jakarta.validation.constraints.Size;

public record InscripcionPruebaRequestDto(@Size(max = 500) String nota) {}
