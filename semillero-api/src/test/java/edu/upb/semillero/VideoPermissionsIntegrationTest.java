package edu.upb.semillero;

import edu.upb.semillero.repository.entity.*;
import edu.upb.semillero.repository.enums.*;
import edu.upb.semillero.repository.repos.*;
import edu.upb.semillero.service.VideoPartidoService;
import edu.upb.semillero.service.analysis.AnalysisGateway;
import edu.upb.semillero.service.seguridad.JwtService;
import edu.upb.semillero.service.video.VideoInspector;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Uses real signed JWTs and database ownership, never an unscoped mock role for permission assertions. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:video-permissions;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "analysis.enabled=false", "analysis.worker-enabled=false",
        "video.youtube.enabled=true", "video.youtube.worker-enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VideoPermissionsIntegrationTest {
    @TempDir static Path root;
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("video.storage-dir", () -> root.toString());
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwt;
    @Autowired UsuarioRepository users;
    @Autowired ClubRepository clubs;
    @Autowired PartidoRepository matches;
    @Autowired UsuarioClubRepository clubUsers;
    @Autowired UsuarioTrabajaRepository work;
    @Autowired ParticipacionPartidoRepository participations;
    @Autowired JugadorRepository players;
    @Autowired VideoPartidoRepository videos;
    @Autowired AnalysisJobRepository jobs;
    @Autowired AuditoriaRepository audit;
    @Autowired VideoPartidoService service;
    @MockitoSpyBean VideoInspector inspector;
    @MockitoBean AnalysisGateway gateway;
    private Usuario coach, outsider, mesa, reader, playerUser, admin;
    private Club local, visitor, third, fourth;
    private Partido match, other;
    private byte[] video;

    @BeforeEach
    void seed() throws Exception {
        audit.deleteAll(); jobs.deleteAll(); videos.deleteAll(); participations.deleteAll();
        players.deleteAll(); matches.deleteAll(); work.deleteAll(); clubUsers.deleteAll(); clubs.deleteAll(); users.deleteAll();
        try (var paths = Files.walk(root)) {
            for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) if (!path.equals(root)) Files.delete(path);
        }
        coach = user("coach"); outsider = user("outsider"); mesa = user("mesa"); reader = user("reader");
        playerUser = user("player"); admin = user("admin");
        local = club("local"); visitor = club("visitor"); third = club("third"); fourth = club("fourth");
        employs(coach, local); employs(outsider, third);
        match = match(local, visitor); match.setMesaControl(mesa); match = matches.saveAndFlush(match);
        other = match(third, fourth);
        var player = new Jugador(); player.setUsuario(playerUser); player = players.saveAndFlush(player);
        var participation = new ParticipacionPartido();
        participation.setPartido(match); participation.setJugador(player); participation.setClub(local);
        participation.setEsTitular(true); participation.setMinutoEntrada((short) 0); participation.setDorsal((short) 7);
        participations.saveAndFlush(participation);
        try (var input = getClass().getResourceAsStream("/video/valid.mp4")) { video = input.readAllBytes(); }
    }

    @Test
    void anonymousOrInvalidTokenCannotUploadReadOrRetry() throws Exception {
        for (String token : new String[]{"", "Bearer invalid.token.value"}) {
            mvc.perform(multipart(base(match)).file(file()).header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isUnauthorized());
            mvc.perform(get(base(match)).header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isUnauthorized());
            mvc.perform(post(base(match) + "/1/analisis").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isUnauthorized());
            mvc.perform(post(base(match) + "/1/youtube/reintentar").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/analisis/1").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isUnauthorized());
        }
        assertNoUpload();
        verify(inspector, never()).inspect(any());
        assertThat(audit.count()).isGreaterThan(0);
    }

    @Test
    void ownCoachCreatesPendingUploadWithRecordedIdentityAndConsultableLocation() throws Exception {
        var accepted = upload(match, coach, "ENTRENADOR");
        long id = accepted.path("id").asLong(), analysis = accepted.path("analysisId").asLong();
        assertThat(videos.findById(id).orElseThrow().getUsuarioCargaId()).isEqualTo(coach.getId());
        mvc.perform(auth(get(base(match) + "/" + id), coach, "ENTRENADOR")).andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoAnalisis").value("PENDIENTE"));
        mvc.perform(auth(get("/api/v1/analisis/" + analysis), coach, "ENTRENADOR")).andExpect(status().isOk());
        assertThat(jobs.findById(analysis).orElseThrow().isEnviado()).isFalse();
        verifyNoInteractions(gateway);
    }

    @Test
    void anotherClubCannotUploadOrReadUsingGuessedIdentifiers() throws Exception {
        var accepted = upload(match, coach, "ENTRENADOR");
        String videoPath = base(match) + "/" + accepted.path("id").asLong();
        String analysisPath = "/api/v1/analisis/" + accepted.path("analysisId").asLong();
        clearInvocations(inspector);
        mvc.perform(auth(multipart(base(match)).file(file()), outsider, "ENTRENADOR")).andExpect(status().isForbidden());
        for (String path : new String[]{base(match), videoPath, videoPath + "/analisis", analysisPath,
                analysisPath + "/resultados", analysisPath + "/heatmap"}) {
            mvc.perform(auth(get(path), outsider, "ENTRENADOR")).andExpect(status().isForbidden());
        }
        mvc.perform(auth(post(videoPath + "/analisis"), outsider, "ENTRENADOR")).andExpect(status().isForbidden());
        mvc.perform(auth(post(videoPath + "/youtube/reintentar"), outsider, "ENTRENADOR")).andExpect(status().isForbidden());
        assertThat(videos.count()).isEqualTo(1);
        verify(inspector, never()).inspect(any());
    }

    @Test
    void matchingClubClaimAloneDoesNotGrantAccessWithoutDatabaseRelationship() throws Exception {
        String token = jwt.emitir(reader.getId(), "reader", List.of("ENTRENADOR"), local.getId());
        mvc.perform(multipart(base(match)).file(file()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(get(base(match)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isForbidden());
        assertNoUpload();
    }

    @Test
    void removingClubRelationshipRevokesAccessEvenWithAnUnexpiredToken() throws Exception {
        String token = jwt.emitir(coach.getId(), "coach", List.of("ENTRENADOR"), local.getId());
        work.deleteAll();
        mvc.perform(multipart(base(match)).file(file()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isForbidden());
        assertNoUpload();
    }

    @Test
    void mesaCanUploadOnlyToItsAssignedMatch() throws Exception {
        upload(match, mesa, "MESA_CONTROL");
        mvc.perform(auth(get(base(other)), mesa, "MESA_CONTROL")).andExpect(status().isForbidden());
        mvc.perform(auth(multipart(base(other)).file(file()), mesa, "MESA_CONTROL")).andExpect(status().isForbidden());
        assertThat(videos.count()).isEqualTo(1);
    }

    @Test
    void participantCanReadOwnMatchButCannotUploadOrStartAnalysis() throws Exception {
        var accepted = upload(match, coach, "ENTRENADOR");
        String path = base(match) + "/" + accepted.path("id").asLong();
        String analysis = "/api/v1/analisis/" + accepted.path("analysisId").asLong();
        for (String route : new String[]{base(match), path, path + "/analisis", analysis}) {
            mvc.perform(auth(get(route), playerUser, "CONSULTA")).andExpect(status().isOk());
        }
        mvc.perform(auth(get(analysis + "/resultados"), playerUser, "CONSULTA")).andExpect(status().isConflict());
        mvc.perform(auth(get(analysis + "/heatmap"), playerUser, "CONSULTA")).andExpect(status().isConflict());
        mvc.perform(auth(multipart(base(match)).file(file()), playerUser, "CONSULTA")).andExpect(status().isForbidden());
        mvc.perform(auth(post(path + "/analisis"), playerUser, "CONSULTA")).andExpect(status().isForbidden());
        mvc.perform(auth(post(path + "/youtube/reintentar"), playerUser, "CONSULTA")).andExpect(status().isForbidden());
        mvc.perform(auth(get(base(other)), playerUser, "CONSULTA")).andExpect(status().isForbidden());
    }

    @Test
    void consultaWithoutMatchRelationshipCannotAccessVideos() throws Exception {
        mvc.perform(auth(get(base(match)), reader, "CONSULTA")).andExpect(status().isForbidden());
        mvc.perform(auth(multipart(base(match)).file(file()), reader, "CONSULTA")).andExpect(status().isForbidden());
        assertNoUpload();
    }

    @Test
    void adminAndOrganizerHaveGlobalAccessButCannotCrossVideoMatchRelationships() throws Exception {
        var a = upload(match, admin, "ADMIN");
        var b = upload(other, reader, "ORGANIZADOR");
        mvc.perform(auth(get(base(other) + "/" + b.path("id").asLong()), admin, "ADMIN")).andExpect(status().isOk());
        mvc.perform(auth(get(base(other) + "/" + a.path("id").asLong()), admin, "ADMIN")).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/v1/analisis/999999"), admin, "ADMIN")).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/v1/analisis/-1"), admin, "ADMIN")).andExpect(status().isBadRequest());
    }

    @Test
    void ownershipCannotBeForgedThroughPublicMatchParticipationOrFichajeWrites() throws Exception {
        for (String route : new String[]{"partidos", "participaciones-partido", "fichados"}) {
            mvc.perform(post("/api/v1/" + route).contentType("application/json").content("{}"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(auth(post("/api/v1/" + route).contentType("application/json").content("{}"), outsider, "ENTRENADOR"))
                    .andExpect(status().isForbidden());
            mvc.perform(auth(put("/api/v1/" + route + "/" + match.getId()).contentType("application/json").content("{}"), outsider, "ENTRENADOR"))
                    .andExpect(status().isForbidden());
            mvc.perform(auth(delete("/api/v1/" + route + "/" + match.getId()), reader, "CONSULTA"))
                    .andExpect(status().isForbidden());
        }
        assertThat(matches.findById(match.getId()).orElseThrow().getClubLocal().getId()).isEqualTo(local.getId());
    }

    @Test
    void serviceCannotAcceptAnUploadWithoutAuthentication() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        var reads = new java.util.concurrent.atomic.AtomicInteger();
        var input = new java.io.ByteArrayInputStream(video) {
            @Override public synchronized int read(byte[] b, int off, int len) { reads.incrementAndGet(); return super.read(b, off, len); }
        };
        assertThatThrownBy(() -> service.create(match.getId(), "partido.mp4", video.length, input))
                .isInstanceOf(edu.upb.semillero.service.exception.ForbiddenException.class);
        assertThat(reads.get()).isZero();
        assertNoUpload();
    }

    private JsonNode upload(Partido target, Usuario user, String role) throws Exception {
        var result = mvc.perform(auth(multipart(base(target)).file(file()), user, role))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.estadoAnalisis").value("PENDIENTE"))
                .andExpect(jsonPath("$.usuarioCargaId").value(user.getId())).andReturn();
        var body = json.readTree(result.getResponse().getContentAsString());
        assertThat(result.getResponse().getHeader("Location")).endsWith("/api/v1/analisis/" + body.path("analysisId").asLong());
        return body;
    }
    private <T extends AbstractMockHttpServletRequestBuilder<T>> T auth(T request, Usuario user, String role) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.emitir(user.getId(), user.getName(), List.of(role), null));
    }
    private Usuario user(String name) { var u = new Usuario(); u.setName(name); u.setPasswordHash("test-hash"); return users.saveAndFlush(u); }
    private Club club(String name) { var c = new Club(); c.setNombreClub(name); return clubs.saveAndFlush(c); }
    private void employs(Usuario user, Club club) {
        var member = new UsuarioClub(); member.setUsuario(user); member = clubUsers.saveAndFlush(member);
        var job = new UsuarioTrabaja(); job.setUsuarioClub(member); job.setClub(club); job.setTipoUsuarioClub("EN"); work.saveAndFlush(job);
    }
    private Partido match(Club local, Club visitor) {
        var p = new Partido(); p.setClubLocal(local); p.setClubVisitante(visitor);
        p.setFechaInicio(LocalDateTime.parse("2026-11-02T10:00:00")); p.setEstado(EstadoPartido.FINALIZADO); return matches.saveAndFlush(p);
    }
    private String base(Partido p) { return "/api/v1/partidos/" + p.getId() + "/videos"; }
    private MockMultipartFile file() { return new MockMultipartFile("file", "partido.mp4", "video/mp4", video); }
    private void assertNoUpload() { assertThat(videos.count()).isZero(); assertThat(jobs.count()).isZero(); verifyNoInteractions(gateway); }
}
