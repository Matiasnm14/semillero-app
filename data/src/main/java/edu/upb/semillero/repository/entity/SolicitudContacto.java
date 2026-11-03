package edu.upb.semillero.repository.entity;

import edu.upb.semillero.repository.enums.EstadoSolicitudContacto;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "solicitudes_contacto", indexes = {
        @Index(name = "idx_solicitudes_contacto_club_fecha", columnList = "club_id, fecha_envio"),
        @Index(name = "idx_solicitudes_contacto_jugador_fecha", columnList = "jugador_id, fecha_envio")
})
public class SolicitudContacto extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jugador_id", nullable = false)
    private Jugador jugador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_club_id")
    private UsuarioClub usuarioClub;

    @Column(name = "asunto", length = 120, nullable = false)
    private String asunto;

    @Column(name = "mensaje", length = 500, nullable = false)
    private String mensaje;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 20, nullable = false)
    private EstadoSolicitudContacto estado = EstadoSolicitudContacto.PENDIENTE;

    @Column(name = "fecha_envio", nullable = false)
    private LocalDateTime fechaEnvio;

    @Column(name = "fecha_respuesta")
    private LocalDateTime fechaRespuesta;

    @Column(name = "respuesta", length = 500)
    private String respuesta;
}
