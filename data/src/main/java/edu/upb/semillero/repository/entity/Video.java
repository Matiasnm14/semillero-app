package edu.upb.semillero.repository.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "videos")
public class Video extends BaseEntity {

    @Column(name = "titulo_video", length = 100)
    private String tituloVideo;

    @Column(name = "link_video", length = 100)
    private String linkVideo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jugadores_id")
    private Jugador jugador;
}
