package edu.upb.semillero.repository.dto.response;

public record UsuarioTrabajaResponseDto(
        Long id,
        Long usuarioClubId,
        String tipoUsuarioClub,
        Long clubId
) {}
