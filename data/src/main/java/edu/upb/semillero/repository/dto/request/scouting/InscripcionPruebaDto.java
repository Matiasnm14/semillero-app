package edu.upb.semillero.repository.dto.request.scouting;

import jakarta.validation.constraints.Size;

public record InscripcionPruebaDto(@Size(max = 500) String nota) {}
