package edu.upb.semillero.repository.dto.response;

public record ConversacionResponseDto(
        Long id,
        String mensaje,
        Long usuarioEnviaId,
        Long usuarioRecibeId
) {}
