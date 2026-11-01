package edu.upb.semillero;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HighlightVisibilityIntegrationTest {
    private static final String API = "/api/v1/";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void loginAcceptsEmailOrUsername() throws Exception {
        String unique = Long.toUnsignedString(System.nanoTime());
        String username = "jugador-" + unique;
        String email = "ana-" + unique + "@example.com";
        long userId = create("usuarios", Map.of(
                "name", username,
                "nombres", "Ana",
                "apellidos", "Pérez",
                "email", email,
                "password", "secreto123"));
        create("jugadores", Map.of("usuarioId", userId));

        assertThat(login(email, "secreto123")).isNotBlank();
        assertThat(login(username, "secreto123")).isNotBlank();
    }

    @Test
    void rejectsDuplicateLoginIdentifiers() throws Exception {
        String unique = Long.toUnsignedString(System.nanoTime());
        String username = "jugador-" + unique;
        String email = "ana-" + unique + "@example.com";
        Map<String, Object> user = Map.of(
                "name", username,
                "nombres", "Ana",
                "apellidos", "Pérez",
                "email", email,
                "password", "secreto123");
        create("usuarios", user);

        mvc.perform(post(API + "usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(user)))
                .andExpect(status().isConflict());
    }

    @Test
    void hiddenHighlightStaysInOwnerHistoryAndIsNotPublic() throws Exception {
        String unique = Long.toUnsignedString(System.nanoTime());
        long userId = create("usuarios", Map.of(
                "name", "jugador-" + unique,
                "nombres", "Ana",
                "apellidos", "Pérez",
                "email", "ana-" + unique + "@example.com",
                "password", "secreto123"));
        long playerId = create("jugadores", Map.of("usuarioId", userId));

        String token = login("ana-" + unique + "@example.com", "secreto123");
        long highlightId = createHighlight(playerId, token);

        mvc.perform(patch(API + "highlights/" + highlightId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visible\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visible").value(false));

        mvc.perform(get(API + "highlights").param("jugadorId", String.valueOf(playerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        mvc.perform(get(API + "highlights")
                        .param("jugadorId", String.valueOf(playerId))
                        .param("incluirOcultos", "true"))
                .andExpect(status().isForbidden());

        mvc.perform(get(API + "highlights")
                        .header("Authorization", "Bearer " + token)
                        .param("jugadorId", String.valueOf(playerId))
                        .param("incluirOcultos", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(highlightId))
                .andExpect(jsonPath("$.content[0].visible").value(false));

        mvc.perform(patch(API + "highlights/" + highlightId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visible\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visible").value(true));

        mvc.perform(get(API + "highlights").param("jugadorId", String.valueOf(playerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(highlightId));
    }

    private long createHighlight(long playerId, String token) throws Exception {
        String response = mvc.perform(post(API + "highlights")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "jugadorId", playerId,
                                "youtubeUrl", "https://youtu.be/dQw4w9WgXcQ"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    private long create(String route, Map<String, Object> body) throws Exception {
        String response = mvc.perform(post(API + route)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    private String login(String identifier, String password) throws Exception {
        String response = mvc.perform(post(API + "auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "identificador", identifier, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode result = json.readTree(response);
        String token = result.get("token").asString();
        assertThat(token).isNotBlank();
        return token;
    }
}
