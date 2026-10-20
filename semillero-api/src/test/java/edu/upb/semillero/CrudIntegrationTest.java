package edu.upb.semillero;

import edu.upb.semillero.repository.repos.UsuarioRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CrudIntegrationTest {
    private static final String BASE = "/api/v1/";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    private long paisId;
    private long ciudadId;
    private long localId;
    private long visitanteId;
    private long terceroId;
    private long usuarioId;
    private long otroUsuarioId;
    private long usuarioClubId;
    private long jugadorId;
    private long posicionId;
    private long otraPosicionId;
    private long partidoId;

    @BeforeEach
    void fixtures() throws Exception {
        paisId = create("paises", Map.of("nombrePais", "Bolivia"));
        ciudadId = create("ciudades", Map.of("nombreCiudad", "La Paz", "paisId", paisId));
        localId = create("clubes", Map.of("nombreClub", "Local", "ciudadId", ciudadId));
        visitanteId = create("clubes", Map.of("nombreClub", "Visitante", "ciudadId", ciudadId));
        terceroId = create("clubes", Map.of("nombreClub", "Tercero", "ciudadId", ciudadId));
        usuarioId = create("usuarios", usuario("ana@example.com"));
        otroUsuarioId = create("usuarios", usuario("luis@example.com"));
        usuarioClubId = create("usuarios-club", Map.of("usuarioId", usuarioId));
        jugadorId = create("jugadores", Map.of("usuarioId", usuarioId, "piernaHabil", "D"));
        posicionId = create("posiciones", Map.of("posicion", "Delantero", "codigoPosicion", "DEL"));
        otraPosicionId = create("posiciones", Map.of("posicion", "Arquero", "codigoPosicion", "ARQ"));
        partidoId = create("partidos", partido(localId, visitanteId));
    }

    @ParameterizedTest(name = "CRUD de {0}")
    @ValueSource(strings = {"paises", "ciudades", "clubes", "usuarios", "administradores",
            "usuarios-club", "usuarios-trabaja", "jugadores", "posiciones", "posiciones-jugador",
            "fichados", "pruebas", "clinicas-pago", "videos", "conversaciones", "partidos",
            "participaciones-partido"})
    void fullCrud(String route) throws Exception {
        Resource resource = resource(route);
        Map<String, Object> body = new LinkedHashMap<>(resource.body());
        long id = create(route, body);
        mvc.perform(get(BASE + route + "/" + id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        mvc.perform(get(BASE + route).param("size", "100").param("sort", "id,desc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(100));

        body.put(resource.changedField(), resource.changedValue());
        mvc.perform(put(BASE + route + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$." + resource.changedField()).value(resource.changedValue()));
        mvc.perform(get(BASE + route + "/" + id))
                .andExpect(jsonPath("$." + resource.changedField()).value(resource.changedValue()));
        mvc.perform(delete(BASE + route + "/" + id)).andExpect(status().isNoContent());
        mvc.perform(get(BASE + route + "/" + id)).andExpect(status().isNotFound());
        mvc.perform(delete(BASE + route + "/" + id)).andExpect(status().isNotFound());
        mvc.perform(put(BASE + route + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body))).andExpect(status().isNotFound());
    }

    @Test
    void passwordsAreHashedAndNeverReturned() throws Exception {
        long id = create("usuarios", usuario("hash@example.com"));
        String hash = usuarios.findById(id).orElseThrow().getPasswordHash();
        assertThat(hash).isNotEqualTo("secreto123");
        assertThat(passwordEncoder.matches("secreto123", hash)).isTrue();
        mvc.perform(get(BASE + "usuarios/" + id)).andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get(BASE + "usuarios")).andExpect(jsonPath("$.content[*].passwordHash").isEmpty());
        Map<String, Object> updated = new LinkedHashMap<>(usuario("hash@example.com"));
        updated.put("password", "otraClave123");
        mvc.perform(put(BASE + "usuarios/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(updated)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        assertThat(passwordEncoder.matches("otraClave123", usuarios.findById(id).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void rejectsInvalidFieldsAndMalformedRequests() throws Exception {
        postJson("paises", Map.of("nombrePais", "x".repeat(31))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nombrePais").exists());
        postJson("usuarios", usuario("correo-invalido")).andExpect(status().isBadRequest());
        postJson("usuarios", Map.of("email", "ana@example.com")).andExpect(status().isBadRequest());
        postJson("partidos", Map.of("estado", "PROGRAMADO")).andExpect(status().isBadRequest());
        postJson("paises", Map.of("nombrePais", "Bolivia", "id", 100)).andExpect(status().isBadRequest());
        mvc.perform(post(BASE + "paises").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "paises/-1")).andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "paises/no-es-id")).andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "paises").param("sort", "noExiste"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void foreignKeysMustExistAndDeletesDoNotCascade() throws Exception {
        postJson("ciudades", Map.of("nombreCiudad", "Sucre", "paisId", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
        mvc.perform(delete(BASE + "paises/" + paisId)).andExpect(status().isConflict());
        mvc.perform(get(BASE + "ciudades/" + ciudadId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.paisId").value(paisId));
    }

    @Test
    void matchesRequireDifferentClubsAndOrderedDates() throws Exception {
        postJson("partidos", partido(localId, localId)).andExpect(status().isBadRequest());
        Map<String, Object> invalid = new LinkedHashMap<>(partido(localId, visitanteId));
        invalid.put("fechaFin", "2026-10-20T09:00:00");
        postJson("partidos", invalid).andExpect(status().isBadRequest());
        invalid.put("fechaFin", "2026-10-20T12:00:00");
        invalid.put("estado", "DESCONOCIDO");
        postJson("partidos", invalid).andExpect(status().isBadRequest());
        assertThatThrownBy(() -> jdbc.update("update partidos set club_visitante_id = club_local_id where id = ?", partidoId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void participationMustBeUniqueAndBelongToTheMatch() throws Exception {
        postJson("participaciones-partido", participacion(terceroId)).andExpect(status().isBadRequest());
        long id = create("participaciones-partido", participacion(localId));
        mvc.perform(get(BASE + "participaciones-partido/" + id))
                .andExpect(jsonPath("$.minutoEntrada").value(0));
        postJson("participaciones-partido", participacion(visitanteId)).andExpect(status().isConflict());
        assertThatThrownBy(() -> jdbc.update("""
                insert into participaciones_partido (partidos_id, jugadores_id, clubes_id, es_titular, minuto_entrada)
                values (?, ?, ?, true, 0)
                """, partidoId, jugadorId, localId)).isInstanceOf(DataIntegrityViolationException.class);
        Map<String, Object> invalid = new LinkedHashMap<>(participacion(localId));
        invalid.put("minutoEntrada", 70);
        invalid.put("minutoSalida", 20);
        mvc.perform(put(BASE + "participaciones-partido/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(invalid))).andExpect(status().isBadRequest());
        invalid.put("minutoEntrada", -1);
        postJson("participaciones-partido", invalid).andExpect(status().isBadRequest());
        mvc.perform(put(BASE + "partidos/" + partidoId).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(partido(terceroId, visitanteId))))
                .andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "partidos/" + partidoId)).andExpect(jsonPath("$.clubLocalId").value(localId));
    }

    @Test
    void optionalRelationshipsFollowTheDbmlNullability() throws Exception {
        long id = create("ciudades", Map.of("nombreCiudad", "Sin país"));
        mvc.perform(get(BASE + "ciudades/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.paisId").doesNotExist());
    }

    private long create(String route, Map<String, Object> body) throws Exception {
        String result = postJson(route, body).andExpect(status().isCreated())
                .andExpect(header().exists("Location")).andReturn().getResponse().getContentAsString();
        JsonNode response = json.readTree(result);
        assertThat(response.has("password")).isFalse();
        assertThat(response.has("passwordHash")).isFalse();
        return response.get("id").asLong();
    }

    private ResultActions postJson(String route, Map<String, Object> body) throws Exception {
        return mvc.perform(post(BASE + route).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private Map<String, Object> usuario(String email) {
        return Map.of("name", "ana", "nombres", "Ana", "apellidos", "Pérez", "email", email, "password", "secreto123");
    }

    private Map<String, Object> partido(long local, long visitante) {
        return Map.of("fechaInicio", "2026-10-20T10:00:00", "clubLocalId", local,
                "clubVisitanteId", visitante, "estado", "PROGRAMADO");
    }

    private Map<String, Object> participacion(long club) {
        return Map.of("partidoId", partidoId, "jugadorId", jugadorId, "clubId", club, "esTitular", true);
    }

    private Resource resource(String route) {
        return switch (route) {
            case "paises" -> new Resource(Map.of("nombrePais", "Bolivia"), "nombrePais", "Perú");
            case "ciudades" -> new Resource(Map.of("nombreCiudad", "La Paz", "paisId", paisId), "nombreCiudad", "Sucre");
            case "clubes" -> new Resource(Map.of("nombreClub", "Club", "ciudadId", ciudadId), "nombreClub", "Nuevo Club");
            case "usuarios" -> new Resource(usuario("crud@example.com"), "nombres", "Luis");
            case "administradores", "usuarios-club" -> new Resource(Map.of("usuarioId", usuarioId), "usuarioId", (int) otroUsuarioId);
            case "usuarios-trabaja" -> new Resource(Map.of("usuarioClubId", usuarioClubId, "clubId", localId, "tipoUsuarioClub", "DT"), "tipoUsuarioClub", "PF");
            case "jugadores" -> new Resource(Map.of("usuarioId", usuarioId, "altura", 1.8, "peso", 75.0, "fechaNacimiento", "2004-01-01"), "altura", 1.85);
            case "posiciones" -> new Resource(Map.of("posicion", "Delantero", "codigoPosicion", "DEL"), "posicion", "Defensor");
            case "posiciones-jugador" -> new Resource(Map.of("jugadorId", jugadorId, "posicionId", posicionId), "posicionId", (int) otraPosicionId);
            case "fichados" -> new Resource(Map.of("jugadorId", jugadorId, "clubId", localId, "fechaFichaje", "2026-10-20T10:00:00"), "fechaFinalFichaje", "2027-10-20T10:00:00");
            case "pruebas" -> new Resource(Map.of("clubId", localId, "usuarioClubId", usuarioClubId, "fechaPrueba", "2026-10-20", "horaPrueba", "10:00:00"), "fechaPrueba", "2026-10-21");
            case "clinicas-pago" -> new Resource(Map.of("clubId", localId, "usuarioClubId", usuarioClubId, "fechaInicioClinica", "2026-10-20T10:00:00"), "fechaFinClinica", "2026-10-21T10:00:00");
            case "videos" -> new Resource(Map.of("jugadorId", jugadorId, "tituloVideo", "Jugadas", "linkVideo", "https://example.com/video"), "tituloVideo", "Goles");
            case "conversaciones" -> new Resource(Map.of("usuarioEnviaId", usuarioId, "usuarioRecibeId", otroUsuarioId, "mensaje", "Hola"), "mensaje", "Nos vemos");
            case "partidos" -> new Resource(partido(localId, visitanteId), "estado", "FINALIZADO");
            case "participaciones-partido" -> new Resource(participacion(localId), "dorsal", 10);
            default -> throw new IllegalArgumentException(route);
        };
    }

    private record Resource(Map<String, Object> body, String changedField, Object changedValue) {}
}
