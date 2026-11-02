package edu.upb.semillero.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:semillero-vps;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "semillero.jwt.secret=secreto-solo-para-pruebas-del-perfil-vps",
        "semillero.cors.allowed-origin-patterns=https://web.semillero.example"
})
@AutoConfigureMockMvc
@ActiveProfiles({"test", "vps"})
class VpsConfigurationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired AnalysisProperties analysis;
    @Autowired VideoProperties video;
    @Autowired org.springframework.core.env.Environment environment;
    @Autowired org.springframework.context.ApplicationContext context;

    @Test
    void elRuntimeNoCreaFlywayYUsaElLoopbackYPuertoDelProxy() {
        assertThat(environment.getProperty("spring.flyway.enabled", Boolean.class)).isFalse();
        assertThat(context.getBeansOfType(org.flywaydb.core.Flyway.class)).isEmpty();
        assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
        assertThat(environment.getProperty("server.port", Integer.class)).isEqualTo(18083);
    }

    @Test
    void losVideosMapasYTemporalesUsanElVolumenPersistente() {
        assertThat(video.getStorageDir()).isEqualTo(java.nio.file.Path.of("/var/lib/semillero/videos"));
        assertThat(analysis.getArtifactsDir()).isEqualTo(java.nio.file.Path.of("/var/lib/semillero/analysis"));
        assertThat(environment.getProperty("spring.servlet.multipart.location"))
                .isEqualTo("/var/lib/semillero/tmp/multipart");
    }

    @Test
    void lasIntegracionesDeVideoQuedanDeshabilitadasEnEstaEntrega() {
        assertThat(analysis.isEnabled()).isFalse();
        assertThat(analysis.isWorkerEnabled()).isFalse();
        assertThat(video.getYoutube().isEnabled()).isFalse();
        assertThat(video.getYoutube().isWorkerEnabled()).isFalse();
    }

    @Test
    void laWebConfiguradaPuedeCompletarElPreflightDeUnaRutaProtegida() throws Exception {
        mvc.perform(options("/api/v1/torneos")
                        .header(HttpHeaders.ORIGIN, "https://web.semillero.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "https://web.semillero.example"));
    }

    @Test
    void elOrigenConfiguradoNoEliminaLaAutenticacion() throws Exception {
        mvc.perform(get("/api/v1/torneos").header(HttpHeaders.ORIGIN, "https://web.semillero.example"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "https://web.semillero.example"));
    }

    @Test
    void elPreflightAdmiteElPatchDeVisibilidadDeHighlights() throws Exception {
        mvc.perform(options("/api/v1/highlights/1")
                        .header(HttpHeaders.ORIGIN, "https://web.semillero.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "https://web.semillero.example"));
    }

    @Test
    void laConfiguracionDelVpsNoConservaLosOrigenesLocalesPorDefecto() throws Exception {
        mvc.perform(options("/api/v1/torneos")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
