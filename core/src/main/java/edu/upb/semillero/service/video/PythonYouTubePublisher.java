package edu.upb.semillero.service.video;

import edu.upb.semillero.config.VideoProperties;
import edu.upb.semillero.repository.enums.EstadoYoutube;
import java.nio.file.Files;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Reuses the resumable uploader and the OAuth grant already verified with the channel. */
@Component
public class PythonYouTubePublisher implements YouTubePublisher {
    private final VideoProperties properties;
    private final VideoProcessRunner runner;
    private final ObjectMapper json;

    public PythonYouTubePublisher(VideoProperties properties, VideoProcessRunner runner, ObjectMapper json) {
        this.properties = properties;
        this.runner = runner;
        this.json = json;
    }

    @Override
    public Result publish(Publication publication) {
        var settings = properties.getYoutube();
        var result = runner.run(List.of(settings.getPython(), settings.getScript().toAbsolutePath().toString(),
                "upload", "--non-interactive", "--wait-seconds", "0", "--category", "17",
                "--channel", settings.getChannel(), "--client", settings.getClient().toAbsolutePath().toString(),
                "--tokens", settings.getTokens().toAbsolutePath().toString(),
                "--file", publication.original().toString(), "--title", publication.title(),
                "--record", publication.receipt().toString()), settings.getTimeout());
        JsonNode receipt;
        try {
            receipt = json.readTree(Files.readString(publication.receipt()));
        } catch (Exception e) {
            throw new YouTubePublicationException(false);
        }
        String state = receipt.path("state").asText("");
        if (result.exitCode() != 0) {
            throw new YouTubePublicationException(List.of("REVIEW_REQUIRED", "METADATA_MISMATCH", "FAILED").contains(state));
        }
        String id = receipt.path("videoId").asText("");
        if (!id.matches("[A-Za-z0-9_-]{11}")
                || receipt.path("channelId").asText("").isBlank()
                || !publication.title().equals(receipt.path("actualTitle").asText())
                || !"unlisted".equals(receipt.path("actualPrivacy").asText())
                || !receipt.path("channelId").asText().equals(receipt.path("actualChannelId").asText())) {
            throw new YouTubePublicationException(true);
        }
        return switch (state) {
            case "AVAILABLE" -> new Result(EstadoYoutube.DISPONIBLE, id);
            case "PROCESSING" -> new Result(EstadoYoutube.PROCESANDO, id);
            default -> throw new YouTubePublicationException(true);
        };
    }
}
