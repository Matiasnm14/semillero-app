package edu.upb.semillero.repository.dto.response;

public record UsuarioResponseDto(
        Long id,
        String name,
        String email,
        String telefonoUsuario,
        String docIdentidadUsuario,
        String nombres,
        String apellidos
) {}
