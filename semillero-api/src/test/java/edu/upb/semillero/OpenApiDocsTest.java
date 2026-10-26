package edu.upb.semillero;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tarea S1-02: comprueba que springdoc publica el documento OpenAPI y que los
 * endpoints quedan documentados, que es lo que pide la definición de terminado.
 *
 * <p>springdoc 2.8.6 apunta a Spring Boot 3 y acá corre sobre Spring Boot 4.1.1:
 * no hay versión para Boot 4 todavía. Esta prueba es la que avisa si una
 * actualización de Spring Boot lo rompe.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocsTest {
    @Autowired MockMvc mvc;

    @Test
    void publicaElDocumentoOpenApi() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths").exists());
    }

    @Test
    void documentaLosEndpointsDeTorneos() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/torneos']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/torneos/{id}/abrir-inscripciones']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/torneos/{torneoId}/reglamento/desempates']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/torneos/sedes']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/torneos/canchas']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/torneos/inscripciones']").exists());
    }

    @Test
    void documentaTambienLosEndpointsDeLosOtrosGrupos() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/partidos']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/jugadores']").exists());
    }
}
