package edu.upb.semillero.repository.dto.response;

public record LoginResponseDto(
        Long id,
        String name,
        String nombres,
        String apellidos,
        String email,
        String rol,
        Long jugadorId,
        Long clubId,
        String nombreClub,
        String fotoPerfil) {}
