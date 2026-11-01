package edu.upb.semillero.repository.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
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

    @Column(name = "link_video", columnDefinition = "text")
    private String linkVideo;

    @Column(name = "youtube_video_id", length = 11)
    private String youtubeVideoId;

    @Column(nullable = false)
    private boolean visible = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jugadores_id")
    private Jugador jugador;

    @PrePersist
    void initializeCreatedAt() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
