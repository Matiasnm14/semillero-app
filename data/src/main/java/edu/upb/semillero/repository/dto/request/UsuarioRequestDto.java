package edu.upb.semillero.repository.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UsuarioRequestDto(
        @Size(max = 255) String name,
        @Size(max = 255) @Email String email,
        @Size(max = 20) String telefonoUsuario,
        @Size(max = 20) String docIdentidadUsuario,
        @NotBlank @Size(min = 8, max = 128) String password,
        @Size(max = 35) String nombres,
        @Size(max = 35) String apellidos
) {}
