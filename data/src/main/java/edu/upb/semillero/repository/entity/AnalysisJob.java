package edu.upb.semillero.repository.entity;

import edu.upb.semillero.repository.enums.EstadoAnalisis;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "analysis_jobs", indexes = {
        @Index(name = "idx_analysis_video", columnList = "video_partido_id, id"),
        @Index(name = "idx_analysis_queue", columnList = "estado, proxima_ejecucion")
})
public class AnalysisJob extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "video_partido_id", nullable = false)
    private VideoPartido video;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EstadoAnalisis estado;
    @Column(name = "fecha_solicitud", nullable = false)
    private Instant fechaSolicitud;
    @Column(name = "fecha_inicio")
    private Instant fechaInicio;
    @Column(name = "fecha_fin")
    private Instant fechaFin;
    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;
    private Double porcentaje;
    @Column(name = "fotogramas_procesados")
    private Long fotogramasProcesados;
    @Column(name = "fotogramas_totales")
    private Long fotogramasTotales;
    @Column(name = "resultados_disponibles", nullable = false)
    private boolean resultadosDisponibles;
    @Column(name = "resultado_ref", length = 255)
    private String resultadoRef;
    @Column(name = "respuesta_json", columnDefinition = "text")
    private String respuestaJson;
    @Column(name = "codigo_error", length = 40)
    private String codigoError;
    @Column(name = "mensaje_error", length = 255)
    private String mensajeError;

    // Persist dispatch intent before the HTTP call. An interrupted wait is not silently resubmitted.
    @Column(nullable = false)
    private boolean enviado;
    // Kept for historical jobs from the former remote-status protocol; unused by Future evaluations.
    @Column(name = "revision_remota")
    private Long revisionRemota;
    @Column(name = "fallos_consecutivos", nullable = false)
    private int fallosConsecutivos;
    @Column(name = "proxima_ejecucion")
    private Instant proximaEjecucion;
    @Column(name = "claim_token", length = 36)
    private String claimToken;
    @Column(name = "lease_until")
    private Instant leaseUntil;
    @Version
    @Column(nullable = false)
    private long revision;
}
