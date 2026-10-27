package edu.upb.semillero.repository.dto.response;

import edu.upb.semillero.repository.enums.EstadoAnalisis;
import java.time.Instant;

public record AnalysisJobResponseDto(
        Long id, Long partidoId, Long videoId, EstadoAnalisis estado,
        Double porcentaje, Long fotogramasProcesados, Long fotogramasTotales,
        Instant fechaSolicitud, Instant fechaInicio, Instant fechaFin, Instant fechaActualizacion,
        boolean resultadosDisponibles, boolean respuestaRecibida, String codigoError, String mensajeError) {
}
