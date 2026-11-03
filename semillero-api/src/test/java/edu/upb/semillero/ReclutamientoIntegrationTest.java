package edu.upb.semillero;

import edu.upb.semillero.service.seguridad.UsuarioAutenticado;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "ADMIN")
class ReclutamientoIntegrationTest {
    private static final String API = "/api/v1/";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    private long clubId;
    private long clubUserId;
    private long playerId;
    private long playerUserId;
    private long secondPlayerId;
    private long secondPlayerUserId;
    private String playerFirstName;

    @BeforeEach
    void fixtures() throws Exception {
        long countryId = create("paises", Map.of("nombrePais", "Bolivia"));
        long cityId = create("ciudades", Map.of("nombreCiudad", "La Paz", "paisId", countryId));
        clubId = create("clubes", Map.of("nombreClub", "Club de Prueba", "ciudadId", cityId));
        clubUserId = create("usuarios", user("club@example.test", "María", "Club"));
        long clubProfile = create("usuarios-club", Map.of("usuarioId", clubUserId));
        create("usuarios-trabaja", Map.of("usuarioClubId", clubProfile, "clubId", clubId,
                "tipoUsuarioClub", "DT"));
        playerFirstName = "Lucía" + Long.toUnsignedString(System.nanoTime());
        playerUserId = create("usuarios", user("player@example.test", playerFirstName, "Pérez"));
        playerId = create("jugadores", Map.of("usuarioId", playerUserId, "piernaHabil", "D",
                "fechaNacimiento", "2006-02-01", "altura", 1.7, "peso", 64));
        long positionId = create("posiciones", Map.of("posicion", "Delantero", "codigoPosicion", "DEL"));
        create("posiciones-jugador", Map.of("jugadorId", playerId, "posicionId", positionId));
        create("fichados", Map.of("jugadorId", playerId, "clubId", clubId,
                "fechaFichaje", "2026-01-01T10:00:00"));
        secondPlayerUserId = create("usuarios", user("second@example.test", "Otro", "Jugador"));
        secondPlayerId = create("jugadores", Map.of("usuarioId", secondPlayerUserId, "piernaHabil", "I",
                "fechaNacimiento", "2007-03-01"));
    }

    @Test
    void directorySupportsKnownFiltersAndReturnsClubAndProfileData() throws Exception {
        asPlayer();
        mvc.perform(get(API + "jugadores").param("posicion", "delantero")
                        .param("piernaHabil", "Derecho").param("edadMin", "18")
                        .param("edadMax", "21").param("nombre", playerFirstName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nombres").value(playerFirstName))
                .andExpect(jsonPath("$.content[0].posiciones[0]").value("Delantero"))
                .andExpect(jsonPath("$.content[0].clubActual.id").value(clubId));
    }

    @Test
    void contactRequestsAreOwnedRespondedToAndNotifyBothSides() throws Exception {
        asClub();
        String created = mvc.perform(post(API + "solicitudes-contacto").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("jugadorId", playerId,
                                "asunto", "Invitación", "mensaje", "Queremos conversar."))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andReturn().getResponse().getContentAsString();
        long requestId = json.readTree(created).get("id").asLong();

        asPlayer();
        mvc.perform(get(API + "solicitudes-contacto/jugador/" + playerId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].estado").value("VISTO"));
        mvc.perform(patch(API + "solicitudes-contacto/" + requestId + "/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"ACEPTADO\",\"respuesta\":\"Gracias\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fechaRespuesta").isNotEmpty());

        asClub();
        mvc.perform(get(API + "notificaciones").param("leida", "false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNoLeidas").value(1))
                .andExpect(jsonPath("$.content[0].tipo").value("RESPUESTA_CONTACTO"));
    }

    @Test
    void trialApplicationsRespectCapacityAndUseSoftCancellation() throws Exception {
        asClub();
        String trialJson = mvc.perform(post(API + "pruebas").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("titulo", "Prueba Sub-20",
                                "fechaPrueba", "2027-02-20", "horaPrueba", "10:30:00",
                                "lugar", "Cancha central", "categoria", "Sub-20", "cupos", 1,
                                "clubId", 9999))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.clubId").value(clubId))
                .andExpect(jsonPath("$.titulo").value("Prueba Sub-20"))
                .andReturn().getResponse().getContentAsString();
        long trialId = json.readTree(trialJson).get("id").asLong();

        asPlayer();
        mvc.perform(post(API + "pruebas/" + trialId + "/inscripciones")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nota\":\"Puedo jugar por ambas bandas\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("PENDIENTE"));
        asUser(secondPlayerUserId, null);
        mvc.perform(post(API + "pruebas/" + trialId + "/inscripciones")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());

        asClub();
        String applicationJson = mvc.perform(get(API + "pruebas/" + trialId + "/inscripciones"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].nombreJugador").value(playerFirstName + " Pérez"))
                .andReturn().getResponse().getContentAsString();
        long applicationId = json.readTree(applicationJson).get(0).get("id").asLong();
        mvc.perform(patch(API + "pruebas/" + trialId + "/inscripciones/" + applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"ACEPTADO\",\"notaClub\":\"Confirmado\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ACEPTADO"));
        mvc.perform(delete(API + "pruebas/" + trialId)).andExpect(status().isNoContent());
        mvc.perform(get(API + "pruebas/" + trialId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"));

        asPlayer();
        mvc.perform(delete(API + "pruebas/" + trialId + "/inscripciones/" + applicationId))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select estado from inscripciones_prueba where id=?", String.class, applicationId))
                .isEqualTo("RETIRADO");
    }

    private long create(String route, Map<String, Object> body) throws Exception {
        String result = mvc.perform(post(API + route).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode response = json.readTree(result);
        return response.get("id").asLong();
    }

    private Map<String, Object> user(String email, String first, String last) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        email = email.replace("@", "+" + suffix + "@");
        String name = email.substring(0, email.indexOf('@'));
        var user = new LinkedHashMap<String, Object>();
        user.put("name", name);
        user.put("nombres", first);
        user.put("apellidos", last);
        user.put("email", email);
        user.put("password", "secreto123");
        return user;
    }

    private void asClub() {
        asUser(clubUserId, clubId);
    }

    private void asPlayer() {
        asUser(playerUserId, null);
    }

    private void asUser(long userId, Long activeClub) {
        var principal = new UsuarioAutenticado(userId, "test-user", List.of("CONSULTA"), activeClub);
        TestSecurityContextHolder.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, AuthorityUtils.createAuthorityList("ROLE_CONSULTA")));
    }
}
