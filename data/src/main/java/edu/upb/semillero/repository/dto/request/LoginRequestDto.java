package edu.upb.semillero.repository.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequestDto(
        @NotBlank @Size(max = 255) String identificador,
        @NotBlank @Size(min = 8, max = 128) String password
) {}
