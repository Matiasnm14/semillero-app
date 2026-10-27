package edu.upb.semillero.service.video;

import edu.upb.semillero.config.VideoProperties;
import edu.upb.semillero.service.exception.BusinessException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class FfmpegVideoInspector implements VideoInspector {
    private final VideoProperties properties;
    private final VideoProcessRunner runner;
    private final ObjectMapper json;

    public FfmpegVideoInspector(VideoProperties properties, VideoProcessRunner runner, ObjectMapper json) {
        this.properties = properties;
        this.runner = runner;
        this.json = json;
    }

    @Override
    public Metadata inspect(Path file) {
        var probe = runner.run(List.of(properties.getFfprobe(), "-v", "error", "-protocol_whitelist", "file,pipe",
                "-show_entries", "format=format_name,duration:format_tags=major_brand:stream=codec_type",
                "-of", "json", file.toString()), Duration.ofSeconds(60));
        if (probe.exitCode() != 0) throw invalid();
        Double duration = null;
        try {
            var root = json.readTree(probe.output());
            var format = root.path("format");
            if (!List.of(format.path("format_name").asText("").split(",")).contains("mp4")
                    || format.path("tags").path("major_brand").asText("").strip().equals("qt")) throw invalid();
            boolean video = false;
            for (var stream : root.path("streams")) {
                if (stream.path("codec_type").asText().equals("video")) video = true;
            }
            if (!video) throw invalid();
            String value = format.path("duration").asText("");
            if (!value.isBlank() && !value.equals("N/A")) {
                duration = Double.valueOf(value);
                if (!Double.isFinite(duration) || duration <= 0) throw invalid();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            throw invalid();
        }
        // Probe alone accepts files with corrupt packets. Decode all video/audio before accepting them.
        var decoded = runner.run(List.of(properties.getFfmpeg(), "-nostdin", "-v", "error", "-xerror",
                "-err_detect", "explode", "-protocol_whitelist", "file,pipe", "-i", file.toString(),
                "-map", "0:v:0", "-map", "0:a?", "-f", "null", "-"), properties.getValidationTimeout());
        if (decoded.exitCode() != 0 || !decoded.output().isBlank()) throw invalid();
        return new Metadata("mp4", duration);
    }

    private BusinessException invalid() {
        return new BusinessException("El archivo está corrupto, no contiene video o no es un MP4 compatible");
    }
}
