package edu.upb.semillero.repository.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ClubRequestDto(
        @Size(max = 15) String nombreClub,
        @Positive Long ciudadId
) {}
