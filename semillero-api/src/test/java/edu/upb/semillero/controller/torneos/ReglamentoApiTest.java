package edu.upb.semillero.controller.torneos;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HU 2 - Reglamento: tareas S1-12 (endpoint) y S1-13 (orden de los criterios
 * de desempate).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReglamentoApiTest {
    private static final String TORNEOS = "/api/v1/torneos";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private long organizadorId;

    @BeforeEach
    void fixtures() throws Exception {
        organizadorId = create("/api/v1/usuarios", Map.of("name", "org",
                "nombres", "Ignacio", "apellidos", "Daza",
                "email", "reglamento" + System.nanoTime() + "@example.com",
                "password", "secreto123"));
    }

    @Test
    void creaElReglamentoConSuOrdenDeDesempates() throws Exception {
        long torneoId = crearTorneo();
        String url = TORNEOS + "/" + torneoId + "/reglamento";

        postJson(url, reglamento(List.of("DIF_GOLES", "GOLES_FAVOR", "ENFRENTAMIENTO")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.torneoId").value(torneoId));

        mvc.perform(get(url)).andExpect(status().isOk())
                .andExpect(jsonPath("$.puntosVictoria").value(3))
                .andExpect(jsonPath("$.puntosEmpate").value(1))
                .andExpect(jsonPath("$.puntosDerrota").value(0))
                .andExpect(jsonPath("$.umbralAmarillas").value(5))
                .andExpect(jsonPath("$.descansoMinimoMin").value(60))
                .andExpect(jsonPath("$.minJugadores").value(11))
                .andExpect(jsonPath("$.maxJugadores").value(18))
                .andExpect(jsonPath("$.desempates[0]").value("DIF_GOLES"))
                .andExpect(jsonPath("$.desempates[1]").value("GOLES_FAVOR"))
                .andExpect(jsonPath("$.desempates[2]").value("ENFRENTAMIENTO"));

        mvc.perform(get(url + "/desempates")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].codigo").value("DIF_GOLES"))
                .andExpect(jsonPath("$[0].orden").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Diferencia de goles"))
                .andExpect(jsonPath("$[2].codigo").value("ENFRENTAMIENTO"))
                .andExpect(jsonPath("$[2].orden").value(3));
    }

    @Test
    void laFechaDeCorteEsEl31DeDiciembreDeLaTemporada() throws Exception {
        long torneoId = crearTorneo();
        Map<String, Object> body = reglamento(List.of("DIF_GOLES"));
        body.remove("fechaCorte");
        postJson(TORNEOS + "/" + torneoId + "/reglamento", body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fechaCorte").value("2026-12-31"));
    }

    @Test
    void soloHayUnReglamentoPorTorneo() throws Exception {
        long torneoId = crearTorneo();
        String url = TORNEOS + "/" + torneoId + "/reglamento";
        postJson(url, reglamento(List.of("DIF_GOLES"))).andExpect(status().isCreated());
        postJson(url, reglamento(List.of("DIF_GOLES"))).andExpect(status().isConflict());
    }

    @Test
    void rechazaElMinimoMayorQueElMaximo() throws Exception {
        long torneoId = crearTorneo();
        Map<String, Object> body = reglamento(List.of("DIF_GOLES"));
        body.put("minJugadores", 20);
        body.put("maxJugadores", 11);
        postJson(TORNEOS + "/" + torneoId + "/reglamento", body).andExpect(status().isBadRequest());
    }

    @Test
    void rechazaCriteriosRepetidosOInexistentes() throws Exception {
        long torneoId = crearTorneo();
        String url = TORNEOS + "/" + torneoId + "/reglamento";

        postJson(url, reglamento(List.of("DIF_GOLES", "DIF_GOLES"))).andExpect(status().isBadRequest());
        postJson(url, reglamento(List.of("NO_EXISTE"))).andExpect(status().isNotFound());
        postJson(url, reglamento(List.of())).andExpect(status().isBadRequest());
        mvc.perform(get(url)).andExpect(status().isNotFound());
    }

    @Test
    void reemplazaElOrdenDeLosDesempates() throws Exception {
        long torneoId = crearTorneo();
        String url = TORNEOS + "/" + torneoId + "/reglamento";
        postJson(url, reglamento(List.of("DIF_GOLES", "GOLES_FAVOR"))).andExpect(status().isCreated());

        mvc.perform(put(url + "/desempates").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("codigos",
                                List.of("FAIR_PLAY", "DIF_GOLES", "SORTEO")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].codigo").value("FAIR_PLAY"))
                .andExpect(jsonPath("$[0].orden").value(1))
                .andExpect(jsonPath("$[1].codigo").value("DIF_GOLES"))
                .andExpect(jsonPath("$[2].codigo").value("SORTEO"));

        mvc.perform(get(url)).andExpect(jsonPath("$.desempates[0]").value("FAIR_PLAY"))
                .andExpect(jsonPath("$.desempates.length()").value(3));

        mvc.perform(put(url + "/desempates").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("codigos", List.of("SORTEO", "SORTEO")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elTorneoSinReglamentoYElTorneoInexistenteSon404() throws Exception {
        long torneoId = crearTorneo();
        mvc.perform(get(TORNEOS + "/" + torneoId + "/reglamento")).andExpect(status().isNotFound());
        mvc.perform(get(TORNEOS + "/" + Long.MAX_VALUE + "/reglamento")).andExpect(status().isNotFound());
        mvc.perform(delete(TORNEOS + "/" + torneoId + "/reglamento")).andExpect(status().isNotFound());
    }

    @Test
    void borraElReglamentoConSusDesempates() throws Exception {
        long torneoId = crearTorneo();
        String url = TORNEOS + "/" + torneoId + "/reglamento";
        postJson(url, reglamento(List.of("DIF_GOLES", "SORTEO"))).andExpect(status().isCreated());
        mvc.perform(delete(url)).andExpect(status().isNoContent());
        mvc.perform(get(url)).andExpect(status().isNotFound());
        mvc.perform(get(url + "/desempates")).andExpect(status().isNotFound());
    }

    @Test
    void elCatalogoTraeLosCincoCriteriosDelScript() throws Exception {
        mvc.perform(get(TORNEOS + "/criterios-desempate").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[?(@.codigo == 'DIF_GOLES')]").exists())
                .andExpect(jsonPath("$.content[?(@.codigo == 'SORTEO')]").exists());
    }

    private long crearTorneo() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nombre", "Copa Reglamento");
        body.put("temporada", 2026);
        body.put("tipo", "OFICIAL");
        body.put("ambito", "INTERNO");
        body.put("formato", "LIGA");
        body.put("fechaInicio", "2026-03-01");
        body.put("fechaFin", "2026-06-30");
        body.put("fechaLimiteInscripcion", "2026-02-15");
        body.put("usuarioId", organizadorId);
        return create(TORNEOS, body);
    }

    private Map<String, Object> reglamento(List<String> desempates) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("umbralAmarillas", 5);
        body.put("fechaCorte", "2026-12-31");
        body.put("minJugadores", 11);
        body.put("maxJugadores", 18);
        body.put("desempates", desempates);
        return body;
    }

    private long create(String url, Map<String, Object> body) throws Exception {
        String content = postJson(url, body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(content).get("id").asLong();
    }

    private ResultActions postJson(String url, Map<String, Object> body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }
}
