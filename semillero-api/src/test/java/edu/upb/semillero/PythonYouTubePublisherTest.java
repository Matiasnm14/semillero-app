package edu.upb.semillero;

import edu.upb.semillero.config.VideoProperties;
import edu.upb.semillero.repository.enums.EstadoYoutube;
import edu.upb.semillero.service.video.PythonYouTubePublisher;
import edu.upb.semillero.service.video.VideoProcessRunner;
import edu.upb.semillero.service.video.YouTubePublicationException;
import edu.upb.semillero.service.video.YouTubePublisher;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PythonYouTubePublisherTest {
    @TempDir Path root;
    private final VideoProperties properties = new VideoProperties();
    private final VideoProcessRunner runner = mock(VideoProcessRunner.class);
    private final ObjectMapper json = new ObjectMapper();
    private PythonYouTubePublisher publisher;
    private YouTubePublisher.Publication publication;

    @BeforeEach
    void setup() {
        publisher = new PythonYouTubePublisher(properties, runner, json);
        publication = new YouTubePublisher.Publication(root.resolve("original.mp4"), root.resolve("receipt.json"), "2026-05-02");
    }

    @Test
    @SuppressWarnings("unchecked")
    void usesNonInteractiveResumableScriptWithMatchDateAndSportsCategory() throws Exception {
        receipt("AVAILABLE", "unlisted", "2026-05-02");
        when(runner.run(any(), any())).thenReturn(new VideoProcessRunner.Result(0, "ignored output"));
        assertThat(publisher.publish(publication)).isEqualTo(new YouTubePublisher.Result(EstadoYoutube.DISPONIBLE, "videoABC123"));
        var args = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(runner).run(args.capture(), any());
        assertThat(args.getValue()).containsSubsequence("--non-interactive", "--wait-seconds", "0", "--category", "17");
        assertThat(args.getValue()).containsSubsequence("--title", "2026-05-02", "--record", publication.receipt().toString());
    }

    @Test
    void processingDoesNotMeanAvailable() throws Exception {
        receipt("PROCESSING", "unlisted", "2026-05-02");
        when(runner.run(any(), any())).thenReturn(new VideoProcessRunner.Result(0, ""));
        assertThat(publisher.publish(publication).state()).isEqualTo(EstadoYoutube.PROCESANDO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"REVIEW_REQUIRED", "METADATA_MISMATCH", "FAILED"})
    void remoteProblemsRequireReviewAndNeverSurfaceRawProcessOutput(String state) throws Exception {
        receipt(state, "unlisted", "2026-05-02");
        when(runner.run(any(), any())).thenReturn(new VideoProcessRunner.Result(1, "secret_token=do-not-show"));
        assertThatThrownBy(() -> publisher.publish(publication)).isInstanceOfSatisfying(
                YouTubePublicationException.class, e -> {
                    assertThat(e.isReviewRequired()).isTrue();
                    assertThat(e.getMessage()).doesNotContain("secret_token");
                });
    }

    @Test
    void doesNotTrustSuccessfulExitWithWrongPrivacyOrTitle() throws Exception {
        when(runner.run(any(), any())).thenReturn(new VideoProcessRunner.Result(0, ""));
        receipt("AVAILABLE", "public", "2026-05-02");
        assertThatThrownBy(() -> publisher.publish(publication)).isInstanceOf(YouTubePublicationException.class);
        receipt("AVAILABLE", "unlisted", "wrong date");
        assertThatThrownBy(() -> publisher.publish(publication)).isInstanceOf(YouTubePublicationException.class);
    }

    @Test
    void missingReceiptCannotReportSuccess() {
        when(runner.run(any(), any())).thenReturn(new VideoProcessRunner.Result(0, ""));
        assertThatThrownBy(() -> publisher.publish(publication)).isInstanceOf(YouTubePublicationException.class);
    }

    private void receipt(String state, String privacy, String title) throws Exception {
        Files.writeString(publication.receipt(), json.writeValueAsString(Map.of(
                "state", state, "videoId", "videoABC123", "actualPrivacy", privacy,
                "actualTitle", title, "channelId", "channel-id", "actualChannelId", "channel-id")));
    }
}
