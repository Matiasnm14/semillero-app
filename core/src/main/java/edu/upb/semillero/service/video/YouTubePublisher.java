package edu.upb.semillero.service.video;

import edu.upb.semillero.repository.enums.EstadoYoutube;
import java.nio.file.Path;

public interface YouTubePublisher {
    record Publication(Path original, Path receipt, String title) {}
    record Result(EstadoYoutube state, String videoId) {}
    Result publish(Publication publication);
}
