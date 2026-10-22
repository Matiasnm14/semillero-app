package edu.upb.semillero.service.analysis;

import edu.upb.semillero.repository.entity.AnalysisJob;
import edu.upb.semillero.repository.enums.EstadoAnalisis;
import java.util.Objects;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

import static edu.upb.semillero.service.analysis.AnalysisGatewayException.Reason.INVALID_RESPONSE;

@Component
public class AnalysisSnapshotValidator {
    public boolean isNewer(AnalysisGateway.Snapshot snapshot, AnalysisJob job) {
        check(snapshot != null && Objects.equals(snapshot.analysisId(), job.getId()));
        check(snapshot.revision() != null && snapshot.revision() >= 0);
        if (job.getRevisionRemota() != null && snapshot.revision() <= job.getRevisionRemota()) return false;
        check(snapshot.estado() != null && snapshot.resultadosDisponibles() != null);
        check(!(job.getEstado() == EstadoAnalisis.PROCESANDO && snapshot.estado() == EstadoAnalisis.PENDIENTE));
        Double percentage = snapshot.porcentaje();
        Long processed = snapshot.fotogramasProcesados();
        Long total = snapshot.fotogramasTotales();
        check(percentage == null || Double.isFinite(percentage) && percentage >= 0 && percentage <= 100);
        check(processed == null || processed >= 0);
        check(total == null || total > 0);
        check(processed == null || total == null || processed <= total);
        Long storedProcessed = processed == null ? job.getFotogramasProcesados() : processed;
        Long storedTotal = total == null ? job.getFotogramasTotales() : total;
        check(storedProcessed == null || storedTotal == null || storedProcessed <= storedTotal);
        check(percentage == null || job.getPorcentaje() == null || percentage >= job.getPorcentaje());
        check(processed == null || job.getFotogramasProcesados() == null || processed >= job.getFotogramasProcesados());
        check(snapshot.fechaFin() == null || snapshot.fechaInicio() == null
                || !snapshot.fechaFin().isBefore(snapshot.fechaInicio()));
        check(job.getFechaInicio() == null || snapshot.fechaInicio() != null
                && Objects.equals(job.getFechaInicio(), snapshot.fechaInicio().truncatedTo(ChronoUnit.MICROS)));
        check(snapshot.resultadoRef() == null || !snapshot.resultadoRef().isBlank() && snapshot.resultadoRef().length() <= 255);
        if (snapshot.estado() == EstadoAnalisis.PENDIENTE) check(snapshot.fechaInicio() == null);
        if (snapshot.estado() == EstadoAnalisis.PROCESANDO || snapshot.estado() == EstadoAnalisis.COMPLETADO) {
            check(snapshot.fechaInicio() != null);
        }
        check(snapshot.estado().isTerminal() == (snapshot.fechaFin() != null));
        if (snapshot.estado() != EstadoAnalisis.COMPLETADO) {
            check(!snapshot.resultadosDisponibles() && snapshot.resultadoRef() == null);
        } else if (snapshot.resultadosDisponibles()) {
            check(snapshot.resultadoRef() != null);
        }
        return true;
    }

    private void check(boolean condition) {
        if (!condition) throw new AnalysisGatewayException(INVALID_RESPONSE);
    }
}
