package edu.upb.semillero.controller.torneos;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HU 3 - Sedes y canchas: tareas S1-16 (CRUD) y S1-17 (descripción y
 * dimensiones de la cancha).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "ORGANIZADOR")
class SedeCanchaApiTest {
    private static final String SEDES = "/api/v1/torneos/sedes";
    private static final String CANCHAS = "/api/v1/torneos/canchas";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private long ciudadId;

    @BeforeEach
    void fixtures() throws Exception {
        long paisId = create("/api/v1/paises", Map.of("nombrePais", "Bolivia"));
        ciudadId = create("/api/v1/ciudades", Map.of("nombreCiudad", "Santa Cruz", "paisId", paisId));
    }

    @Test
    void crudCompletoDeSedeYCancha() throws Exception {
        long sedeId = create(SEDES, sede("Complejo Norte"));
        mvc.perform(get(SEDES + "/" + sedeId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Complejo Norte"))
                .andExpect(jsonPath("$.direccion").value("Av. Banzer 100"))
                .andExpect(jsonPath("$.ciudadId").value(ciudadId));

        long canchaId = create(CANCHAS, cancha(sedeId, "Cancha 1"));
        mvc.perform(get(CANCHAS + "/" + canchaId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.sedeId").value(sedeId))
                .andExpect(jsonPath("$.nombre").value("Cancha 1"))
                .andExpect(jsonPath("$.tipoSuperficie").value("CESPED_SINTETICO"))
                .andExpect(jsonPath("$.largoM").value(100.0))
                .andExpect(jsonPath("$.anchoM").value(64.5))
                .andExpect(jsonPath("$.techada").value(false))
                .andExpect(jsonPath("$.iluminacion").value(true))
                .andExpect(jsonPath("$.vestuarios").value(true))
                .andExpect(jsonPath("$.descripcion").value("Cancha principal con tribuna"))
                .andExpect(jsonPath("$.activa").value(true));

        Map<String, Object> modificada = cancha(sedeId, "Cancha 1");
        modificada.put("tipoSuperficie", "CESPED_NATURAL");
        modificada.put("activa", false);
        mvc.perform(put(CANCHAS + "/" + canchaId).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(modificada)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoSuperficie").value("CESPED_NATURAL"))
                .andExpect(jsonPath("$.activa").value(false));

        mvc.perform(delete(CANCHAS + "/" + canchaId)).andExpect(status().isNoContent());
        mvc.perform(get(CANCHAS + "/" + canchaId)).andExpect(status().isNotFound());
        mvc.perform(delete(SEDES + "/" + sedeId)).andExpect(status().isNoContent());
        mvc.perform(get(SEDES + "/" + sedeId)).andExpect(status().isNotFound());
    }

    @Test
    void laCanchaNoSeRepiteDentroDeLaMismaSede() throws Exception {
        long sedeId = create(SEDES, sede("Complejo Sur"));
        create(CANCHAS, cancha(sedeId, "Principal"));
        postJson(CANCHAS, cancha(sedeId, "Principal")).andExpect(status().isConflict());

        long otraSede = create(SEDES, sede("Complejo Este"));
        postJson(CANCHAS, cancha(otraSede, "Principal")).andExpect(status().isCreated());
    }

    @Test
    void validaElCuerpoYLasRelacionesDeLaCancha() throws Exception {
        long sedeId = create(SEDES, sede("Complejo Oeste"));

        Map<String, Object> superficieInvalida = cancha(sedeId, "C1");
        superficieInvalida.put("tipoSuperficie", "ARENA");
        postJson(CANCHAS, superficieInvalida).andExpect(status().isBadRequest());

        Map<String, Object> sinNombre = cancha(sedeId, "C1");
        sinNombre.remove("nombre");
        postJson(CANCHAS, sinNombre).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nombre").exists());

        Map<String, Object> largoInvalido = cancha(sedeId, "C2");
        largoInvalido.put("largoM", 100.25);
        postJson(CANCHAS, largoInvalido).andExpect(status().isBadRequest());

        Map<String, Object> sedeInexistente = cancha(Long.MAX_VALUE, "C3");
        postJson(CANCHAS, sedeInexistente).andExpect(status().isNotFound());

        postJson(SEDES, Map.of("nombre", "X", "direccion", "Y", "ciudadId", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void filtraLasCanchasPorSede() throws Exception {
        long sedeA = create(SEDES, sede("Sede A"));
        long sedeB = create(SEDES, sede("Sede B"));
        create(CANCHAS, cancha(sedeA, "A1"));
        create(CANCHAS, cancha(sedeA, "A2"));
        create(CANCHAS, cancha(sedeB, "B1"));

        mvc.perform(get(CANCHAS).param("sedeId", String.valueOf(sedeA)).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get(CANCHAS).param("sedeId", String.valueOf(sedeB)).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nombre").value("B1"));
        mvc.perform(get(CANCHAS).param("sedeId", String.valueOf(Long.MAX_VALUE)))
                .andExpect(status().isNotFound());
    }

    /**
     * Las rutas /api/v1/torneos/sedes y /api/v1/torneos/canchas conviven con
     * /api/v1/torneos/{id}: Spring prefiere el segmento literal al path variable.
     */
    @Test
    void lasRutasPlanasNoChocanConLaDeTorneoPorId() throws Exception {
        mvc.perform(get(SEDES)).andExpect(status().isOk()).andExpect(jsonPath("$.content").exists());
        mvc.perform(get(CANCHAS)).andExpect(status().isOk()).andExpect(jsonPath("$.content").exists());
        mvc.perform(get("/api/v1/torneos/criterios-desempate")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").exists());
    }

    private Map<String, Object> sede(String nombre) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nombre", nombre);
        body.put("direccion", "Av. Banzer 100");
        body.put("ciudadId", ciudadId);
        return body;
    }

    private Map<String, Object> cancha(long sedeId, String nombre) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sedeId", sedeId);
        body.put("nombre", nombre);
        body.put("tipoSuperficie", "CESPED_SINTETICO");
        body.put("largoM", 100.0);
        body.put("anchoM", 64.5);
        body.put("techada", false);
        body.put("iluminacion", true);
        body.put("vestuarios", true);
        body.put("descripcion", "Cancha principal con tribuna");
        body.put("activa", true);
        return body;
    }

    private long create(String url, Map<String, Object> body) throws Exception {
        String content = postJson(url, body).andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(content).get("id").asLong();
    }

    private ResultActions postJson(String url, Map<String, Object> body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }
}
