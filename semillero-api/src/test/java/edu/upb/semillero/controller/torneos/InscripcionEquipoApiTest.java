package edu.upb.semillero.controller.torneos;

import java.time.LocalDate;
import java.util.LinkedHashMap;
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
 * HU 4 - Inscribir un equipo: tareas S1-20 (endpoint) y S1-21 (duplicado y
 * cupo). El equipo es el club inscrito en una categoría del torneo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InscripcionEquipoApiTest {
    private static final String TORNEOS = "/api/v1/torneos";
    private static final String INSCRIPCIONES = "/api/v1/torneos/inscripciones";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private long ciudadId;
    private long usuarioId;
    private long clubA;
    private long clubB;
    private long clubC;

    @BeforeEach
    void fixtures() throws Exception {
        long paisId = create("/api/v1/paises", Map.of("nombrePais", "Bolivia"));
        ciudadId = create("/api/v1/ciudades", Map.of("nombreCiudad", "Santa Cruz", "paisId", paisId));
        usuarioId = create("/api/v1/usuarios", Map.of("name", "dt",
                "nombres", "Alan", "apellidos", "Villa",
                "email", "inscripcion" + System.nanoTime() + "@example.com",
                "password", "secreto123"));
        clubA = club("Club A");
        clubB = club("Club B");
        clubC = club("Club C");
    }

    @Test
    void inscribeUnEquipoYQuedaPendiente() throws Exception {
        long categoriaId = categoriaDeTorneoAbierto(2);
        String content = postJson(INSCRIPCIONES, inscripcion(categoriaId, clubA))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.listaConfirmada").value(false))
                .andExpect(jsonPath("$.fechaInscripcion").exists())
                .andExpect(jsonPath("$.motivo").doesNotExist())
                .andExpect(jsonPath("$.torneoCategoriaId").value(categoriaId))
                .andExpect(jsonPath("$.clubId").value(clubA))
                .andExpect(jsonPath("$.usuarioId").value(usuarioId))
                .andReturn().getResponse().getContentAsString();

        long id = json.readTree(content).get("id").asLong();
        mvc.perform(get(INSCRIPCIONES + "/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
        mvc.perform(delete(INSCRIPCIONES + "/" + id)).andExpect(status().isNoContent());
        mvc.perform(get(INSCRIPCIONES + "/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void elMismoClubNoSeInscribeDosVecesEnLaCategoria() throws Exception {
        long categoriaId = categoriaDeTorneoAbierto(2);
        postJson(INSCRIPCIONES, inscripcion(categoriaId, clubA)).andExpect(status().isCreated());
        postJson(INSCRIPCIONES, inscripcion(categoriaId, clubA)).andExpect(status().isConflict());
        postJson(INSCRIPCIONES, inscripcion(categoriaId, clubB)).andExpect(status().isCreated());
    }

    @Test
    void respetaElCupoMaximoDeLaCategoria() throws Exception {
        long categoriaId = categoriaDeTorneoAbierto(2);
        postJson(INSCRIPCIONES, inscripcion(categoriaId, clubA)).andExpect(status().isCreated());
        postJson(INSCRIPCIONES, inscripcion(categoriaId, clubB)).andExpect(status().isCreated());
        postJson(INSCRIPCIONES, inscripcion(categoriaId, clubC)).andExpect(status().isBadRequest());
    }

    @Test
    void noInscribeSiElTorneoSigueEnBorrador() throws Exception {
        long torneoId = torneoFuturo();
        long categoriaId = categoria(torneoId, 4);
        postJson(INSCRIPCIONES, inscripcion(categoriaId, clubA)).andExpect(status().isBadRequest());

        mvc.perform(post(TORNEOS + "/" + torneoId + "/abrir-inscripciones")).andExpect(status().isOk());
        postJson(INSCRIPCIONES, inscripcion(categoriaId, clubA)).andExpect(status().isCreated());
    }

    @Test
    void noInscribeDespuesDeLaFechaLimite() throws Exception {
        long torneoId = torneoVencido();
        long categoriaId = categoria(torneoId, 4);
        mvc.perform(post(TORNEOS + "/" + torneoId + "/abrir-inscripciones")).andExpect(status().isOk());
        postJson(INSCRIPCIONES, inscripcion(categoriaId, clubA)).andExpect(status().isBadRequest());
    }

    @Test
    void validaLasRelacionesDeLaInscripcion() throws Exception {
        long categoriaId = categoriaDeTorneoAbierto(4);

        postJson(INSCRIPCIONES, inscripcion(Long.MAX_VALUE, clubA)).andExpect(status().isNotFound());
        postJson(INSCRIPCIONES, inscripcion(categoriaId, Long.MAX_VALUE)).andExpect(status().isNotFound());

        Map<String, Object> sinUsuario = inscripcion(categoriaId, clubA);
        sinUsuario.remove("usuarioId");
        postJson(INSCRIPCIONES, sinUsuario).andExpect(status().isBadRequest());

        Map<String, Object> usuarioInexistente = inscripcion(categoriaId, clubA);
        usuarioInexistente.put("usuarioId", Long.MAX_VALUE);
        postJson(INSCRIPCIONES, usuarioInexistente).andExpect(status().isNotFound());
    }

    @Test
    void filtraLasInscripcionesPorCategoria() throws Exception {
        long primera = categoriaDeTorneoAbierto(4);
        long segunda = categoriaDeTorneoAbierto(4);
        postJson(INSCRIPCIONES, inscripcion(primera, clubA)).andExpect(status().isCreated());
        postJson(INSCRIPCIONES, inscripcion(primera, clubB)).andExpect(status().isCreated());
        postJson(INSCRIPCIONES, inscripcion(segunda, clubC)).andExpect(status().isCreated());

        mvc.perform(get(INSCRIPCIONES).param("torneoCategoriaId", String.valueOf(primera))
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get(INSCRIPCIONES).param("torneoCategoriaId", String.valueOf(segunda)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].clubId").value(clubC));
        mvc.perform(get(INSCRIPCIONES).param("torneoCategoriaId", String.valueOf(Long.MAX_VALUE)))
                .andExpect(status().isNotFound());
    }

    private long categoriaDeTorneoAbierto(int cupo) throws Exception {
        long torneoId = torneoFuturo();
        long categoriaId = categoria(torneoId, cupo);
        mvc.perform(post(TORNEOS + "/" + torneoId + "/abrir-inscripciones")).andExpect(status().isOk());
        return categoriaId;
    }

    /** Las fechas van relativas a hoy: la ventana de inscripción depende de now(). */
    private long torneoFuturo() throws Exception {
        LocalDate hoy = LocalDate.now();
        return torneo(hoy.plusDays(20), hoy.plusDays(100), hoy.plusDays(10));
    }

    private long torneoVencido() throws Exception {
        LocalDate hoy = LocalDate.now();
        return torneo(hoy.minusDays(100), hoy.minusDays(90), hoy.minusDays(110));
    }

    private long torneo(LocalDate inicio, LocalDate fin, LocalDate limite) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nombre", "Copa Inscripcion");
        body.put("temporada", inicio.getYear());
        body.put("tipo", "OFICIAL");
        body.put("ambito", "INTERNO");
        body.put("formato", "LIGA");
        body.put("fechaInicio", inicio.toString());
        body.put("fechaFin", fin.toString());
        body.put("fechaLimiteInscripcion", limite.toString());
        body.put("usuarioId", usuarioId);
        return create(TORNEOS, body);
    }

    private long categoria(long torneoId, int cupo) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nombre", "Sub-14");
        body.put("edadMinima", 12);
        body.put("edadMaxima", 14);
        body.put("cupoMaximo", cupo);
        body.put("duracionTiempoMin", 35);
        body.put("jugadoresEnCancha", 11);
        return create(TORNEOS + "/" + torneoId + "/categorias", body);
    }

    private long club(String nombre) throws Exception {
        return create("/api/v1/clubes", Map.of("nombreClub", nombre, "ciudadId", ciudadId));
    }

    private Map<String, Object> inscripcion(long categoriaId, long clubId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("torneoCategoriaId", categoriaId);
        body.put("clubId", clubId);
        body.put("usuarioId", usuarioId);
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
