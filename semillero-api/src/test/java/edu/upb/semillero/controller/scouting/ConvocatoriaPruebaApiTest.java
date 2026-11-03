package edu.upb.semillero.controller.scouting;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import edu.upb.semillero.repository.entity.*;
import edu.upb.semillero.repository.repos.*;
import edu.upb.semillero.repository.repos.scouting.*;
import edu.upb.semillero.service.seguridad.JwtService;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;

import tools.jackson.databind.ObjectMapper;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@SpringBootTest(
        properties = {
            "spring.datasource.url=${PRUEBAS_TEST_URL:jdbc:h2:mem:scouting-pruebas;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
            "spring.datasource.username=${PRUEBAS_TEST_USER:sa}"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConvocatoriaPruebaApiTest {
    static final String API = "/api/v1/scouting/pruebas";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwt;
    @Autowired UsuarioRepository usuarios;
    @Autowired ClubRepository clubes;
    @Autowired UsuarioClubRepository perfiles;
    @Autowired UsuarioTrabajaRepository trabajos;
    @Autowired JugadorRepository jugadores;
    @Autowired InscripcionPruebaScoutingRepository inscripciones;
    @Autowired EvaluacionPruebaScoutingRepository evaluaciones;

    @MockitoBean(name = "scoutingClock")
    Clock clock;

    volatile Instant ahora;

    @BeforeEach
    void tiempo() {
        ahora = Instant.parse("2027-04-01T13:00:00Z");
        when(clock.instant()).thenAnswer(i -> ahora);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    Usuario usuario() {
        var u = new Usuario();
        u.setName(UUID.randomUUID().toString());
        u.setNombres("Ana");
        u.setApellidos("Prueba");
        u.setEmail(u.getName() + "@example.test");
        return usuarios.saveAndFlush(u);
    }

    record DT(Usuario user, Club club, UsuarioTrabaja trabajo) {}

    DT dt() {
        var u = usuario();
        var c = new Club();
        c.setNombreClub("Club " + UUID.randomUUID().toString().substring(0, 8));
        c = clubes.saveAndFlush(c);
        var p = new UsuarioClub();
        p.setUsuario(u);
        p = perfiles.saveAndFlush(p);
        var t = new UsuarioTrabaja();
        t.setUsuarioClub(p);
        t.setClub(c);
        t.setTipoUsuarioClub("DT");
        return new DT(u, c, trabajos.saveAndFlush(t));
    }

    Usuario jugador(int edad) {
        var u = usuario();
        var j = new Jugador();
        j.setUsuario(u);
        j.setFechaNacimiento(LocalDate.of(2027, 4, 1).minusYears(edad));
        jugadores.saveAndFlush(j);
        return u;
    }

    String token(Usuario u) {
        return "Bearer " + jwt.emitir(u.getId(), u.getName(), List.of("CONSULTA"), null);
    }

    Map<String, Object> cuerpo(DT d, int cupo) {
        return new LinkedHashMap<>(
                Map.of(
                        "clubId",
                        d.club().getId(),
                        "titulo",
                        "Selección de delanteros",
                        "fechaPrueba",
                        "2027-04-02",
                        "horaPrueba",
                        "09:00:00",
                        "limiteInscripcion",
                        "2027-04-02T12:00:00Z",
                        "lugar",
                        "Cancha central",
                        "categoria",
                        "Adultos",
                        "descripcion",
                        "Ejercicios técnicos y partido de evaluación",
                        "cupos",
                        cupo));
    }

    long nueva(DT d, int cupo) throws Exception {
        return json.readTree(
                        mvc.perform(
                                        post(API)
                                                .header("Authorization", token(d.user()))
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(json.writeValueAsString(cuerpo(d, cupo))))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                .get("id")
                .asLong();
    }

    ResultActions anotar(long id, Usuario u) throws Exception {
        return mvc.perform(
                post(API + "/" + id + "/inscripciones")
                        .header("Authorization", token(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nota\":\"Delantero\"}"));
    }

    String anotado(long id, Usuario u) throws Exception {
        return json.readTree(
                        anotar(id, u)
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                .get("id")
                .asString();
    }

    @Test
    void dtPublicaYJugadorSeInscribeSinDuplicados() throws Exception {
        var d = dt();
        long id = nueva(d, 2);
        var u = jugador(20);
        String i = anotado(id, u);
        anotar(id, u).andExpect(jsonPath("$.id").value(i));
        assertThat(inscripciones.count()).isGreaterThan(0);
        mvc.perform(get(API + "/" + id).header("Authorization", token(u)))
                .andExpect(jsonPath("$.inscritos").value(1))
                .andExpect(jsonPath("$.inscripcionPropia.id").value(i));
    }

    @Test
    void soloDtAfiliadoPuedePublicarOGestionar() throws Exception {
        var d = dt();
        var otro = dt();
        long id = nueva(d, 2);
        var body = cuerpo(d, 2);
        mvc.perform(
                        post(API)
                                .header("Authorization", token(otro.user()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(body)))
                .andExpect(status().isForbidden());
        mvc.perform(
                        get(API + "/" + id + "/inscripciones")
                                .header("Authorization", token(otro.user())))
                .andExpect(status().isForbidden());
        trabajos.deleteById(d.trabajo().getId());
        mvc.perform(get(API + "/" + id + "/inscripciones").header("Authorization", token(d.user())))
                .andExpect(status().isForbidden());
    }

    @Test
    void cupoSeRespetaConSolicitudesSimultaneas() throws Exception {
        var d = dt();
        long id = nueva(d, 1);
        var a = jugador(21);
        var b = jugador(22);
        try (var ex = Executors.newFixedThreadPool(2)) {
            var inicio = new CountDownLatch(1);
            Callable<Integer> ca =
                    () -> {
                        inicio.await();
                        return anotar(id, a).andReturn().getResponse().getStatus();
                    };
            Callable<Integer> cb =
                    () -> {
                        inicio.await();
                        return anotar(id, b).andReturn().getResponse().getStatus();
                    };
            var fa = ex.submit(ca);
            var fb = ex.submit(cb);
            inicio.countDown();
            assertThat(List.of(fa.get(20, TimeUnit.SECONDS), fb.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
    }

    @Test
    void menorYTerceroNoAccedenALaLista() throws Exception {
        long id = nueva(dt(), 2);
        anotar(id, jugador(16)).andExpect(status().isForbidden());
        mvc.perform(
                        get(API + "/" + id + "/inscripciones")
                                .header("Authorization", token(jugador(20))))
                .andExpect(status().isForbidden());
        mvc.perform(get(API)).andExpect(status().isUnauthorized());
    }

    @Test
    void jugadorRetiraSoloSuInscripcionYNoSeReinscribe() throws Exception {
        long id = nueva(dt(), 1);
        var u = jugador(20);
        String i = anotado(id, u);
        mvc.perform(
                        delete(API + "/" + id + "/inscripciones/" + i)
                                .param("version", "0")
                                .header("Authorization", token(jugador(21))))
                .andExpect(status().isNotFound());
        mvc.perform(
                        delete(API + "/" + id + "/inscripciones/" + i)
                                .param("version", "0")
                                .header("Authorization", token(u)))
                .andExpect(status().isNoContent());
        anotar(id, u).andExpect(status().isConflict());
        anotar(id, jugador(21)).andExpect(status().isOk());
    }

    @Test
    void plazoYCancellationCierranInscripciones() throws Exception {
        var d = dt();
        long id = nueva(d, 1);
        mvc.perform(
                        delete(API + "/" + id)
                                .param("version", "0")
                                .header("Authorization", token(d.user())))
                .andExpect(status().isNoContent());
        anotar(id, jugador(22)).andExpect(status().isConflict());
        long otro = nueva(d, 1);
        ahora = Instant.parse("2027-04-02T12:00:00Z");
        anotar(otro, jugador(20)).andExpect(status().isConflict());
    }

    @Test
    void evaluacionIndividualTieneVersionHistorialYPrivacidad() throws Exception {
        var d = dt();
        long id = nueva(d, 2);
        var u = jugador(20);
        String i = anotado(id, u);
        String path = API + "/" + id + "/inscripciones/" + i + "/evaluaciones";
        String body =
                "{\"asistencia\":\"PRESENTE\",\"resultado\":\"ACEPTADO\",\"observaciones\":\"Buen"
                    + " control y pase\",\"version\":0}";
        mvc.perform(
                        post(path)
                                .header("Authorization", token(d.user()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isConflict());
        ahora = Instant.parse("2027-04-02T14:00:00Z");
        mvc.perform(
                        post(path)
                                .header("Authorization", token(d.user()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(
                        post(path)
                                .header("Authorization", token(d.user()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isConflict());
        mvc.perform(get(path).header("Authorization", token(u)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(path).header("Authorization", token(jugador(20))))
                .andExpect(status().isForbidden());
        mvc.perform(get(API + "/" + id).header("Authorization", token(u)))
                .andExpect(
                        jsonPath("$.inscripcionPropia.observaciones").value("Buen control y pase"));
    }

    @Test
    void noSePuedeInscribirAJugadorAjenoDesdeElCuerpo() throws Exception {
        long id = nueva(dt(), 2);
        var u = jugador(20);
        var otro = jugador(22);
        long ajeno = jugadores.findFirstByUsuarioId(otro.getId()).orElseThrow().getId();
        mvc.perform(
                        post(API + "/" + id + "/inscripciones")
                                .header("Authorization", token(u))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "jugadorId",
                                                        ajeno,
                                                        "nota",
                                                        "Intento ajeno"))))
                .andExpect(status().isBadRequest());
        assertThat(inscripciones.findByConvocatoriaIdAndJugadorId(id, ajeno)).isEmpty();
    }
}
