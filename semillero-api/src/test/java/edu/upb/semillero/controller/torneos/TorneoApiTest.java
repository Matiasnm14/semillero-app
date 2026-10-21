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
 * HU 1 - Crear torneo: endpoints de las tareas S1-08, S1-09 y S1-10, al estilo
 * de {@link edu.upb.semillero.CrudIntegrationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TorneoApiTest {
    private static final String TORNEOS = "/api/v1/torneos";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private long organizadorId;

    @BeforeEach
    void fixtures() throws Exception {
        organizadorId = create("/api/v1/usuarios", Map.of("name", "organizador",
                "nombres", "Miguel", "apellidos", "López",
                "email", "organizador" + System.nanoTime() + "@example.com",
                "password", "secreto123"));
    }

    @Test
    void creaElTorneoEnBorradorConSusCategorias() throws Exception {
        Map<String, Object> body = torneo();
        body.put("categorias", List.of(categoria("Sub-12"), categoria("Sub-14")));
        long id = create(TORNEOS, body);

        mvc.perform(get(TORNEOS + "/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Copa Semillero"))
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.ambito").value("INTERNO"))
                .andExpect(jsonPath("$.usuarioId").value(organizadorId))
                .andExpect(jsonPath("$.fechaCreacion").exists())
                .andExpect(jsonPath("$.organizadorExterno").doesNotExist());

        mvc.perform(get(TORNEOS + "/" + id + "/categorias")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].nombre").value("Sub-12"))
                .andExpect(jsonPath("$.content[0].torneoId").value(id))
                .andExpect(jsonPath("$.content[0].edadMaxima").value(12))
                .andExpect(jsonPath("$.content[1].nombre").value("Sub-14"));
    }

    @Test
    void abreLasInscripcionesUnaSolaVez() throws Exception {
        long id = crearTorneo(List.of(categoria("Sub-12")));
        mvc.perform(post(TORNEOS + "/" + id + "/abrir-inscripciones")).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INSCRIPCIONES_ABIERTAS"));
        mvc.perform(post(TORNEOS + "/" + id + "/abrir-inscripciones")).andExpect(status().isConflict());
    }

    @Test
    void noAbreInscripcionesSinCategorias() throws Exception {
        long id = crearTorneo(List.of());
        mvc.perform(post(TORNEOS + "/" + id + "/abrir-inscripciones")).andExpect(status().isBadRequest());
        mvc.perform(get(TORNEOS + "/" + id)).andExpect(jsonPath("$.estado").value("BORRADOR"));
    }

    @Test
    void noGuardaFechasIncoherentes() throws Exception {
        Map<String, Object> finAntesDelInicio = torneo();
        finAntesDelInicio.put("fechaFin", "2026-02-01");
        postTorneo(finAntesDelInicio).andExpect(status().isBadRequest());

        Map<String, Object> limiteTardio = torneo();
        limiteTardio.put("fechaLimiteInscripcion", "2026-04-01");
        postTorneo(limiteTardio).andExpect(status().isBadRequest());
    }

    @Test
    void elTorneoExternoExigeOrganizador() throws Exception {
        Map<String, Object> externo = torneo();
        externo.put("ambito", "EXTERNO");
        postTorneo(externo).andExpect(status().isBadRequest());

        externo.put("organizadorExterno", "Liga Cruceña");
        postTorneo(externo).andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizadorExterno").value("Liga Cruceña"));
    }

    @Test
    void elTorneoInternoNoGuardaOrganizadorExterno() throws Exception {
        Map<String, Object> interno = torneo();
        interno.put("organizadorExterno", "Liga Cruceña");
        postTorneo(interno).andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizadorExterno").doesNotExist());
    }

    @Test
    void validaElCuerpoYElOrganizador() throws Exception {
        Map<String, Object> sinNombre = torneo();
        sinNombre.remove("nombre");
        postTorneo(sinNombre).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nombre").exists());

        Map<String, Object> tipoInvalido = torneo();
        tipoInvalido.put("tipo", "DESCONOCIDO");
        postTorneo(tipoInvalido).andExpect(status().isBadRequest());

        Map<String, Object> organizadorInexistente = torneo();
        organizadorInexistente.put("usuarioId", Long.MAX_VALUE);
        postTorneo(organizadorInexistente).andExpect(status().isNotFound());

        mvc.perform(get(TORNEOS + "/-1")).andExpect(status().isBadRequest());
        mvc.perform(get(TORNEOS + "/" + Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void validaLasCategoriasDelTorneo() throws Exception {
        long id = crearTorneo(List.of(categoria("Sub-12")));

        postCategoria(id, categoria("Sub-12")).andExpect(status().isConflict());

        // El 400 no trae el mapa "errors" porque el método tiene además un
        // @PathVariable validado y Spring lanza HandlerMethodValidationException,
        // que el ApiExceptionHandler compartido resuelve sin detalle por campo.
        Map<String, Object> cupoChico = categoria("Sub-16");
        cupoChico.put("cupoMaximo", 1);
        postCategoria(id, cupoChico).andExpect(status().isBadRequest());

        Map<String, Object> edadesInvertidas = categoria("Sub-18");
        edadesInvertidas.put("edadMinima", 20);
        postCategoria(id, edadesInvertidas).andExpect(status().isBadRequest());

        postCategoria(id, categoria("Sub-14")).andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.torneoId").value(id));
    }

    @Test
    void laCategoriaViveDentroDeSuTorneo() throws Exception {
        long primero = crearTorneo(List.of(categoria("Sub-12")));
        long segundo = crearTorneo(List.of(categoria("Sub-14")));
        long categoriaDelSegundo = primeraCategoria(segundo);

        mvc.perform(get(TORNEOS + "/" + primero + "/categorias/" + categoriaDelSegundo))
                .andExpect(status().isNotFound());
        mvc.perform(delete(TORNEOS + "/" + segundo + "/categorias/" + categoriaDelSegundo))
                .andExpect(status().isNoContent());
        mvc.perform(get(TORNEOS + "/" + segundo + "/categorias/" + categoriaDelSegundo))
                .andExpect(status().isNotFound());
    }

    @Test
    void actualizarElTorneoNoCambiaSuEstado() throws Exception {
        long id = crearTorneo(List.of(categoria("Sub-12")));
        mvc.perform(post(TORNEOS + "/" + id + "/abrir-inscripciones")).andExpect(status().isOk());

        Map<String, Object> body = torneo();
        body.put("nombre", "Copa Semillero 2026");
        mvc.perform(put(TORNEOS + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Copa Semillero 2026"))
                .andExpect(jsonPath("$.estado").value("INSCRIPCIONES_ABIERTAS"));
    }

    @Test
    void listaLosTorneosPaginados() throws Exception {
        long id = crearTorneo(List.of());
        mvc.perform(get(TORNEOS).param("size", "100").param("sort", "id,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(100));
    }

    private long crearTorneo(List<Map<String, Object>> categorias) throws Exception {
        Map<String, Object> body = torneo();
        body.put("categorias", categorias);
        return create(TORNEOS, body);
    }

    private long primeraCategoria(long torneoId) throws Exception {
        String content = mvc.perform(get(TORNEOS + "/" + torneoId + "/categorias"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(content).get("content").get(0).get("id").asLong();
    }

    private Map<String, Object> torneo() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nombre", "Copa Semillero");
        body.put("temporada", 2026);
        body.put("tipo", "OFICIAL");
        body.put("ambito", "INTERNO");
        body.put("formato", "LIGA");
        body.put("idaVuelta", false);
        body.put("fechaInicio", "2026-03-01");
        body.put("fechaFin", "2026-06-30");
        body.put("fechaLimiteInscripcion", "2026-02-15");
        body.put("usuarioId", organizadorId);
        return body;
    }

    private Map<String, Object> categoria(String nombre) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nombre", nombre);
        body.put("edadMinima", 10);
        body.put("edadMaxima", 12);
        body.put("cupoMaximo", 8);
        body.put("duracionTiempoMin", 35);
        body.put("jugadoresEnCancha", 11);
        body.put("maxSustituciones", 5);
        return body;
    }

    private long create(String url, Map<String, Object> body) throws Exception {
        String content = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(content).get("id").asLong();
    }

    private ResultActions postTorneo(Map<String, Object> body) throws Exception {
        return mvc.perform(post(TORNEOS).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private ResultActions postCategoria(long torneoId, Map<String, Object> body) throws Exception {
        return mvc.perform(post(TORNEOS + "/" + torneoId + "/categorias")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }
}
