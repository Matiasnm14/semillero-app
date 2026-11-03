package edu.upb.semillero.config;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:vps-video-opt-in;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "semillero.jwt.secret=clave-exclusiva-de-pruebas",
        "ANALYSIS_ENABLED=true", "ANALYSIS_WORKER_ENABLED=true", "ANALYSIS_BASE_URL=https://spark.example.test",
        "VIDEO_YOUTUBE_ENABLED=true", "VIDEO_YOUTUBE_WORKER_ENABLED=true",
        "analysis.poll-interval=1h", "analysis.lease-duration=3h", "video.youtube.poll-ms=3600000"
})
@ActiveProfiles({"test", "vps"})
class VpsVideoOptInIntegrationTest {
    @Autowired VideoProperties video;
    @Autowired AnalysisProperties analysis;
    @Autowired org.springframework.core.env.Environment environment;

    @Test
    void lasIntegracionesSeHabilitanExplicitamenteSinHabilitarMigracionesEnElRuntime() {
        assertThat(analysis.isEnabled()).isTrue();
        assertThat(analysis.isWorkerEnabled()).isTrue();
        assertThat(video.getYoutube().isEnabled()).isTrue();
        assertThat(video.getYoutube().isWorkerEnabled()).isTrue();
        assertThat(environment.getProperty("spring.flyway.enabled", Boolean.class)).isFalse();
    }

    @Test
    void elUploaderUsaElInterpreteDeLaImagenYElVolumenPrivado() {
        assertThat(video.getYoutube().getPython()).isEqualTo("/opt/youtube/bin/python");
        assertThat(video.getYoutube().getScript()).isEqualTo(Path.of("/app/scripts/youtube_upload.py"));
        assertThat(video.getYoutube().getClient()).isEqualTo(Path.of("/var/lib/semillero/youtube/client_secret.json"));
        assertThat(video.getYoutube().getTokens()).isEqualTo(Path.of("/var/lib/semillero/youtube/tokens.json"));
    }
}
