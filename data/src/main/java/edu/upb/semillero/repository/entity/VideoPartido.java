package edu.upb.semillero.repository.entity;

import edu.upb.semillero.repository.enums.EstadoYoutube;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "videos_partido", indexes = {
        @Index(name = "idx_video_partido_partido", columnList = "partido_id, id"),
        @Index(name = "idx_video_partido_cola", columnList = "estado_youtube, proximo_intento")
}, uniqueConstraints = @UniqueConstraint(name = "uk_video_partido_storage", columnNames = "storage_id"))
public class VideoPartido extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partido_id", nullable = false)
    private Partido partido;

    @Column(name = "storage_id", nullable = false, length = 36)
    private String storageId;
    @Column(name = "nombre_original", nullable = false, length = 255)
    private String nombreOriginal;
    @Column(nullable = false, length = 10)
    private String formato;
    @Column(name = "tamano_bytes", nullable = false)
    private Long tamanoBytes;
    @Column(name = "duracion_segundos")
    private Double duracionSegundos;
    @Column(nullable = false, length = 64)
    private String sha256;
    @Column(name = "fecha_carga", nullable = false)
    private Instant fechaCarga;
    @Column(name = "titulo_youtube", nullable = false, length = 100)
    private String tituloYoutube;
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_youtube", nullable = false, length = 24)
    private EstadoYoutube estadoYoutube;
    @Column(name = "youtube_id", length = 11)
    private String youtubeId;
    @Column(name = "error_youtube", length = 255)
    private String errorYoutube;
    @Column(name = "proximo_intento")
    private Instant proximoIntento;
    @Column(name = "claim_token", length = 36)
    private String claimToken;
    @Version
    @Column(nullable = false)
    private long revision;
}
