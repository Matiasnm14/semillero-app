package edu.upb.semillero.repository.dto.response;

public record ParticipacionPartidoResponseDto(
        Long id,
        Long partidoId,
        Long jugadorId,
        Long clubId,
        Boolean esTitular,
        Short dorsal,
        Short minutoEntrada,
        Short minutoSalida,
        Double velocidadProm,
        Double distanciaRecorrida
) {}
