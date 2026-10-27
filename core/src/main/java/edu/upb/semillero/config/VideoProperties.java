package edu.upb.semillero.config;

import java.nio.file.Path;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "video")
public class VideoProperties {
    private Path storageDir = Path.of(".local/videos");
    private long maxBytes = 2L * 1024 * 1024 * 1024;
    private String ffprobe = "ffprobe";
    private String ffmpeg = "ffmpeg";
    private Duration validationTimeout = Duration.ofHours(1);
    private Youtube youtube = new Youtube();

    @Getter
    @Setter
    public static class Youtube {
        private boolean enabled;
        private boolean workerEnabled = true;
        private String python = ".local/youtube/venv/bin/python";
        private Path script = Path.of("scripts/youtube_upload.py");
        private Path client = Path.of(".local/youtube/client_secret.json");
        private Path tokens = Path.of(".local/youtube/tokens.json");
        private String channel = "Semillero-Video-Analysis";
        private Duration timeout = Duration.ofHours(1);
    }
}
