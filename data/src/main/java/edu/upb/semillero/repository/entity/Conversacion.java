package edu.upb.semillero.repository.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "conversa")
public class Conversacion extends BaseEntity {

    @Column(name = "mensaje", length = 255)
    private String mensaje;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "users_envia_id")
    private Usuario usuarioEnvia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "users_recibe_id")
    private Usuario usuarioRecibe;
}
